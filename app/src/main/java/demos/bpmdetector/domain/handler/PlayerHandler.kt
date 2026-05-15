package demos.bpmdetector.domain.handler

import android.net.Uri
import demos.bpmdetector.domain.store.PlayerStore
import demos.bpmdetector.infra.system.AudioPickerSystemApi
import demos.bpmdetector.infra.system.AudioPcmDecoderSystemApi
import demos.bpmdetector.infra.system.AudioPlayerSystemApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Handler：编排 UI、Store、SystemApi。
class PlayerHandler(
    private val store: PlayerStore,
    private val audioPickerSystemApi: AudioPickerSystemApi,
    private val audioPlayerSystemApi: AudioPlayerSystemApi,
    private val audioPcmDecoderSystemApi: AudioPcmDecoderSystemApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var analysisJob: Job? = null
    private var beatJob: Job? = null
    @Volatile
    private var beatEstimate: BpmEstimate? = null

    // 用户选歌后，解析文件名、加载播放器、准备实时分析。
    fun selectAudio(uri: Uri) {
        analysisJob?.cancel()
        stopBeatLoop()
        beatEstimate = null
        val displayName = audioPickerSystemApi.resolveDisplayName(uri)
        store.update {
            it.copy(
                selectedAudioUri = uri,
                selectedAudioName = displayName,
                isReady = false,
                isPlaying = false,
                beatVisible = false,
                bpmText = "分析中...",
                statusText = "加载中...",
                errorText = null,
            )
        }

        analysisJob = scope.launch(Dispatchers.IO) {
            val estimator = BpmEstimator()
            try {
                audioPcmDecoderSystemApi.decodeMonoPcm(
                    uri = uri,
                    isActive = { isActive },
                ) { sampleRate, monoSamples ->
                    estimator.setSampleRate(sampleRate)
                    estimator.ingest(monoSamples)
                }
                if (!isActive) {
                    return@launch
                }
                val estimate = estimator.finish() ?: return@launch
                if (!isCurrentAudio(uri)) {
                    return@launch
                }
                beatEstimate = estimate
                store.update {
                    it.copy(
                        bpmText = "${estimate.bpm} BPM",
                        errorText = null,
                    )
                }
                if (store.state.value.isPlaying) {
                    startBeatLoop()
                }
            } catch (_: CancellationException) {
                return@launch
            } catch (error: Exception) {
                if (!isCurrentAudio(uri)) {
                    return@launch
                }
                store.update {
                    it.copy(
                        statusText = "分析失败",
                        errorText = error.message ?: "unknown analysis error",
                    )
                }
            }
        }

        audioPlayerSystemApi.load(
            uri = uri,
            onPrepared = {
                if (!isCurrentAudio(uri)) {
                    return@load
                }
                store.update {
                    it.copy(
                        isReady = true,
                        isPlaying = false,
                        statusText = "已加载，待播放",
                        errorText = null,
                    )
                }
            },
            onCompleted = {
                if (!isCurrentAudio(uri)) {
                    return@load
                }
                stopBeatLoop()
                store.update {
                    it.copy(
                        isPlaying = false,
                        beatVisible = false,
                        statusText = "已停止",
                    )
                }
            },
            onError = { message ->
                // 系统层出错时，仅转成 UI 可读状态。
                if (!isCurrentAudio(uri)) {
                    return@load
                }
                stopBeatLoop()
                store.update {
                    it.copy(
                        isReady = false,
                        isPlaying = false,
                        beatVisible = false,
                        statusText = "播放失败",
                        errorText = message,
                    )
                }
            },
        )
    }

    // 同一个按钮负责播放与暂停切换。
    fun togglePlayPause() {
        val currentState = store.state.value
        if (!currentState.isReady) {
            return
        }

        if (audioPlayerSystemApi.isPlaying()) {
            audioPlayerSystemApi.pause()
            stopBeatLoop()
            store.update {
                it.copy(
                    isPlaying = false,
                    beatVisible = false,
                    statusText = "已暂停",
                )
            }
            return
        }

        audioPlayerSystemApi.play()
        store.update {
            it.copy(
                isPlaying = true,
                statusText = if (it.bpmText == "分析中...") "播放中，分析中..." else "播放中",
            )
        }
        startBeatLoop()
    }

    // 停止播放，但保留当前分析结果。
    fun stopPlayback() {
        stopBeatLoop()
        audioPlayerSystemApi.stop()
        store.update {
            it.copy(
                isReady = it.selectedAudioUri != null,
                isPlaying = false,
                beatVisible = false,
                statusText = "已停止",
            )
        }
    }

    // 页面销毁时释放系统资源。
    fun release() {
        analysisJob?.cancel()
        stopBeatLoop()
        scope.cancel()
        audioPlayerSystemApi.release()
    }

    private fun isCurrentAudio(uri: Uri): Boolean {
        return store.state.value.selectedAudioUri == uri
    }

    private fun startBeatLoop() {
        val estimate = beatEstimate ?: return
        beatJob?.cancel()
        beatJob = scope.launch {
            val beatIntervalMs = 60_000L / estimate.bpm
            val flashMs = (beatIntervalMs / 4).coerceIn(60L, 120L)
            while (isActive && store.state.value.isPlaying) {
                val delayMs = computeDelayToNextBeat(
                    currentPositionMs = audioPlayerSystemApi.currentPositionMs(),
                    beatOffsetMs = estimate.beatOffsetMs,
                    beatIntervalMs = beatIntervalMs,
                )
                delay(delayMs)
                if (!isActive || !store.state.value.isPlaying) {
                    break
                }
                store.update {
                    it.copy(beatVisible = true)
                }
                try {
                    delay(flashMs)
                } finally {
                    store.update {
                        it.copy(beatVisible = false)
                    }
                }
            }
        }
    }

    private fun stopBeatLoop() {
        beatJob?.cancel()
        beatJob = null
        store.update {
            it.copy(beatVisible = false)
        }
    }

    private fun computeDelayToNextBeat(
        currentPositionMs: Int,
        beatOffsetMs: Int,
        beatIntervalMs: Long,
    ): Long {
        val current = currentPositionMs.toLong()
        val offset = beatOffsetMs.toLong()
        if (current <= offset) {
            return offset - current
        }

        val elapsed = current - offset
        val remainder = elapsed % beatIntervalMs
        return if (remainder == 0L) {
            beatIntervalMs
        } else {
            beatIntervalMs - remainder
        }
    }
}

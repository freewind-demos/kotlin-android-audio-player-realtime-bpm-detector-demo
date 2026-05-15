package demos.bpmdetector.domain.handler

import android.net.Uri
import demos.bpmdetector.domain.store.PlayerStore
import demos.bpmdetector.infra.system.AudioPickerSystemApi
import demos.bpmdetector.infra.system.AudioPlayerSystemApi

// Handler：编排 UI、Store、SystemApi。
class PlayerHandler(
    private val store: PlayerStore,
    private val audioPickerSystemApi: AudioPickerSystemApi,
    private val audioPlayerSystemApi: AudioPlayerSystemApi,
    private val bpmEstimator: BpmEstimator,
) {
    // 用户选歌后，解析文件名、加载播放器、准备实时分析。
    fun selectAudio(uri: Uri) {
        val displayName = audioPickerSystemApi.resolveDisplayName(uri)
        bpmEstimator.reset()
        store.update {
            it.copy(
                selectedAudioUri = uri,
                selectedAudioName = displayName,
                isReady = false,
                isPlaying = false,
                bpmText = "分析中...",
                statusText = "加载中...",
                errorText = null,
            )
        }

        audioPlayerSystemApi.load(
            uri = uri,
            onPrepared = {
                store.update {
                    it.copy(
                        isReady = true,
                        isPlaying = false,
                        bpmText = "-- BPM",
                        statusText = "已加载，待播放",
                        errorText = null,
                    )
                }
            },
            onCompleted = {
                // 播放完成后回到静止态。
                bpmEstimator.reset()
                store.update {
                    it.copy(
                        isPlaying = false,
                        bpmText = "-- BPM",
                        statusText = "已停止",
                    )
                }
            },
            onWaveform = { waveform ->
                // 只有估算出稳定 BPM 才推进 UI。
                val bpm = bpmEstimator.addWaveformFrame(waveform) ?: return@load
                store.update {
                    if (!it.isPlaying) {
                        it
                    } else {
                        it.copy(bpmText = "$bpm BPM")
                    }
                }
            },
            onError = { message ->
                // 系统层出错时，仅转成 UI 可读状态。
                store.update {
                    it.copy(
                        isReady = false,
                        isPlaying = false,
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
            store.update {
                it.copy(
                    isPlaying = false,
                    statusText = "已暂停",
                )
            }
            return
        }

        audioPlayerSystemApi.play()
        store.update {
            it.copy(
                isPlaying = true,
                statusText = "播放中，实时分析拍点",
                bpmText = if (it.bpmText == "-- BPM") "分析中..." else it.bpmText,
            )
        }
    }

    // 停止播放并清空本次估算窗口。
    fun stopPlayback() {
        bpmEstimator.reset()
        audioPlayerSystemApi.stop()
        store.update {
            it.copy(
                isReady = it.selectedAudioUri != null,
                isPlaying = false,
                bpmText = "-- BPM",
                statusText = "已停止",
            )
        }
    }

    // 页面销毁时释放系统资源。
    fun release() {
        audioPlayerSystemApi.release()
    }
}

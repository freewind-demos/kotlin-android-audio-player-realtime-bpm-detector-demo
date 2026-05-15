package demos.bpmdetector.infra.system

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri

// SystemApi：只封装播放器与系统音频采样能力。
class AudioPlayerSystemApi(
    private val context: Context,
) {
    // 真正负责解码播放的系统播放器。
    private var mediaPlayer: MediaPlayer? = null
    // 从播放 session 抓实时波形的系统分析器。
    private var visualizer: Visualizer? = null

    // 加载音频并准备回调。
    fun load(
        uri: Uri,
        onPrepared: () -> Unit,
        onCompleted: () -> Unit,
        onWaveform: (ByteArray) -> Unit,
        onError: (String) -> Unit,
    ) {
        // 每次重新加载都先释放旧实例。
        release()

        val player = MediaPlayer()
        mediaPlayer = player
        try {
            player.setDataSource(context, uri)
            player.setOnPreparedListener {
                // 准备完成后再绑定 Visualizer，确保 audio session 可用。
                attachVisualizer(player.audioSessionId, onWaveform, onError)
                onPrepared()
            }
            player.setOnCompletionListener {
                onCompleted()
            }
            player.setOnErrorListener { _, what, extra ->
                onError("MediaPlayer error what=$what extra=$extra")
                true
            }
            player.prepareAsync()
        } catch (error: Exception) {
            onError(error.message ?: "unknown load error")
        }
    }

    // 把 Visualizer 绑定到当前播放 session。
    private fun attachVisualizer(
        audioSessionId: Int,
        onWaveform: (ByteArray) -> Unit,
        onError: (String) -> Unit,
    ) {
        try {
            val visualizerInstance = Visualizer(audioSessionId)
            visualizer = visualizerInstance
            // 直接使用设备支持的最大波形窗口，增强拍点稳定性。
            val captureSize = Visualizer.getCaptureSizeRange()[1]
            visualizerInstance.captureSize = captureSize
            visualizerInstance.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int,
                    ) {
                        if (waveform != null) {
                            onWaveform(waveform)
                        }
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int,
                    ) = Unit
                },
                Visualizer.getMaxCaptureRate() / 2,
                true,
                false,
            )
            visualizerInstance.enabled = true
        } catch (error: Exception) {
            onError(error.message ?: "visualizer error")
        }
    }

    // 开始或恢复播放。
    fun play() {
        mediaPlayer?.start()
    }

    // 暂停播放。
    fun pause() {
        mediaPlayer?.pause()
    }

    // 停止到开头，但保留已加载状态，便于再次播放。
    fun stop() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            }
            player.seekTo(0)
        }
    }

    // 查询当前是否处于播放态。
    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    // 彻底释放系统资源。
    fun release() {
        visualizer?.release()
        visualizer = null

        mediaPlayer?.release()
        mediaPlayer = null
    }
}

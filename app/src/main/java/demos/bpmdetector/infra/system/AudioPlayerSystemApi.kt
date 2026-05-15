package demos.bpmdetector.infra.system

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

// SystemApi：只封装播放器能力。
class AudioPlayerSystemApi(
    private val context: Context,
) {
    // 真正负责解码播放的系统播放器。
    private var mediaPlayer: MediaPlayer? = null

    // 加载音频并准备回调。
    fun load(
        uri: Uri,
        onPrepared: () -> Unit,
        onCompleted: () -> Unit,
        onError: (String) -> Unit,
    ) {
        // 每次重新加载都先释放旧实例。
        release()

        val player = MediaPlayer()
        mediaPlayer = player
        try {
            player.setDataSource(context, uri)
            player.setOnPreparedListener {
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
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

package demos.bpmdetector.domain.model

import android.net.Uri

// 播放器页面共享状态。
data class PlayerUiState(
    // 当前选择的音频地址。
    val selectedAudioUri: Uri? = null,
    // 当前选择的音频显示名。
    val selectedAudioName: String = "",
    // 是否已完成加载、可开始播放。
    val isReady: Boolean = false,
    // 当前是否正在播放。
    val isPlaying: Boolean = false,
    // 当前是否显示拍点红块。
    val beatVisible: Boolean = false,
    // 屏幕中央的大号 BPM 文案。
    val bpmText: String = "-- BPM",
    // 页面底部状态文案。
    val statusText: String = "待机",
    // 错误时优先展示的报错文案。
    val errorText: String? = null,
)

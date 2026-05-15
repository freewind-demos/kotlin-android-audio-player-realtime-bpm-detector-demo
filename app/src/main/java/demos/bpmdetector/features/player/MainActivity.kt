package demos.bpmdetector.features.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import demos.bpmdetector.databinding.ActivityMainBinding
import demos.bpmdetector.domain.handler.PlayerHandler
import demos.bpmdetector.domain.store.PlayerStore
import demos.bpmdetector.infra.system.AudioPickerSystemApi
import demos.bpmdetector.infra.system.AudioPcmDecoderSystemApi
import demos.bpmdetector.infra.system.AudioPlayerSystemApi
import kotlinx.coroutines.launch

// Entry：只接事件、订阅状态、绑定 UI。
class MainActivity : ComponentActivity() {
    // ViewBinding 持有页面控件。
    private lateinit var binding: ActivityMainBinding
    // 共享内存态。
    private val store = PlayerStore()
    // 业务编排入口。
    private lateinit var handler: PlayerHandler

    // 用系统文件选择器挑音频。
    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            handler.selectAudio(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handler = PlayerHandler(
            store = store,
            audioPickerSystemApi = AudioPickerSystemApi(contentResolver),
            audioPlayerSystemApi = AudioPlayerSystemApi(this),
            audioPcmDecoderSystemApi = AudioPcmDecoderSystemApi(contentResolver),
        )

        binding.pickAudioButton.setOnClickListener {
            pickAudioLauncher.launch(arrayOf("audio/*"))
        }
        binding.playPauseButton.setOnClickListener {
            handler.togglePlayPause()
        }
        binding.stopButton.setOnClickListener {
            handler.stopPlayback()
        }

        // 页面进入前台后持续订阅共享状态，驱动界面刷新。
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                store.state.collect { state ->
                    binding.songNameText.text = state.selectedAudioName.ifEmpty { "未选择音频" }
                    binding.bpmText.text = state.bpmText
                    binding.statusText.text = state.errorText ?: state.statusText
                    binding.beatSquare.visibility = if (state.beatVisible) {
                        android.view.View.VISIBLE
                    } else {
                        android.view.View.INVISIBLE
                    }
                    binding.playPauseButton.isEnabled = state.isReady
                    binding.stopButton.isEnabled = state.isReady || state.isPlaying
                    binding.playPauseButton.text = if (state.isPlaying) "暂停" else "播放"
                }
            }
        }
    }

    override fun onDestroy() {
        // Activity 销毁时释放播放器与分析器。
        handler.release()
        super.onDestroy()
    }
}

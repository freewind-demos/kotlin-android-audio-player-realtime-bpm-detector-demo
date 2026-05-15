package demos.bpmdetector.domain.store

import demos.bpmdetector.domain.model.PlayerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Store 只管内存态，不掺业务编排。
class PlayerStore {
    // 内部可变状态流。
    private val mutableState = MutableStateFlow(PlayerUiState())
    // 对外只暴露只读状态流。
    val state: StateFlow<PlayerUiState> = mutableState.asStateFlow()

    // 统一经 reducer 修改共享状态。
    fun update(reducer: (PlayerUiState) -> PlayerUiState) {
        mutableState.update(reducer)
    }
}

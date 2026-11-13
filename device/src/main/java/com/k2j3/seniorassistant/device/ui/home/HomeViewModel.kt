package com.k2j3.seniorassistant.device.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2j3.seniorassistant.device.server.SettingsApi
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val userName: String = "",
    val now: LocalDateTime = LocalDateTime.now(),
)

/** 메인: 인사(성함)와 1초마다 바뀌는 날짜·시간 */
class HomeViewModel(private val settings: SettingsApi) : ViewModel() {
    private val state = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                state.update { it.copy(now = LocalDateTime.now()) }
                delay(1_000)
            }
        }
    }

    /** 메인에 들어올 때마다 성함을 다시 읽는다 (설정에서 바꿨을 수 있으니) */
    fun refresh() {
        viewModelScope.launch { state.update { it.copy(userName = settings.getSettings().userName) } }
    }
}

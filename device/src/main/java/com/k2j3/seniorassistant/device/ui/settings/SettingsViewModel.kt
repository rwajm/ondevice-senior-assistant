package com.k2j3.seniorassistant.device.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2j3.seniorassistant.device.audio.AudioPlayer
import com.k2j3.seniorassistant.device.data.settings.Settings
import com.k2j3.seniorassistant.device.server.BoardApiServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: Settings = Settings(),
    val previewing: Boolean = false,
)

/** 설정 화면 (FR-07). 고른 값을 저장만 하고, 화면에 적용하는 기능은 아직 없다 */
class SettingsViewModel(
    private val server: BoardApiServer,
    private val player: AudioPlayer,
) : ViewModel() {

    private val state = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = state.asStateFlow()

    fun refresh() {
        viewModelScope.launch { state.update { it.copy(settings = server.settings.getSettings()) } }
    }

    fun change(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            val changed = transform(server.settings.getSettings())
            server.settings.updateSettings(changed)
            state.update { it.copy(settings = changed) }
        }
    }

    /** 말 빠르기 '들어보기': 다 읽을 때까지 '읽는 중…' */
    fun preview() {
        if (state.value.previewing) return
        viewModelScope.launch {
            state.update { it.copy(previewing = true) }
            try {
                server.settings.previewSpeech(state.value.settings.speechRate)?.let { player.play(it) }
            } finally {
                state.update { it.copy(previewing = false) }
            }
        }
    }
}

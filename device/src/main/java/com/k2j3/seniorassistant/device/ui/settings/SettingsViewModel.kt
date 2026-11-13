package com.k2j3.seniorassistant.device.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
class SettingsViewModel(private val server: BoardApiServer) : ViewModel() {

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

    /** 말 빠르기 '들어보기' (소리 재생은 아직 없음) */
    fun preview() {
        if (state.value.previewing) return
        viewModelScope.launch {
            state.update { it.copy(previewing = true) }
            val audio = server.settings.previewSpeech(state.value.settings.speechRate)
            Log.d(TAG, "preview ${audio?.size ?: 0} bytes")
            state.update { it.copy(previewing = false) }
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}

package com.k2j3.seniorassistant.device.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2j3.seniorassistant.device.data.settings.FontSize
import com.k2j3.seniorassistant.device.data.settings.Settings
import com.k2j3.seniorassistant.device.data.settings.SpeechRate
import com.k2j3.seniorassistant.device.server.BoardApiServer
import java.time.Duration
import kotlinx.coroutines.launch

class SettingsViewModel(private val server: BoardApiServer) : ViewModel() {
    fun changeFontSize() = change { it.copy(fontSize = it.fontSize.next()) }

    fun changeSpeechRate() = change { it.copy(speechRate = it.speechRate.next()) }

    fun changeRemindBefore() = change {
        val next = remindPresets[(remindPresets.indexOf(it.remindBefore) + 1) % remindPresets.size]
        it.copy(remindBefore = next)
    }

    fun changeUserName() = change { it.copy(userName = if (it.userName.isEmpty()) "김OO" else "") }

    fun changeVoiceNotification() = change {
        it.copy(voiceNotificationEnabled = !it.voiceNotificationEnabled)
    }

    fun changeExternalConsent() = change {
        it.copy(externalTransferConsent = !it.externalTransferConsent)
    }

    fun previewSpeech() {
        viewModelScope.launch {
            val rate = server.settings.getSettings().speechRate
            Log.d(TAG, "preview $rate: ${server.settings.previewSpeech(rate)?.size ?: 0} bytes")
        }
    }

    private fun change(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            val changed = transform(server.settings.getSettings())
            Log.d(TAG, "saved ${server.settings.updateSettings(changed)}: $changed")
        }
    }

    private fun FontSize.next() = FontSize.entries[(ordinal + 1) % FontSize.entries.size]

    private fun SpeechRate.next() = SpeechRate.entries[(ordinal + 1) % SpeechRate.entries.size]

    private companion object {
        const val TAG = "SettingsViewModel"
        val remindPresets = listOf(
            listOf(Duration.ofDays(1), Duration.ofHours(1)),
            listOf(Duration.ZERO),
            listOf(Duration.ofDays(1), Duration.ofHours(1), Duration.ZERO),
        )
    }
}

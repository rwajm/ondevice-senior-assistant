package com.k2j3.seniorassistant.device.server

import com.k2j3.seniorassistant.device.data.settings.Settings
import com.k2j3.seniorassistant.device.data.settings.SettingsRepository
import com.k2j3.seniorassistant.device.data.settings.SpeechRate
import com.k2j3.seniorassistant.device.scheduler.ReminderScheduler

class SettingsApi internal constructor(
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val speaker: Speaker,
) {
    suspend fun getSettings(): Settings = settings.getSettings()

    suspend fun updateSettings(changed: Settings): Boolean {
        if (changed.remindBefore.isEmpty()) return false // FR-07: at least one
        val before = settings.getSettings()
        settings.updateSettings(changed)
        if (before.morningReminderTime != changed.morningReminderTime) {
            scheduler.rescheduleAll() // moves untimed reminders and the summary
        }
        return true
    }

    suspend fun previewSpeech(rate: SpeechRate): ByteArray? = // FR-07 들어보기; not saved
        speaker.speak(Sentences.SPEECH_PREVIEW, rate)
}

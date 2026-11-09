package com.k2j3.seniorassistant.device.server

import com.k2j3.seniorassistant.device.ai.queue.InferenceQueue
import com.k2j3.seniorassistant.device.ai.queue.InferenceResult
import com.k2j3.seniorassistant.device.data.settings.SettingsRepository
import com.k2j3.seniorassistant.device.data.settings.SpeechRate

internal class Speaker(
    private val queue: InferenceQueue,
    private val settings: SettingsRepository,
) {
    suspend fun speak(text: String): ByteArray? = speak(text, settings.getSettings().speechRate)

    suspend fun speak(text: String, rate: SpeechRate): ByteArray? = // null when it cannot be made
        (queue.synthesize(text, speedOf(rate)) as? InferenceResult.Success)?.value

    private fun speedOf(rate: SpeechRate): Float = when (rate) {
        SpeechRate.SLOW -> 0.8f
        SpeechRate.NORMAL -> 1.0f
        SpeechRate.FAST -> 1.2f
    }
}

package com.k2j3.seniorassistant.device.ai.tts

import com.k2j3.seniorassistant.device.ai.InferenceModule

interface TtsModule : InferenceModule {
    suspend fun synthesize(text: String, speed: Float): ByteArray // WAV; speed 1.0 is normal
}

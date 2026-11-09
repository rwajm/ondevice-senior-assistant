package com.k2j3.seniorassistant.device.ai.stt

import com.k2j3.seniorassistant.device.ai.InferenceModule

interface SttModule : InferenceModule {
    suspend fun transcribe(audio: ByteArray): String // 16 kHz mono 16-bit WAV
}

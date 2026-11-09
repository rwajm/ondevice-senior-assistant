package com.k2j3.seniorassistant.device.ai

interface InferenceModule {
    suspend fun load() // once, before any inference
}

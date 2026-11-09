package com.k2j3.seniorassistant.device.ai.llm

import com.k2j3.seniorassistant.device.ai.InferenceModule
import java.time.LocalDateTime

interface LlmModule : InferenceModule {
    // raw output such as <maum_0>{...}<maum_end>; requestTime resolves relative dates
    suspend fun generate(text: String, requestTime: LocalDateTime): String
}

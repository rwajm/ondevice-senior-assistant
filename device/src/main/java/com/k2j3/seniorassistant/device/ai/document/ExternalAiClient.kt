package com.k2j3.seniorassistant.device.ai.document

interface ExternalAiClient { // EXT-01; IOException when offline, ExternalAiException on failure
    suspend fun extract(request: ExternalAiRequest): String // model output JSON
}

data class ExternalAiRequest(
    val model: String,
    val instructions: String,
    val imageBase64: String,
    val mimeType: String,
    val jsonSchema: String,
    val store: Boolean,
)

class ExternalAiException(message: String) : Exception(message)

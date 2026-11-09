package com.k2j3.seniorassistant.device.server

import com.k2j3.seniorassistant.device.ai.document.DocumentAnalysis
import com.k2j3.seniorassistant.device.ai.document.DocumentAnalyzer
import com.k2j3.seniorassistant.device.ai.document.DocumentField
import com.k2j3.seniorassistant.device.ai.document.DocumentFields
import com.k2j3.seniorassistant.device.data.settings.SettingsRepository
import java.time.LocalDateTime

sealed interface DocumentReading {
    data class Extracted( // filled blanks and checked fields are up to the user
        val fields: DocumentFields,
        val uncertainFields: Set<DocumentField>,
        val text: String,
        val audio: ByteArray?,
    ) : DocumentReading

    data object ConsentRequired : DocumentReading // FR-04: guide to writing it instead

    data class Failed(val text: String, val audio: ByteArray?) : DocumentReading
}

class DocumentApi internal constructor(
    private val analyzer: DocumentAnalyzer,
    private val settings: SettingsRepository,
    private val speaker: Speaker,
    private val now: () -> LocalDateTime,
    private val log: (String) -> Unit,
) {
    suspend fun read(image: ByteArray): DocumentReading {
        if (!settings.getSettings().externalTransferConsent) return DocumentReading.ConsentRequired
        return when (val analysis = analyzer.analyze(image)) {
            is DocumentAnalysis.Extracted -> {
                val text = Sentences.document(analysis.fields, now().toLocalDate())
                val audio = speaker.speak(text)
                DocumentReading.Extracted(analysis.fields, analysis.uncertainFields, text, audio)
            }
            DocumentAnalysis.NotDetected -> failed(Sentences.DOCUMENT_NOT_DETECTED)
            is DocumentAnalysis.Unavailable -> {
                log("document unavailable: ${analysis.cause}")
                failed(Sentences.DOCUMENT_UNAVAILABLE)
            }
        }
    }

    private suspend fun failed(text: String) = DocumentReading.Failed(text, speaker.speak(text))
}

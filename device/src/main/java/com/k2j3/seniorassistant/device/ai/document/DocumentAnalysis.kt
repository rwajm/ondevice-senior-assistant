package com.k2j3.seniorassistant.device.ai.document

import java.time.LocalDate
import java.time.LocalTime

sealed interface DocumentAnalysis { // FR-04 outcomes
    data class Extracted(
        val fields: DocumentFields, // missing values stay blank for the user to fill
        val uncertainFields: Set<DocumentField>, // highlighted for the user to check
    ) : DocumentAnalysis

    data object NotDetected : DocumentAnalysis // ask to take the picture again

    data class Unavailable(val cause: UnavailableCause) : DocumentAnalysis // suggest writing it
}

data class DocumentFields(
    val title: String = "",
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val place: String = "",
    val items: List<String> = emptyList(),
)

enum class DocumentField {
    TITLE,
    DATE,
    TIME,
    PLACE,
    ITEMS,
}

enum class UnavailableCause { // for logs; the user sees the same message
    UNSUPPORTED_TYPE,
    SERVICE_FAILED,
    TIMEOUT,
    NETWORK_UNAVAILABLE,
}

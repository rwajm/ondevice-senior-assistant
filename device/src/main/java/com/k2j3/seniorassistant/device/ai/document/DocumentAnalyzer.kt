package com.k2j3.seniorassistant.device.ai.document

import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class DocumentAnalyzer(
    private val client: ExternalAiClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default, // encoding and parsing
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    suspend fun analyze(image: ByteArray): DocumentAnalysis = withContext(dispatcher) {
        val mimeType = mimeTypeOf(image)
            ?: return@withContext unavailable(UnavailableCause.UNSUPPORTED_TYPE)
        val request = ExternalAiRequest(
            model = MODEL,
            instructions = instructions(now()),
            imageBase64 = Base64.getEncoder().encodeToString(image),
            mimeType = mimeType,
            jsonSchema = OUTPUT_SCHEMA,
            store = false,
        )
        requestWithRetry(request)
    }

    private suspend fun requestWithRetry(request: ExternalAiRequest): DocumentAnalysis {
        var cause = UnavailableCause.SERVICE_FAILED
        repeat(ATTEMPTS) {
            try {
                val output = withTimeoutOrNull(TIMEOUT_MILLIS) { client.extract(request) }
                if (output == null) {
                    cause = UnavailableCause.TIMEOUT
                } else {
                    return toAnalysis(json.decodeFromString<ExtractionOutput>(output))
                }
            } catch (e: IOException) {
                return unavailable(UnavailableCause.NETWORK_UNAVAILABLE) // no retry
            } catch (e: ExternalAiException) {
                cause = UnavailableCause.SERVICE_FAILED
            } catch (e: SerializationException) {
                cause = UnavailableCause.SERVICE_FAILED // output does not match the schema
            }
        }
        return unavailable(cause)
    }

    private fun unavailable(cause: UnavailableCause) = DocumentAnalysis.Unavailable(cause)

    private fun toAnalysis(output: ExtractionOutput): DocumentAnalysis {
        if (!output.documentDetected) return DocumentAnalysis.NotDetected
        val uncertain = output.uncertainFields
            .mapNotNull { name -> DocumentField.entries.find { it.name.equals(name, true) } }
            .toMutableSet()
        val date = parseOrNull(output.date, LocalDate::parse)
        if (output.date.isNotBlank() && date == null) uncertain += DocumentField.DATE
        val time = parseOrNull(output.time, LocalTime::parse)
        if (output.time.isNotBlank() && time == null) uncertain += DocumentField.TIME
        val fields = DocumentFields(
            title = output.title.trim(),
            date = date,
            time = time,
            place = output.place.trim(),
            items = output.items.map { it.trim() }.filter { it.isNotEmpty() },
        )
        return DocumentAnalysis.Extracted(fields, uncertain)
    }

    private fun <T> parseOrNull(value: String, parse: (String) -> T): T? =
        try {
            if (value.isBlank()) null else parse(value.trim())
        } catch (e: DateTimeParseException) {
            null
        }

    private fun mimeTypeOf(image: ByteArray): String? = when { // formats the service accepts
        image.hasAt(0, jpegSignature) -> "image/jpeg"
        image.hasAt(0, pngSignature) -> "image/png"
        image.hasAt(0, riffSignature) && image.hasAt(8, webpSignature) -> "image/webp"
        image.hasAt(0, gifSignature) -> "image/gif"
        else -> null
    }

    private fun ByteArray.hasAt(offset: Int, bytes: ByteArray): Boolean =
        size >= offset + bytes.size && bytes.indices.all { this[offset + it] == bytes[it] }

    private fun instructions(requestTime: LocalDateTime): String {
        val base = requestTime.atZone(ZoneId.systemDefault()).toOffsetDateTime()
        return """
            사진 속 문서(고지서, 안내문, 문자 캡처 등)에서 일정 하나의 항목을 추출한다.
            title은 무엇, date는 날짜(YYYY-MM-DD), time은 시각(24시간 HH:MM),
            place는 안내하는 행사나 서비스가 실제로 이뤄지는 장소(접수처, 행사장 등)다.
            온라인과 방문이 둘 다 가능하면 방문 장소를 적는다. 단순 납부를 위해 아무 은행이나
            이용할 수 있는 계좌 정보는 place가 아니다. 자동이체·온라인 전용처럼 방문 자체가
            불가능한 경우에도 place를 빈 문자열로 둔다.
            items는 챙길 것 목록이다. 값이 없으면 빈 문자열이나 빈 목록으로 둔다.
            날짜가 여러 개면 접수·신청 마감일처럼 그 전에 행동해야 하는 날짜를 우선하고,
            안내 기간 자체(교육 기간, 운영 기간 등)는 후순위로 둔다. 어느 쪽인지 애매하면
            가장 이른 날짜를 쓰고 date를 uncertain_fields에 넣는다.
            연도가 없는 날짜는 기준 시각 $base 이후 가장 가까운 날짜로 해석한다.
            읽기 어렵거나 확실하지 않은 항목의 이름은 uncertain_fields에 넣는다.
            문서나 글자를 찾지 못하면 document_detected를 false로 둔다.
        """.trimIndent()
    }

    @Serializable
    private class ExtractionOutput(
        @SerialName("document_detected") val documentDetected: Boolean,
        val title: String = "",
        val date: String = "",
        val time: String = "",
        val place: String = "",
        val items: List<String> = emptyList(),
        @SerialName("uncertain_fields") val uncertainFields: List<String> = emptyList(),
    )

    private companion object {
        const val MODEL = "gpt-6-luna"
        const val TIMEOUT_MILLIS = 20_000L // per attempt
        const val ATTEMPTS = 2 // one retry

        val jpegSignature = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val riffSignature = "RIFF".toByteArray()
        val webpSignature = "WEBP".toByteArray()
        val gifSignature = "GIF8".toByteArray()

        val json = Json { ignoreUnknownKeys = true }

        val OUTPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "document_detected": {"type": "boolean"},
                "title": {"type": "string"},
                "date": {"type": "string"},
                "time": {"type": "string"},
                "place": {"type": "string"},
                "items": {"type": "array", "items": {"type": "string"}},
                "uncertain_fields": {"type": "array", "items": {"type": "string",
                  "enum": ["title", "date", "time", "place", "items"]}}
              },
              "required": ["document_detected", "title", "date", "time", "place", "items",
                "uncertain_fields"],
              "additionalProperties": false
            }
        """.trimIndent()
    }
}

package com.k2j3.seniorassistant.device.ai.document

import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentAnalyzerTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val webp = "RIFF".toByteArray() + ByteArray(4) + "WEBPVP8 ".toByteArray()
    private val now = LocalDateTime.of(2026, 10, 11, 10, 0)

    private val checkup = """
        {"document_detected": true, "title": "건강검진", "date": "2026-10-28", "time": "09:00",
        "place": "○○내과 2층", "items": ["신분증", "검진표", " "], "uncertain_fields": ["time"]}
    """.trimIndent()

    @Test
    fun extractsFieldsFromTheExternalService() = runTest {
        val client = FakeClient({ checkup })
        val result = analyzer(client).analyze(png)

        val fields = DocumentFields(
            title = "건강검진",
            date = LocalDate.of(2026, 10, 28),
            time = LocalTime.of(9, 0),
            place = "○○내과 2층",
            items = listOf("신분증", "검진표"),
        )
        assertEquals(DocumentAnalysis.Extracted(fields, setOf(DocumentField.TIME)), result)
        val request = client.requests.single()
        assertEquals("gpt-6-luna", request.model)
        assertEquals("image/png", request.mimeType)
        assertFalse(request.store)
        assertTrue(request.instructions.contains("2026-10-11T10:00"))
    }

    @Test
    fun badDateAndTimeBecomeUncertain() = runTest {
        val output = """{"document_detected": true, "title": "검진", "date": "10월 28일",
            "time": "오전 9시", "place": "", "items": [], "uncertain_fields": []}"""
        val result = analyzer(FakeClient({ output })).analyze(png)

        val fields = DocumentFields(title = "검진")
        val uncertain = setOf(DocumentField.DATE, DocumentField.TIME)
        assertEquals(DocumentAnalysis.Extracted(fields, uncertain), result)
    }

    @Test
    fun noDocumentIsReported() = runTest {
        val output = """{"document_detected": false, "title": "", "date": "", "time": "",
            "place": "", "items": [], "uncertain_fields": []}"""
        val result = analyzer(FakeClient({ output })).analyze(png)
        assertEquals(DocumentAnalysis.NotDetected, result)
    }

    @Test
    fun onlyFormatsTheServiceAcceptsAreSent() = runTest {
        val client = FakeClient({ checkup })
        val documents = analyzer(client)

        assertTrue(documents.analyze(webp) is DocumentAnalysis.Extracted)
        assertEquals("image/webp", client.requests.single().mimeType)
        val pdf = "%PDF-1.7".toByteArray()
        assertEquals(unavailable(UnavailableCause.UNSUPPORTED_TYPE), documents.analyze(pdf))
        assertEquals(1, client.requests.size)
    }

    @Test
    fun eachAttemptTimesOutAfterTwentySeconds() = runTest {
        val client = FakeClient({ delay(21_000); checkup })
        val result = analyzer(client).analyze(png)
        assertEquals(unavailable(UnavailableCause.TIMEOUT), result)
        assertEquals(2, client.requests.size)
    }

    @Test
    fun timedOutAttemptIsRetried() = runTest {
        val client = FakeClient({ delay(21_000); checkup }, { checkup })
        assertTrue(analyzer(client).analyze(png) is DocumentAnalysis.Extracted)
        assertEquals(2, client.requests.size)
    }

    @Test
    fun serviceFailureOrBadOutputIsRetriedOnce() = runTest {
        val recovered = FakeClient({ throw ExternalAiException("500") }, { checkup })
        assertTrue(analyzer(recovered).analyze(png) is DocumentAnalysis.Extracted)

        val broken = FakeClient({ "not json" })
        val result = analyzer(broken).analyze(png)
        assertEquals(unavailable(UnavailableCause.SERVICE_FAILED), result)
        assertEquals(2, broken.requests.size)
    }

    @Test
    fun noNetworkIsNotRetried() = runTest {
        val client = FakeClient({ throw IOException("offline") })
        val result = analyzer(client).analyze(png)
        assertEquals(unavailable(UnavailableCause.NETWORK_UNAVAILABLE), result)
        assertEquals(1, client.requests.size)
    }

    private fun TestScope.analyzer(client: ExternalAiClient) =
        DocumentAnalyzer(client, StandardTestDispatcher(testScheduler)) { now }

    private fun unavailable(cause: UnavailableCause) = DocumentAnalysis.Unavailable(cause)

    private class FakeClient( // next response per call, the last one repeats
        vararg val responses: suspend () -> String,
    ) : ExternalAiClient {
        val requests = mutableListOf<ExternalAiRequest>()

        override suspend fun extract(request: ExternalAiRequest): String {
            requests += request
            return responses[minOf(requests.size, responses.size) - 1]()
        }
    }
}

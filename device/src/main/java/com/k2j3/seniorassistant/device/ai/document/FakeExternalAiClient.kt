package com.k2j3.seniorassistant.device.ai.document

class FakeExternalAiClient(private val output: String = CHECKUP_NOTICE) : ExternalAiClient {
    override suspend fun extract(request: ExternalAiRequest): String = output

    private companion object {
        const val CHECKUP_NOTICE = """{"document_detected": true, "title": "건강검진",
            "date": "2026-10-28", "time": "09:00", "place": "○○내과 2층",
            "items": ["신분증", "검진표"], "uncertain_fields": []}"""
    }
}

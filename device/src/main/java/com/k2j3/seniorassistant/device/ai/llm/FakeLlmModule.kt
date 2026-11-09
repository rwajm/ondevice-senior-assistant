package com.k2j3.seniorassistant.device.ai.llm

import java.time.LocalDateTime

class FakeLlmModule(private val output: String = NEXT_SCHEDULE) : LlmModule {
    override suspend fun load() = Unit

    override suspend fun generate(text: String, requestTime: LocalDateTime): String = output

    private companion object {
        const val NEXT_SCHEDULE =
            """<maum_0>{"date": "", "time": "", "place": "", "type": 3}<maum_end>"""
    }
}

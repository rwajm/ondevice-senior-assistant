package com.k2j3.seniorassistant.device.ai.llm

import java.time.LocalDateTime

class FakeLlmModule(private val output: String = NEXT_SCHEDULE) : LlmModule {
    override suspend fun load() = Unit

    override suspend fun generate(text: String, requestTime: LocalDateTime): String = output

    private companion object {
        // the board model's answer to "다음 일정이 뭐야?"; Genie leaves out <maum_end>
        const val NEXT_SCHEDULE =
            """<maum_0>{"date_expression":"","time_expression":"","place":"","type":3}"""
    }
}

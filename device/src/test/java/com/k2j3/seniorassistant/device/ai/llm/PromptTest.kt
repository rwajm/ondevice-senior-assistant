package com.k2j3.seniorassistant.device.ai.llm

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class PromptTest {
    // the tail of the bundle's runtime_prompt_template.txt
    private val template = "...마지막 확정 표현을 따른다.\n\n[요청 시각] {{request_timestamp}}\n[사용자 발화] {{utterance}} Response:"
    private val seoul = ZoneId.of("Asia/Seoul")

    @Test
    fun theRequestTimeAndUtteranceGoWhereTheTrainingDataHadThem() {
        val prompt = fillPrompt(template, LocalDateTime.of(2026, 11, 16, 18, 52, 7, 123), seoul, "내일 오후 7시에 헬스 가야돼. 등록해줘")
        assertEquals(
            "...마지막 확정 표현을 따른다.\n\n[요청 시각] 2026-11-16T18:52:07+09:00\n" +
                "[사용자 발화] 내일 오후 7시에 헬스 가야돼. 등록해줘 Response:",
            prompt,
        )
    }

    @Test
    fun secondsAreAlwaysWritten() { // the bundle's example prompt is 2028-07-22T15:50:00+09:00
        val prompt = fillPrompt("{{request_timestamp}}", LocalDateTime.of(2028, 7, 22, 15, 50), seoul, "")
        assertEquals("2028-07-22T15:50:00+09:00", prompt)
    }

    @Test
    fun theUtteranceStaysOnOneLine() {
        assertEquals("[사용자 발화] 다음 일정 알려 줘", fillPrompt("[사용자 발화] {{utterance}}", LocalDateTime.MIN, seoul, "  다음\n일정  알려 줘 "))
    }
}

package com.k2j3.seniorassistant.device.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/** 2026-11-17 보드에서 실제로 들은 STT 글자 */
class TranscriptTextTest {
    @Test
    fun theBoardsTranscriptsReadNaturally() {
        mapOf(
            "내일 오후 7 시에 헬스 가야 되 등록 해 죠." to "내일 오후 7시에 헬스 가야 되 등록해 죠",
            "30 분 뒤 에 약 먹 으라고 알람 맞춰 줘." to "30분 뒤에 약 먹으라고 알람 맞춰 줘",
            "이번주 토요일 라 12 시의 가족란 점심 먹 기 했어 달력 에 써 줘." to "이번주 토요일 라 12시의 가족란 점심 먹기 했어 달력에 써 줘",
            "내일 모레 3 시에 병원 가  거 적어 줘." to "내일 모레 3시에 병원 가 거 적어 줘",
            "내일 모레 3 시에 병원 가는 거 적어줘." to "내일 모레 3시에 병원 가는 거 적어줘",
            "30 분 뒤 에 에 약 먹 으라고 말란 맞춰 줘." to "30분 뒤에에 약 먹으라고 말란 맞춰 줘",
            "무료 저녁 시에 병원 가 는 거 적거 죠." to "무료 저녁 시에 병원 가는 거 적거 죠",
        ).forEach { (heard, shown) -> assertEquals(heard, shown, tidyTranscript(heard)) }
    }

    @Test
    fun shortAnswersLoseOnlyTheFinalPeriod() {
        assertEquals("응", tidyTranscript("응."))
        assertEquals("오후", tidyTranscript(" 오후. "))
        assertEquals("아니?", tidyTranscript("아니?"))
        assertEquals("내일 뭐 있어?", tidyTranscript("내일 뭐 있어?"))
        assertEquals("", tidyTranscript(""))
    }

    @Test
    fun wordsThatCanStandAloneAreLeftApart() {
        assertEquals("병원 가 거", tidyTranscript("병원 가 거")) // 가 = go, not the particle
        assertEquals("뭐 해", tidyTranscript("뭐 해")) // one-syllable word before 해
        assertEquals("다음 일정이 뭐야?", tidyTranscript("다음 일정이 뭐야?"))
        assertEquals("오늘 간다고 해 놓은 게", tidyTranscript("오늘 간다고 해 놓은 게")) // 해 after a verb, not a noun
    }

    @Test
    fun numbersJoinTheirUnitButNotOtherWords() {
        assertEquals("3시 30분", tidyTranscript("3 시 30 분"))
        assertEquals("2개", tidyTranscript("2 개"))
        assertEquals("010 1234", tidyTranscript("010 1234"))
    }
}

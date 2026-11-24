package com.k2j3.seniorassistant.device.server

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenNumbersTest {
    @Test
    fun hoursAndCountsAreReadInNativeKorean() {
        assertEquals("오후 세 시", forSpeech("오후 3시"))
        assertEquals("오전 열두 시", forSpeech("오전 12시"))
        assertEquals("오전 열한 시, 산책", forSpeech("오전 11시, 산책"))
        assertEquals("두 시간 뒤", forSpeech("2시간 뒤"))
        assertEquals("오늘 일정은 두 개예요.", forSpeech("오늘 일정은 2개예요."))
        assertEquals("스무 개, 스물한 명", forSpeech("20개, 21명"))
    }

    @Test
    fun minutesMonthsAndDaysAreReadInSinoKorean() {
        assertEquals("오후 네 시 삼십 분", forSpeech("오후 4시 30분"))
        assertEquals("오전 여덟 시 오 분", forSpeech("오전 8시 5분"))
        assertEquals("유월 십오 일", forSpeech("6월 15일"))
        assertEquals("시월 삼 일", forSpeech("10월 3일"))
        assertEquals("십일월 이십 일 (목)", forSpeech("11월 20일 (목)"))
    }

    @Test
    fun theBoardsRealSentencesSoundRight() {
        assertEquals("오전 세 시요, 오후 세 시요?", forSpeech(Sentences.askHalfDay(java.time.LocalTime.of(3, 0))))
        assertEquals(
            "이렇게 알람을 맞출까요? 오늘 오후 네 시 삼십 분, 약 복용 알람이에요.",
            forSpeech("이렇게 알람을 맞출까요? 오늘 오후 4시 30분, 약 복용 알람이에요."),
        )
    }

    @Test
    fun numbersWithoutAUnitAndOtherWordsStayAsTheyAre() {
        assertEquals("010-1234-5678", forSpeech("010-1234-5678"))
        assertEquals("오늘 일정이 있어요", forSpeech("오늘 일정이 있어요"))
        assertEquals("2일정", forSpeech("2일정"))
        assertEquals("", forSpeech(""))
    }

    @Test
    fun sinoAndNativeNumbers() {
        assertEquals("영", sino(0))
        assertEquals("십", sino(10))
        assertEquals("백십오", sino(115))
        assertEquals("이천이십육", sino(2026))
        assertEquals("만이천", sino(12000))
        assertEquals("한", native(1))
        assertEquals("아흔아홉", native(99))
        assertEquals(null, native(100))
    }
}

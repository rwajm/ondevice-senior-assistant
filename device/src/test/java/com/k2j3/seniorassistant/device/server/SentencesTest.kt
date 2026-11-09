package com.k2j3.seniorassistant.device.server

import com.k2j3.seniorassistant.device.ai.document.DocumentFields
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SentencesTest {
    private val today = LocalDate.of(2026, 10, 27)

    @Test
    fun scheduleIsDescribedWithDayTimePlaceAndItems() {
        val checkup = Schedule(
            title = "건강검진",
            date = today.plusDays(1),
            time = LocalTime.of(14, 30),
            place = "○○내과",
            items = listOf("신분증", "검진표"),
        )
        assertEquals(
            "내일 오후 2시 30분, ○○내과 건강검진 일정이 있어요. 챙길 것은 신분증, 검진표.",
            Sentences.found(listOf(checkup), today),
        )
    }

    @Test
    fun daysAndHoursAreSpokenNaturally() {
        val noon = Schedule(title = "점심", date = today, time = LocalTime.NOON)
        val midnight = Schedule(title = "약", date = today.plusDays(2), time = LocalTime.MIDNIGHT)
        val later = Schedule(title = "노인정", date = LocalDate.of(2026, 11, 3))
        assertEquals("오늘 오후 12시, 점심 일정이 있어요.", Sentences.found(listOf(noon), today))
        assertEquals("모레 오전 12시, 약 일정이 있어요.", Sentences.found(listOf(midnight), today))
        assertEquals("11월 3일, 노인정 일정이 있어요.", Sentences.found(listOf(later), today))
    }

    @Test
    fun emptyResultAndSummaryAndDocument() {
        assertEquals(Sentences.NOT_FOUND, Sentences.found(emptyList(), today))
        val walk = Schedule(title = "산책", date = today, time = LocalTime.of(7, 0))
        assertEquals(
            "오늘 일정은 1개예요. 오늘 오전 7시, 산책 일정이 있어요.",
            Sentences.dailySummary(listOf(walk), today),
        )
        val fields = DocumentFields(title = "건강검진", date = today.plusDays(1), items = listOf("신분증"))
        assertEquals(
            "이런 내용이 있어요. 내일 건강검진. 챙길 것은 신분증.",
            Sentences.document(fields, today),
        )
    }
}

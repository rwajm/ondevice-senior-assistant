package com.k2j3.seniorassistant.device.server

import com.k2j3.seniorassistant.device.ai.document.DocumentFields
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.data.schedule.ScheduleKind
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
    fun anAlarmSaysItIsAnAlarm() {
        val pill = Schedule(title = "약 복용", date = today, time = LocalTime.of(16, 30), kind = ScheduleKind.ALARM)
        val health = Schedule(title = "헬스", date = today.plusDays(1), time = LocalTime.of(19, 0))
        assertEquals("이렇게 알람을 맞출까요? 오늘 오후 4시 30분, 약 복용 알람이에요.", Sentences.draft(pill, today))
        assertEquals("이렇게 적을까요? 내일 오후 7시, 헬스 일정이에요.", Sentences.draft(health, today))
        assertEquals("오늘 오후 4시 30분, 약 복용 알람이 있어요.", Sentences.found(listOf(pill), today))
    }

    @Test
    fun halfDayIsAskedWithTheHourSpoken() {
        assertEquals("오전 3시요, 오후 3시요?", Sentences.askHalfDay(LocalTime.of(3, 0)))
        assertEquals("오전 8시 30분이요, 오후 8시 30분이요?", Sentences.askHalfDay(LocalTime.of(8, 30)))
        assertEquals(
            "오전인지 오후인지 말씀해 주세요. 오전 3시요, 오후 3시요?",
            Sentences.askHalfDayAgain(LocalTime.of(3, 0)),
        )
    }

    @Test
    fun shortAnswersPickTheHalfOfTheDay() {
        listOf("오전요", "아침에", "새벽 세 시", "아침 여덟 시").forEach { assertEquals(it, HalfDay.MORNING, halfDayOf(it)) }
        listOf("오후요", "오후", "저녁이야", "밤에", "낮 세 시").forEach { assertEquals(it, HalfDay.AFTERNOON, halfDayOf(it)) }
        listOf("", "응", "몰라", "오전 말고 오후").forEach { assertEquals(it, null, halfDayOf(it)) }
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

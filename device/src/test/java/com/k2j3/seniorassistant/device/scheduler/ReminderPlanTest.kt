package com.k2j3.seniorassistant.device.scheduler

import com.k2j3.seniorassistant.device.data.schedule.Schedule
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderPlanTest {
    private val morning = LocalTime.of(6, 0)
    private val date = LocalDate.of(2026, 10, 28)
    private val timed = Schedule(title = "건강검진", date = date, time = LocalTime.of(9, 0))
    private val untimed = timed.copy(title = "노인정", time = null)

    @Test
    fun futureReminderIsSetAtStartMinusRemindBefore() {
        val now = date.minusDays(2).atTime(12, 0)
        val action = planReminder(timed, Duration.ofDays(1), morning, now)
        assertEquals(ReminderAction.At(date.minusDays(1).atTime(9, 0)), action)
    }

    @Test
    fun untimedDayBeforeRemindsAtPreviousMorning() {
        val now = date.minusDays(2).atTime(12, 0)
        val action = planReminder(untimed, Duration.ofDays(1), morning, now)
        assertEquals(ReminderAction.At(date.minusDays(1).atTime(6, 0)), action)
    }

    @Test
    fun untimedOtherRemindersAreLeftToDailySummary() {
        val now = date.minusDays(1).atTime(12, 0)
        assertEquals(ReminderAction.Skip, planReminder(untimed, Duration.ofHours(1), morning, now))
        assertEquals(ReminderAction.Skip, planReminder(untimed, Duration.ZERO, morning, now))
    }

    @Test
    fun missedReminderBeforeStartFiresNow() {
        val action = planReminder(timed, Duration.ofHours(1), morning, date.atTime(8, 30))
        assertEquals(ReminderAction.Now, action)
    }

    @Test
    fun onTimeReminderFiresWithinItsMinute() {
        val now = LocalDateTime.of(date, LocalTime.of(9, 0, 0, 300_000_000))
        assertEquals(ReminderAction.Now, planReminder(timed, Duration.ZERO, morning, now))
    }

    @Test
    fun reminderAfterStartIsSkipped() {
        val action = planReminder(timed, Duration.ZERO, morning, date.atTime(9, 1))
        assertEquals(ReminderAction.Skip, action)
    }

    @Test
    fun dailySummaryIsTodayBeforeMorningAndTomorrowAfter() {
        assertEquals(date.atTime(6, 0), nextDailySummary(morning, date.atTime(5, 59)))
        assertEquals(date.plusDays(1).atTime(6, 0), nextDailySummary(morning, date.atTime(6, 0)))
    }
}

package com.k2j3.seniorassistant.device.scheduler

import com.k2j3.seniorassistant.device.data.schedule.Schedule
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

sealed interface ReminderAction {
    data class At(val time: LocalDateTime) : ReminderAction

    data object Now : ReminderAction

    data object Skip : ReminderAction
}

private val DAY_BEFORE = Duration.ofDays(1)

fun Schedule.startAt(morning: LocalTime): LocalDateTime = date.atTime(time ?: morning)

fun planReminder(
    schedule: Schedule,
    remindBefore: Duration,
    morning: LocalTime,
    now: LocalDateTime,
): ReminderAction {
    if (schedule.time == null && remindBefore != DAY_BEFORE) {
        return ReminderAction.Skip // the daily summary covers untimed schedules on the day
    }
    val start = schedule.startAt(morning)
    val remindAt = start - remindBefore
    return when {
        remindAt > now -> ReminderAction.At(remindAt)
        start >= now.truncatedTo(ChronoUnit.MINUTES) -> ReminderAction.Now // missed, start ahead
        else -> ReminderAction.Skip
    }
}

fun nextDailySummary(morning: LocalTime, now: LocalDateTime): LocalDateTime {
    val today = now.toLocalDate().atTime(morning)
    return if (today > now) today else today.plusDays(1)
}

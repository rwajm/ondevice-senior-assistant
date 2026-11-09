package com.k2j3.seniorassistant.device.server

import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.data.schedule.ScheduleKind
import com.k2j3.seniorassistant.device.data.schedule.ScheduleRepository
import com.k2j3.seniorassistant.device.data.schedule.ScheduleWithReminders
import com.k2j3.seniorassistant.device.data.settings.SettingsRepository
import com.k2j3.seniorassistant.device.scheduler.ReminderScheduler
import java.time.Duration
import java.time.LocalDate

class ScheduleApi internal constructor(
    private val schedules: ScheduleRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend fun getSchedules(from: LocalDate, to: LocalDate): List<ScheduleWithReminders> =
        schedules.getSchedules(from, to)

    suspend fun getSchedule(id: Long): ScheduleWithReminders? = schedules.getSchedule(id)

    suspend fun addSchedule(schedule: Schedule, remindBefore: List<Duration>? = null): Long {
        val id = schedules.addSchedule(schedule, remindTimes(schedule, remindBefore))
        scheduler.reschedule(id)
        return id
    }

    suspend fun updateSchedule(schedule: Schedule, remindBefore: List<Duration>): Boolean {
        if (!schedules.updateSchedule(schedule, remindTimes(schedule, remindBefore))) return false
        scheduler.reschedule(schedule.id)
        return true
    }

    suspend fun deleteSchedule(id: Long): Boolean {
        scheduler.cancelReminders(id)
        return schedules.deleteSchedule(id)
    }

    private suspend fun remindTimes(schedule: Schedule, remindBefore: List<Duration>?) = when {
        schedule.kind == ScheduleKind.ALARM -> listOf(Duration.ZERO) // FR-07
        remindBefore != null -> remindBefore
        else -> settings.getSettings().remindBefore
    }
}

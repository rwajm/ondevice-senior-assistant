package com.k2j3.seniorassistant.device.scheduler

import com.k2j3.seniorassistant.device.data.schedule.ReminderWithSchedule
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ReminderEvents { // events are dropped if not collected
    private val _due = MutableSharedFlow<ReminderWithSchedule>(extraBufferCapacity = 16)

    private val _dailySummary = MutableSharedFlow<List<Schedule>>(extraBufferCapacity = 1)

    val due: SharedFlow<ReminderWithSchedule> = _due.asSharedFlow()

    val dailySummary: SharedFlow<List<Schedule>> = _dailySummary.asSharedFlow() // today's schedules

    internal fun emitDue(reminder: ReminderWithSchedule) {
        _due.tryEmit(reminder)
    }

    internal fun emitDailySummary(schedules: List<Schedule>) {
        _dailySummary.tryEmit(schedules)
    }
}

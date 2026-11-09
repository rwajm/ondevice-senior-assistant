package com.k2j3.seniorassistant.device.data.schedule

import androidx.room.Embedded
import androidx.room.Relation

data class ScheduleWithReminders(
    @Embedded val schedule: Schedule,
    @Relation(parentColumn = "id", entityColumn = "schedule_id")
    val reminders: List<Reminder>,
)

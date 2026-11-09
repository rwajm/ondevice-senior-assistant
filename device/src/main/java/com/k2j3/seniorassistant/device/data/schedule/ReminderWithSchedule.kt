package com.k2j3.seniorassistant.device.data.schedule

import androidx.room.Embedded
import androidx.room.Relation

data class ReminderWithSchedule(
    @Embedded val reminder: Reminder,
    @Relation(parentColumn = "schedule_id", entityColumn = "id")
    val schedule: Schedule,
)

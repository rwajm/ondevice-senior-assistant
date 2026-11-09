package com.k2j3.seniorassistant.device.data.schedule

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "schedule", indices = [Index("date")])
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: LocalDate,
    val time: LocalTime? = null,
    val place: String = "",
    val items: List<String> = emptyList(),
    val kind: ScheduleKind = ScheduleKind.CALENDAR,
)

enum class ScheduleKind {
    CALENDAR,
    ALARM,
}

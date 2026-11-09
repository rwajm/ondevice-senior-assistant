package com.k2j3.seniorassistant.device.data.schedule

import androidx.room.withTransaction
import com.k2j3.seniorassistant.device.data.AppDatabase
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleRepository(private val db: AppDatabase) {
    private val dao = db.scheduleDao()

    // null matches any; place matches by substring
    suspend fun getSchedules(
        from: LocalDate,
        to: LocalDate,
        time: LocalTime? = null,
        place: String? = null,
    ): List<ScheduleWithReminders> = dao.getBetween(from, to, time, place) // both ends inclusive

    suspend fun getNextSchedules(
        after: LocalDateTime,
        limit: Int,
        place: String? = null,
    ): List<ScheduleWithReminders> =
        dao.getNext(after.toLocalDate(), after.toLocalTime(), place, limit)

    suspend fun getRecentSchedules(limit: Int, place: String? = null): List<ScheduleWithReminders> =
        dao.getRecent(place, limit)

    suspend fun getSchedule(id: Long): ScheduleWithReminders? = dao.getWithReminders(id)

    suspend fun addSchedule(schedule: Schedule, remindBefore: List<Duration>): Long =
        db.withTransaction {
            val id = dao.insert(schedule.copy(id = 0))
            dao.insertReminders(reminders(id, remindBefore.distinct()))
            id
        }

    suspend fun updateSchedule(schedule: Schedule, remindBefore: List<Duration>): Boolean =
        db.withTransaction {
            val old = dao.get(schedule.id) ?: return@withTransaction false
            dao.update(schedule)
            if (old.date != schedule.date || old.time != schedule.time) {
                dao.resetNotified(schedule.id) // remind again at the new time
            }
            val wanted = remindBefore.toSet()
            val existing = dao.getReminders(schedule.id)
            dao.deleteReminders(existing.filter { it.remindBefore !in wanted }.map { it.id })
            dao.insertReminders(reminders(schedule.id, wanted - existing.map { it.remindBefore }))
            true
        }

    suspend fun deleteSchedule(id: Long): Boolean = dao.delete(id) > 0 // reminders cascade

    suspend fun getPendingReminders(): List<ReminderWithSchedule> = dao.getPendingReminders()

    suspend fun getPendingReminders(scheduleId: Long): List<ReminderWithSchedule> =
        dao.getPendingReminders(scheduleId)

    suspend fun getPendingReminder(id: Long): ReminderWithSchedule? = dao.getPendingReminder(id)

    suspend fun markNotified(id: Long): Boolean =
        dao.markNotified(id) > 0 // false if missing

    private fun reminders(scheduleId: Long, remindBefore: Collection<Duration>): List<Reminder> =
        remindBefore.map { Reminder(scheduleId = scheduleId, remindBefore = it) }
}

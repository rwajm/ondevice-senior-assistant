package com.k2j3.seniorassistant.device.data.schedule

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import java.time.LocalDate
import java.time.LocalTime

private const val PLACE_MATCH = "(:place IS NULL OR place LIKE '%' || :place || '%')"

@Dao
interface ScheduleDao {
    // NULL time sorts first
    @Transaction
    @Query(
        """SELECT * FROM schedule WHERE date BETWEEN :from AND :to
        AND (:time IS NULL OR time = :time) AND $PLACE_MATCH ORDER BY date, time""",
    )
    suspend fun getBetween(
        from: LocalDate,
        to: LocalDate,
        time: LocalTime?,
        place: String?,
    ): List<ScheduleWithReminders>

    // untimed schedules on the given date count as upcoming
    @Transaction
    @Query(
        """SELECT * FROM schedule WHERE (date > :date OR (date = :date AND
        (time IS NULL OR time >= :time))) AND $PLACE_MATCH ORDER BY date, time LIMIT :limit""",
    )
    suspend fun getNext(
        date: LocalDate,
        time: LocalTime,
        place: String?,
        limit: Int,
    ): List<ScheduleWithReminders>

    // ids grow in registration order
    @Transaction
    @Query("SELECT * FROM schedule WHERE $PLACE_MATCH ORDER BY id DESC LIMIT :limit")
    suspend fun getRecent(place: String?, limit: Int): List<ScheduleWithReminders>

    @Transaction
    @Query("SELECT * FROM schedule WHERE id = :id")
    suspend fun getWithReminders(id: Long): ScheduleWithReminders?

    @Query("SELECT * FROM schedule WHERE id = :id")
    suspend fun get(id: Long): Schedule?

    @Insert
    suspend fun insert(schedule: Schedule): Long

    @Update
    suspend fun update(schedule: Schedule): Int

    @Query("DELETE FROM schedule WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("SELECT * FROM reminder WHERE schedule_id = :scheduleId")
    suspend fun getReminders(scheduleId: Long): List<Reminder>

    @Transaction
    @Query("SELECT * FROM reminder WHERE notified = 0")
    suspend fun getPendingReminders(): List<ReminderWithSchedule>

    @Insert
    suspend fun insertReminders(reminders: List<Reminder>)

    @Query("DELETE FROM reminder WHERE id IN (:ids)")
    suspend fun deleteReminders(ids: List<Long>)

    @Query("UPDATE reminder SET notified = 1 WHERE id = :id")
    suspend fun markNotified(id: Long): Int

    @Query("UPDATE reminder SET notified = 0 WHERE schedule_id = :scheduleId")
    suspend fun resetNotified(scheduleId: Long)
}

package com.k2j3.seniorassistant.device.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.k2j3.seniorassistant.device.data.AppDatabase
import com.k2j3.seniorassistant.device.data.schedule.ReminderWithSchedule
import com.k2j3.seniorassistant.device.data.schedule.ScheduleRepository
import com.k2j3.seniorassistant.device.data.settings.SettingsRepository
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderScheduler(
    private val context: Context,
    private val schedules: ScheduleRepository,
    private val settings: SettingsRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll() { // after boot or a morning time change
        val morning = morning()
        schedules.getPendingReminders().forEach { applyPlan(it, morning) }
        setAlarm(nextDailySummary(morning, now()), dailySummaryAlarm())
    }

    suspend fun reschedule(scheduleId: Long) {
        val morning = morning()
        schedules.getPendingReminders(scheduleId).forEach { applyPlan(it, morning) }
    }

    suspend fun cancelReminders(scheduleId: Long) { // call before deleting the schedule
        schedules.getSchedule(scheduleId)?.reminders?.forEach { cancelAlarm(reminderAlarm(it.id)) }
    }

    suspend fun onAlarm(reminderId: Long) {
        val due = schedules.getPendingReminder(reminderId)
            ?: return // notified or deleted since the alarm was set
        applyPlan(due, morning())
    }

    suspend fun onDailySummary() {
        val today = now().toLocalDate()
        val todays = schedules.getSchedules(today, today).map { it.schedule }
        if (todays.isNotEmpty()) {
            ReminderEvents.emitDailySummary(todays)
        }
        setAlarm(nextDailySummary(morning(), now()), dailySummaryAlarm())
    }

    internal fun isScheduled(reminderId: Long): Boolean =
        existingPendingIntent(reminderAlarm(reminderId)) != null

    internal fun isDailySummaryScheduled(): Boolean =
        existingPendingIntent(dailySummaryAlarm()) != null

    private suspend fun morning(): LocalTime = settings.getSettings().morningReminderTime

    private suspend fun applyPlan(due: ReminderWithSchedule, morning: LocalTime) {
        val (reminder, schedule) = due
        val alarm = reminderAlarm(reminder.id)
        when (val action = planReminder(schedule, reminder.remindBefore, morning, now())) {
            is ReminderAction.At -> setAlarm(action.time, alarm)
            ReminderAction.Now -> {
                cancelAlarm(alarm)
                schedules.markNotified(reminder.id)
                ReminderEvents.emitDue(due)
            }
            ReminderAction.Skip -> cancelAlarm(alarm)
        }
    }

    private fun setAlarm(time: LocalDateTime, alarm: Alarm) {
        val millis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.requestCode,
            alarm.intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
    }

    private fun cancelAlarm(alarm: Alarm) {
        val pendingIntent = existingPendingIntent(alarm) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun existingPendingIntent(alarm: Alarm): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            alarm.requestCode,
            alarm.intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun reminderAlarm(reminderId: Long): Alarm =
        Alarm(
            Intent(context, ReminderAlarmReceiver::class.java)
                .putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId),
            reminderId.toInt(),
        )

    private fun dailySummaryAlarm(): Alarm =
        Alarm(Intent(context, DailySummaryReceiver::class.java), 0)

    private class Alarm(val intent: Intent, val requestCode: Int)

    companion object {
        fun from(context: Context): ReminderScheduler {
            val db = AppDatabase.getInstance(context)
            return ReminderScheduler(
                context.applicationContext,
                ScheduleRepository(db),
                SettingsRepository(db),
            )
        }
    }
}

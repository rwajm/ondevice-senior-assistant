package com.k2j3.seniorassistant.device.scheduler

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2j3.seniorassistant.device.data.AppDatabase
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.data.schedule.ScheduleRepository
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderAlarmReceiverTest { // real database, as the receiver uses
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val schedules = ScheduleRepository(AppDatabase.getInstance(context))
    private var scheduleId = 0L

    @After
    fun tearDown() = runBlocking {
        schedules.deleteSchedule(scheduleId)
        Unit
    }

    @Test
    fun alarmReachesReminderEvents() = runBlocking {
        val start = LocalDateTime.now().plusSeconds(5).withNano(0)
        val schedule =
            Schedule(title = "약 먹기", date = start.toLocalDate(), time = start.toLocalTime())
        scheduleId = schedules.addSchedule(schedule, listOf(Duration.ZERO))
        val due = async(start = CoroutineStart.UNDISPATCHED) {
            ReminderEvents.due.first { it.schedule.id == scheduleId }
        }

        ReminderScheduler.from(context).reschedule(scheduleId)
        val event = withTimeout(30_000) { due.await() }
        Log.d(TAG, "due by alarm at ${LocalDateTime.now()}: $event")
        assertEquals("약 먹기", event.schedule.title)
    }

    private companion object {
        const val TAG = "ReminderAlarmReceiverTest"
    }
}

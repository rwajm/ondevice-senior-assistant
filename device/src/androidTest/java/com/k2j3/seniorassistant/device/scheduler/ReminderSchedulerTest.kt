package com.k2j3.seniorassistant.device.scheduler

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2j3.seniorassistant.device.data.AppDatabase
import com.k2j3.seniorassistant.device.data.schedule.ReminderWithSchedule
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.data.schedule.ScheduleRepository
import com.k2j3.seniorassistant.device.data.settings.SettingsRepository
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher
@RunWith(AndroidJUnit4::class)
class ReminderSchedulerTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var schedules: ScheduleRepository
    private lateinit var scheduler: ReminderScheduler

    private val now = LocalDateTime.now().withSecond(0).withNano(0)

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        schedules = ScheduleRepository(db)
        scheduler = ReminderScheduler(context, schedules, SettingsRepository(db)) { now }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun exactAlarmsAreAllowed() {
        assertTrue(context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
    }

    @Test
    fun futureRemindersAreSetAndMissedOnesAreNotifiedOnce() = runTest {
        val received = collect(ReminderEvents.due)
        val laterId = schedules.addSchedule(
            scheduleAt("건강검진", now.plusDays(2)),
            listOf(Duration.ofDays(1), Duration.ofHours(1)),
        )
        val soonId = schedules.addSchedule(
            scheduleAt("약 먹기", now.plusMinutes(30)),
            listOf(Duration.ofHours(1)),
        )

        scheduler.rescheduleAll()
        val laterReminders = schedules.getSchedule(laterId)!!.reminders
        assertTrue(laterReminders.all { scheduler.isScheduled(it.id) })
        assertTrue(scheduler.isDailySummaryScheduled())
        Log.d(TAG, "due: ${received.map { it.schedule.title }}")
        assertEquals(listOf(soonId), received.map { it.schedule.id }) // 1h before already passed

        scheduler.rescheduleAll()
        scheduler.onAlarm(received.single().reminder.id)
        assertEquals(1, received.size) // a notified reminder is not shown again

        scheduler.cancelReminders(laterId)
        assertFalse(laterReminders.any { scheduler.isScheduled(it.id) })
    }

    @Test
    fun dailySummaryListsTodaysSchedulesOnly() = runTest {
        val received = collect(ReminderEvents.dailySummary)
        scheduler.onDailySummary()
        assertTrue(received.isEmpty()) // nothing today

        val today = now.toLocalDate()
        schedules.addSchedule(Schedule(title = "노인정", date = today), emptyList())
        schedules.addSchedule(Schedule(title = "내일", date = today.plusDays(1)), emptyList())
        scheduler.onDailySummary()
        Log.d(TAG, "summary: ${received.map { day -> day.map { it.title } }}")
        assertEquals(listOf(listOf("노인정")), received.map { day -> day.map { it.title } })
        assertTrue(scheduler.isDailySummaryScheduled())
    }

    private fun <T> TestScope.collect(flow: Flow<T>): List<T> {
        val received = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(received) }
        return received
    }

    private fun scheduleAt(title: String, start: LocalDateTime): Schedule =
        Schedule(title = title, date = start.toLocalDate(), time = start.toLocalTime())

    private companion object {
        const val TAG = "ReminderSchedulerTest"
    }
}

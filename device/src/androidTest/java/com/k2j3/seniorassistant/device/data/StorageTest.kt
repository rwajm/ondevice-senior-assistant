package com.k2j3.seniorassistant.device.data

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.data.schedule.ScheduleKind
import com.k2j3.seniorassistant.device.data.schedule.ScheduleRepository
import com.k2j3.seniorassistant.device.data.schedule.ScheduleWithReminders
import com.k2j3.seniorassistant.device.data.settings.FontSize
import com.k2j3.seniorassistant.device.data.settings.Settings
import com.k2j3.seniorassistant.device.data.settings.SettingsRepository
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageTest {
    private lateinit var db: AppDatabase
    private lateinit var schedules: ScheduleRepository
    private lateinit var settings: SettingsRepository

    private val dayBefore = Duration.ofDays(1)
    private val hourBefore = Duration.ofHours(1)
    private val onTime = Duration.ZERO

    private val checkup = Schedule(
        title = "건강검진",
        date = LocalDate.of(2026, 10, 28),
        time = LocalTime.of(9, 0),
        place = "○○내과 2층",
        items = listOf("신분증", "검진표"),
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        schedules = ScheduleRepository(db)
        settings = SettingsRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addedScheduleKeepsAllFieldsAndReminders() = runTest {
        val id = schedules.addSchedule(checkup, listOf(dayBefore, hourBefore))
        val saved = schedules.getSchedule(id)
        Log.d(TAG, "saved: $saved")
        assertEquals(checkup.copy(id = id), saved?.schedule)
        val times = saved?.reminders.orEmpty().map { it.remindBefore }.toSet()
        assertEquals(setOf(dayBefore, hourBefore), times)
        assertTrue(saved?.reminders.orEmpty().none { it.notified })
    }

    @Test
    fun rangeIncludesBothEndsInDateAndTimeOrder() = runTest {
        val alarm = Schedule(
            title = "약 먹기",
            date = LocalDate.of(2026, 10, 27),
            time = LocalTime.of(20, 0),
            kind = ScheduleKind.ALARM,
        )
        val morning = alarm.copy(title = "산책", time = LocalTime.of(7, 0))
        val allDay = alarm.copy(title = "노인정", time = null)
        val outside = alarm.copy(title = "범위 밖", date = LocalDate.of(2026, 10, 29))
        listOf(checkup, alarm, morning, allDay, outside).forEach {
            schedules.addSchedule(it, emptyList())
        }

        val from = LocalDate.of(2026, 10, 27)
        val found = schedules.getSchedules(from, LocalDate.of(2026, 10, 28))
        Log.d(TAG, "range: ${found.map { it.schedule.title }}")
        assertEquals(listOf("노인정", "산책", "약 먹기", "건강검진"), found.map { it.schedule.title })
    }

    @Test
    fun searchByConditionNextAndRecent() = runTest {
        val today = LocalDate.of(2026, 10, 27)
        val eleven = LocalTime.of(11, 0)
        val dentist = Schedule(title = "치과", date = today, time = eleven, place = "△△치과")
        val pastToday = dentist.copy(title = "산책", time = LocalTime.of(7, 0), place = "공원")
        val allDay = dentist.copy(title = "노인정", time = null, place = "노인정")
        val checkupLater = checkup.copy(date = today.plusDays(1))
        val yesterday = dentist.copy(title = "어제 진료", date = today.minusDays(1), place = "○○내과")
        listOf(yesterday, checkupLater, dentist, pastToday, allDay).forEach {
            schedules.addSchedule(it, emptyList())
        }

        val byTime = schedules.getSchedules(today, today, time = eleven)
        assertEquals(listOf("치과"), byTime.titles())
        val byPlace = schedules.getSchedules(today.minusDays(1), today.plusDays(1), place = "내과")
        assertEquals(listOf("어제 진료", "건강검진"), byPlace.titles())

        val now = today.atTime(9, 0)
        val next = schedules.getNextSchedules(now, limit = 3)
        Log.d(TAG, "next: ${next.titles()}")
        assertEquals(listOf("노인정", "치과", "건강검진"), next.titles()) // untimed today included
        assertEquals(listOf("건강검진"), schedules.getNextSchedules(now, 1, place = "내과").titles())

        val recent = schedules.getRecentSchedules(limit = 2)
        Log.d(TAG, "recent: ${recent.titles()}")
        assertEquals(listOf("노인정", "산책"), recent.titles())
        assertEquals(listOf("건강검진"), schedules.getRecentSchedules(1, place = "○○내과 2층").titles())
    }

    @Test
    fun updateAndDeleteReportMissingSchedules() = runTest {
        val id = schedules.addSchedule(checkup, emptyList())
        val moved = checkup.copy(id = id, place = "○○내과 3층")
        assertTrue(schedules.updateSchedule(moved, emptyList()))
        assertEquals("○○내과 3층", schedules.getSchedule(id)?.schedule?.place)

        assertTrue(schedules.deleteSchedule(id))
        assertNull(schedules.getSchedule(id))
        assertFalse(schedules.updateSchedule(moved, emptyList()))
        assertFalse(schedules.deleteSchedule(id))
    }

    @Test
    fun remindersFollowScheduleChanges() = runTest {
        val id = schedules.addSchedule(checkup, listOf(dayBefore, hourBefore))
        val dayBeforeId = schedules.getPendingReminders()
            .single { it.reminder.remindBefore == dayBefore }.reminder.id
        assertTrue(schedules.markNotified(dayBeforeId))

        val moved = checkup.copy(id = id, place = "○○내과 3층")
        schedules.updateSchedule(moved, listOf(dayBefore, hourBefore))
        assertEquals(listOf(hourBefore), pendingTimes()) // place change keeps notified

        schedules.updateSchedule(moved, listOf(dayBefore, onTime))
        assertEquals(setOf(onTime), pendingTimes().toSet()) // hour removed, on-time added

        schedules.updateSchedule(moved.copy(time = LocalTime.of(10, 0)), listOf(dayBefore, onTime))
        val pending = schedules.getPendingReminders()
        Log.d(TAG, "after time change: $pending")
        val times = pending.map { it.reminder.remindBefore }.toSet()
        assertEquals(setOf(dayBefore, onTime), times) // time change resets notified
        assertTrue(pending.all { it.schedule.time == LocalTime.of(10, 0) })

        schedules.deleteSchedule(id)
        assertTrue(schedules.getPendingReminders().isEmpty())
        assertFalse(schedules.markNotified(dayBeforeId))
    }

    @Test
    fun settingsStartWithDefaultsAndKeepUpdates() = runTest {
        assertEquals(Settings(), settings.getSettings())

        val changed = Settings(
            userName = "김XX",
            fontSize = FontSize.LARGE,
            remindBefore = listOf(onTime),
        )
        settings.updateSettings(changed)
        val saved = settings.getSettings()
        Log.d(TAG, "settings: $saved")
        assertEquals(changed, saved)
    }

    private suspend fun pendingTimes(): List<Duration> =
        schedules.getPendingReminders().map { it.reminder.remindBefore }

    private fun List<ScheduleWithReminders>.titles(): List<String> = map { it.schedule.title }

    private companion object {
        const val TAG = "StorageTest"
    }
}

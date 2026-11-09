package com.k2j3.seniorassistant.device.ui.schedule

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.data.schedule.ScheduleWithReminders
import com.k2j3.seniorassistant.device.server.BoardApiServer
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScheduleViewModel(private val server: BoardApiServer) : ViewModel() {
    private val _schedules = MutableStateFlow<List<ScheduleWithReminders>>(emptyList())

    val schedules: StateFlow<List<ScheduleWithReminders>> = _schedules.asStateFlow()

    var selectedDate: LocalDate = LocalDate.now()
        private set

    fun loadTodayAndTomorrow() { // FR-01
        val today = LocalDate.now()
        load(today, today.plusDays(1))
    }

    fun selectDate(date: LocalDate) {
        selectedDate = date
        load(date, date)
    }

    fun firstScheduleId(): Long = schedules.value.firstOrNull()?.schedule?.id ?: 0

    fun add(date: LocalDate, draft: Schedule?) {
        viewModelScope.launch {
            val schedule = draft
                ?: Schedule(title = "견본 일정", date = date, time = LocalTime.of(10, 0))
            val id = server.schedules.addSchedule(schedule)
            Log.d(TAG, "added $id: $schedule")
        }
    }

    fun update(id: Long, draft: Schedule?) {
        viewModelScope.launch {
            val current = server.schedules.getSchedule(id)
            if (current == null) {
                Log.d(TAG, "update: no schedule $id")
                return@launch
            }
            val changed = draft?.copy(id = id) ?: current.schedule.copy(place = "견본 장소")
            val remindBefore = current.reminders.map { it.remindBefore }
            val updated = server.schedules.updateSchedule(changed, remindBefore)
            Log.d(TAG, "updated $id: $updated, $changed")
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            Log.d(TAG, "deleted $id: ${server.schedules.deleteSchedule(id)}")
        }
    }

    private fun load(from: LocalDate, to: LocalDate) {
        viewModelScope.launch {
            _schedules.value = server.schedules.getSchedules(from, to)
            Log.d(TAG, "schedules $from..$to: ${_schedules.value.map { it.schedule }}")
        }
    }

    private companion object {
        const val TAG = "ScheduleViewModel"
    }
}

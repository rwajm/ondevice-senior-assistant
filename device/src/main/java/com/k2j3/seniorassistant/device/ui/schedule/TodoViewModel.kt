package com.k2j3.seniorassistant.device.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.server.ScheduleApi
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TodoUiState(
    val today: LocalDate = LocalDate.now(),
    val todayList: List<Schedule> = emptyList(),
    val tomorrowList: List<Schedule> = emptyList(),
)

/** 할 일 보기: 오늘·내일 (FR-01) */
class TodoViewModel(private val schedules: ScheduleApi) : ViewModel() {
    private val state = MutableStateFlow(TodoUiState())
    val uiState: StateFlow<TodoUiState> = state.asStateFlow()

    /** 화면에 들어올 때마다 다시 불러온다 (다른 화면에서 넣거나 고쳤을 수 있으니) */
    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val all = schedules.getSchedules(today, today.plusDays(1)).map { it.schedule }
            state.value = TodoUiState(today, all.on(today), all.on(today.plusDays(1)))
        }
    }
}

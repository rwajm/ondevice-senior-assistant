package com.k2j3.seniorassistant.device.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.server.ScheduleApi
import com.k2j3.seniorassistant.device.ui.schedule.on
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarUiState(
    val today: LocalDate = LocalDate.now(),
    val month: YearMonth = YearMonth.now(),
    val selected: LocalDate = LocalDate.now(),
    val daysWithSchedule: Set<LocalDate> = emptySet(),
    val selectedList: List<Schedule> = emptyList(),
)

/** 달력: 한 달의 할 일 있는 날 + 고른 날의 할 일 (FR-01, FR-03) */
class CalendarViewModel(private val schedules: ScheduleApi) : ViewModel() {
    private val state = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = state.asStateFlow()
    private var monthSchedules: List<Schedule> = emptyList()

    /** 화면에 들어올 때: 처음이면 오늘로, 돌아온 거면 보던 달을 다시 불러온다 */
    fun refresh() = loadMonth(state.value.month)

    /** 그 날짜가 있는 달을 열고 그 날을 고른다 (말로 저장한 뒤) */
    fun show(date: LocalDate) {
        val month = YearMonth.from(date)
        state.update { it.copy(month = month, selected = date) }
        loadMonth(month)
    }

    fun select(date: LocalDate) = state.update { it.copy(selected = date, selectedList = monthSchedules.on(date)) }

    /** 지난달·다음달. 이번 달로 돌아오면 오늘, 다른 달이면 1일을 고른다 */
    fun moveMonth(delta: Long) {
        val month = state.value.month.plusMonths(delta)
        val today = LocalDate.now()
        state.update { it.copy(month = month, selected = if (month == YearMonth.from(today)) today else month.atDay(1)) }
        loadMonth(month)
    }

    private fun loadMonth(month: YearMonth) {
        viewModelScope.launch {
            monthSchedules = schedules.getSchedules(month.atDay(1), month.atEndOfMonth()).map { it.schedule }
            state.update {
                it.copy(today = LocalDate.now(), daysWithSchedule = monthSchedules.map { s -> s.date }.toSet(), selectedList = monthSchedules.on(it.selected))
            }
        }
    }
}

package com.k2j3.seniorassistant.device.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.data.schedule.ScheduleKind
import com.k2j3.seniorassistant.device.server.ScheduleApi
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlarmUiState(
    val now: LocalDateTime = LocalDateTime.now(),
    /** 오늘부터 앞으로의 알람, 시각 순서. 오늘 이미 울린 것도 (흐리게) 들어 있다 */
    val alarms: List<Schedule> = emptyList(),
    /** 지울까요? 창에 띄운 알람 */
    val deleting: Schedule? = null,
)

/** 오늘 이미 울린 알람 (흐리게 보여 준다) */
fun Schedule.rangBy(now: LocalDateTime): Boolean = LocalDateTime.of(date, time ?: LocalTime.MIN) <= now

/** 알람 확인하기 (FR-07): 알람은 할 일 보기·달력에 나오지 않고 여기에만 나온다 */
class AlarmViewModel(private val schedules: ScheduleApi) : ViewModel() {
    private val state = MutableStateFlow(AlarmUiState())
    val uiState: StateFlow<AlarmUiState> = state.asStateFlow()

    /** 화면에 들어올 때마다 다시 불러온다 (다른 화면에서 맞추거나 고쳤을 수 있으니) */
    fun refresh() {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val alarms = schedules.getSchedules(now.toLocalDate(), FAR_FUTURE)
                .map { it.schedule }
                .filter { it.kind == ScheduleKind.ALARM }
                .sortedWith(compareBy({ it.date }, { it.time }))
            state.update { it.copy(now = now, alarms = alarms) }
        }
    }

    fun askDelete(alarm: Schedule) = state.update { it.copy(deleting = alarm) }
    fun closeDialog() = state.update { it.copy(deleting = null) }

    fun delete() {
        val alarm = state.value.deleting ?: return
        state.update { it.copy(deleting = null) }
        viewModelScope.launch {
            schedules.deleteSchedule(alarm.id)
            refresh()
        }
    }

    private companion object {
        val FAR_FUTURE: LocalDate = LocalDate.of(9999, 12, 31)
    }
}

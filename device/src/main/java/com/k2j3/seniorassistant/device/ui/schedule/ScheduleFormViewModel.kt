package com.k2j3.seniorassistant.device.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.data.schedule.ScheduleWithReminders
import com.k2j3.seniorassistant.device.server.ScheduleApi
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 할 일 적기·고치기 입력 칸의 값 */
data class ScheduleForm(
    val year: Int,
    val month: Int,
    val day: Int,
    val pm: Boolean = false,
    val hour12: Int = 11,
    val minute: Int = 0,
    val title: String = "",
    val place: String = "",
    val items: String = "",
) {
    /** 고른 달에 없는 날(예: 2월 31일)은 그달 마지막 날로 맞춘다 */
    val date: LocalDate
        get() {
            val first = LocalDate.of(year, month, 1)
            return first.withDayOfMonth(minOf(day, first.lengthOfMonth()))
        }
    val time: LocalTime get() = LocalTime.of(hour12 % 12 + if (pm) 12 else 0, minute)

    /** 월·일만 고르므로, 오늘보다 앞선 날이면 내년으로 본다 (12월에 1월을 고른 경우) */
    fun withUpcomingYear(today: LocalDate = LocalDate.now()): ScheduleForm {
        val thisYear = copy(year = today.year)
        return if (thisYear.date.isBefore(today)) copy(year = today.year + 1) else thisYear
    }

    companion object {
        fun from(date: LocalDate) = ScheduleForm(date.year, date.monthValue, date.dayOfMonth)
        fun from(s: Schedule): ScheduleForm {
            val t = s.time ?: LocalTime.of(9, 0)
            val h = t.hour % 12
            return ScheduleForm(
                s.date.year, s.date.monthValue, s.date.dayOfMonth,
                pm = t.hour >= 12, hour12 = if (h == 0) 12 else h, minute = t.minute - t.minute % 5,
                title = s.title, place = s.place, items = s.items.joinItems(),
            )
        }
    }
}

enum class FormMode { WRITE, EDIT }

/** 화면 위에 뜨는 창 */
enum class FormDialog { NONE, SAVED, DELETE }

data class ScheduleFormUiState(
    val mode: FormMode,
    val form: ScheduleForm,
    val titleMissing: Boolean = false,
    val dialog: FormDialog = FormDialog.NONE,
)

/**
 * 할 일 적기(FR-03)·고치기(FR-02). 저장은 SCH-02 / SCH-03, 지우기는 SCH-04.
 * 화면마다 새로 쓰지 않고, 들어올 때 start()로 시작한다.
 */
class ScheduleFormViewModel(private val schedules: ScheduleApi) : ViewModel() {

    private val state = MutableStateFlow(ScheduleFormUiState(FormMode.WRITE, ScheduleForm.from(LocalDate.now())))
    val uiState: StateFlow<ScheduleFormUiState> = state.asStateFlow()

    private var original: ScheduleWithReminders? = null
    private var savedId: Long? = null
    private var saving = false

    /** 적기: 날짜(와 문서 읽기처럼 미리 채울 내용), 고치기: 일정 번호 */
    fun start(mode: FormMode, date: LocalDate = LocalDate.now(), editId: Long? = null, prefill: Schedule? = null) {
        original = null; savedId = null; saving = false
        state.value = ScheduleFormUiState(mode, prefill?.let { ScheduleForm.from(it) } ?: ScheduleForm.from(date))
        if (editId != null) viewModelScope.launch {
            original = schedules.getSchedule(editId)
            original?.let { o -> state.update { it.copy(form = ScheduleForm.from(o.schedule)) } }
        }
    }

    fun change(transform: (ScheduleForm) -> ScheduleForm) {
        state.update {
            var next = transform(it.form)
            if (next.month != it.form.month || next.day != it.form.day) next = next.withUpcomingYear()
            it.copy(form = next, titleMissing = false)
        }
    }

    fun save() {
        if (saving) return // 빠르게 두 번 눌러도 한 번만 저장
        val f = state.value.form
        if (f.title.isBlank()) {
            state.update { it.copy(titleMissing = true) }
            return
        }
        saving = true
        viewModelScope.launch {
            val fields = { base: Schedule -> base.copy(title = f.title.trim(), date = f.date, time = f.time, place = f.place.trim(), items = f.items.splitItems()) }
            val o = original
            val id = savedId
            when {
                o != null -> schedules.updateSchedule(fields(o.schedule), o.reminders.map { it.remindBefore })
                id != null -> { // 저장 뒤 '고치기'로 다시 저장
                    val again = schedules.getSchedule(id)
                    if (again != null) schedules.updateSchedule(fields(again.schedule), again.reminders.map { it.remindBefore })
                }
                else -> savedId = schedules.addSchedule(fields(Schedule(title = "", date = f.date)))
            }
            state.update { it.copy(dialog = FormDialog.SAVED) }
            saving = false
        }
    }

    fun askDelete() = state.update { it.copy(dialog = FormDialog.DELETE) }
    fun closeDialog() = state.update { it.copy(dialog = FormDialog.NONE) }

    fun delete(onDone: () -> Unit) {
        val id = original?.schedule?.id ?: return
        viewModelScope.launch { schedules.deleteSchedule(id); onDone() }
    }
}

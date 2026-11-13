package com.k2j3.seniorassistant.device.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.ui.theme.AppCard
import com.k2j3.seniorassistant.device.ui.theme.AppColor
import com.k2j3.seniorassistant.device.ui.theme.AppIcons
import com.k2j3.seniorassistant.device.ui.theme.AppScreen
import com.k2j3.seniorassistant.device.ui.theme.SeniorAssistantTheme
import com.k2j3.seniorassistant.device.ui.theme.AppType
import com.k2j3.seniorassistant.device.ui.theme.PillButton
import com.k2j3.seniorassistant.device.ui.common.koreanDate
import java.time.LocalDate

@Composable
fun TodoRoute(viewModel: TodoViewModel, onBack: () -> Unit, onOtherDate: () -> Unit, onEdit: (Long) -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.uiState.collectAsState()
    TodoScreen(state, onBack, onOtherDate, onEdit)
}

/** 할 일 보기: 오늘과 내일을 나란히 */
@Composable
fun TodoScreen(state: TodoUiState, onBack: () -> Unit, onOtherDate: () -> Unit, onEdit: (Long) -> Unit) {
    AppScreen(
        title = "할 일 보기",
        onBack = onBack,
        trailing = {
            PillButton(
                "다른 날짜", onOtherDate, trailing = AppIcons.Forward,
                container = AppColor.GreenSoft, border = AppColor.Green, content = AppColor.Green,
            )
        },
    ) {
        AppCard(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                DayColumn("오늘", state.today, state.todayList, "오늘은 할 일이 없어요", AppColor.Green, onEdit, Modifier.weight(1f))
                Box(Modifier.width(2.dp).fillMaxHeight().background(AppColor.Border))
                DayColumn("내일", state.today.plusDays(1), state.tomorrowList, "내일은 할 일이 없어요", AppColor.Text, onEdit, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DayColumn(
    label: String,
    date: LocalDate,
    list: List<Schedule>,
    emptyText: String,
    labelColor: Color,
    onEdit: (Long) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxHeight().padding(horizontal = 36.dp, vertical = 28.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(label, style = AppType.ScreenTitle.copy(fontSize = 44.sp), color = labelColor)
            Text(date.koreanDate(), style = AppType.Body.copy(fontWeight = FontWeight.Medium), color = AppColor.TextQuiet, modifier = Modifier.padding(bottom = 6.dp))
        }
        Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(2.dp).background(AppColor.Divider))
        if (list.isEmpty()) {
            EmptySchedule(emptyText)
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(list, key = { it.id }) { ScheduleItem(it) { onEdit(it.id) } }
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun TodoPreview() {
    val today = LocalDate.of(2026, 11, 13)
    SeniorAssistantTheme { TodoScreen(TodoUiState(today, sampleSchedules(today), emptyList()), {}, {}, {}) }
}

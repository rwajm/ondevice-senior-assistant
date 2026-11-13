package com.k2j3.seniorassistant.device.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.k2j3.seniorassistant.device.ui.schedule.EmptySchedule
import com.k2j3.seniorassistant.device.ui.schedule.ScheduleRow
import com.k2j3.seniorassistant.device.ui.schedule.on
import com.k2j3.seniorassistant.device.ui.schedule.sampleSchedules
import com.k2j3.seniorassistant.device.ui.theme.AppCard
import com.k2j3.seniorassistant.device.ui.theme.AppColor
import com.k2j3.seniorassistant.device.ui.theme.AppIcons
import com.k2j3.seniorassistant.device.ui.theme.AppScreen
import com.k2j3.seniorassistant.device.ui.theme.AppShape
import com.k2j3.seniorassistant.device.ui.theme.SeniorAssistantTheme
import com.k2j3.seniorassistant.device.ui.theme.AppType
import com.k2j3.seniorassistant.device.ui.theme.PillButton
import com.k2j3.seniorassistant.device.ui.theme.TouchHeight
import com.k2j3.seniorassistant.device.ui.common.koreanDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class CalendarActions(
    val onBack: () -> Unit = {},
    val onSelect: (LocalDate) -> Unit = {},
    val onMoveMonth: (Long) -> Unit = {},
    val onEdit: (Long) -> Unit = {},
    val onWrite: (LocalDate) -> Unit = {},
)

@Composable
fun CalendarRoute(viewModel: CalendarViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit, onWrite: (LocalDate) -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.uiState.collectAsState()
    CalendarScreen(state, CalendarActions(onBack, viewModel::select, viewModel::moveMonth, onEdit, onWrite))
}

/** 달력: 왼쪽 한 달, 오른쪽 고른 날의 할 일 */
@Composable
fun CalendarScreen(state: CalendarUiState, actions: CalendarActions) {
    AppScreen(title = "달력", onBack = actions.onBack) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            MonthCard(state, actions, Modifier.weight(1.75f).fillMaxHeight())
            DayCard(state, actions, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

private val SmallPill = RoundedCornerShape(28.dp)

@Composable
private fun MonthCard(state: CalendarUiState, actions: CalendarActions, modifier: Modifier) {
    AppCard(modifier) {
        Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
                MonthButton("지난달", AppIcons.Back, leading = true) { actions.onMoveMonth(-1) }
                Text(
                    "${state.month.year}년 ${state.month.monthValue}월",
                    style = AppType.TileTitle.copy(fontSize = 34.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                MonthButton("다음달", AppIcons.Forward, leading = false) { actions.onMoveMonth(1) }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, d ->
                    Text(
                        d,
                        style = AppType.Hint.copy(fontWeight = FontWeight.Bold),
                        color = when (i) { 0 -> AppColor.Red; 6 -> AppColor.Blue; else -> AppColor.Text },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(AppColor.Divider))
            Spacer(Modifier.height(8.dp))
            MonthGrid(state, actions.onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MonthButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, leading: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(56.dp)
            .clip(SmallPill)
            .background(AppColor.Card)
            .border(2.dp, AppColor.Border, SmallPill)
            .clickable(onClick = onClick)
            .padding(start = if (leading) 10.dp else 18.dp, end = if (leading) 18.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (leading) Icon(icon, null, Modifier.size(26.dp), tint = AppColor.TextSub)
        Text(text, style = AppType.Hint.copy(fontWeight = FontWeight.Bold), color = AppColor.TextSub)
        if (!leading) Icon(icon, null, Modifier.size(26.dp), tint = AppColor.TextSub)
    }
}

@Composable
private fun MonthGrid(state: CalendarUiState, onSelect: (LocalDate) -> Unit, modifier: Modifier) {
    val month = state.month
    val lead = month.atDay(1).dayOfWeek.value % 7 // 일요일 = 0
    val weeks = (lead + month.lengthOfMonth() + 6) / 7
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(weeks) { w ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(7) { c ->
                    val day = w * 7 + c - lead + 1
                    if (day < 1 || day > month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val date = month.atDay(day)
                        DayCell(date, state, onSelect, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, state: CalendarUiState, onSelect: (LocalDate) -> Unit, modifier: Modifier) {
    val selected = date == state.selected
    val has = date in state.daysWithSchedule
    val isToday = date == state.today
    val bg = when { selected -> AppColor.Green; has -> AppColor.GreenSoft; else -> AppColor.Background }
    val numColor = when {
        selected -> Color.White
        date.dayOfWeek == DayOfWeek.SUNDAY -> AppColor.Red
        date.dayOfWeek == DayOfWeek.SATURDAY -> AppColor.Blue
        else -> AppColor.Text
    }
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(bg).clickable { onSelect(date) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("${date.dayOfMonth}", style = AppType.Body.copy(fontSize = 28.sp), color = numColor)
        when {
            isToday -> Text("오늘", style = AppType.Hint.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold), color = if (selected) Color.White else AppColor.Green)
            has -> Box(Modifier.padding(top = 4.dp).size(8.dp).clip(CircleShape).background(if (selected) Color.White else AppColor.Green))
            else -> Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DayCard(state: CalendarUiState, actions: CalendarActions, modifier: Modifier) {
    AppCard(modifier) {
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.selected.koreanDate(), style = AppType.Body.copy(fontWeight = FontWeight.ExtraBold), color = AppColor.TextSub)
                    if (state.selected == state.today) {
                        Text(
                            "오늘",
                            style = AppType.Hint.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
                            color = AppColor.Green,
                            modifier = Modifier.padding(start = 12.dp).clip(RoundedCornerShape(999.dp)).background(AppColor.GreenSoft).padding(horizontal = 14.dp, vertical = 2.dp),
                        )
                    }
                }
                Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(2.dp).background(AppColor.Divider))
            }
            if (state.selectedList.isEmpty()) {
                EmptySchedule("이 날은 할 일이 없어요")
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(state.selectedList, key = { it.id }) { ScheduleRow(it) { actions.onEdit(it.id) } }
                }
            }
            PillButton(
                "이 날에 할 일 적기", { actions.onWrite(state.selected) },
                Modifier.fillMaxWidth(), leading = AppIcons.Plus,
                container = AppColor.GreenSoft, border = AppColor.Green, content = AppColor.Green,
            )
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun CalendarPreview() {
    val today = LocalDate.of(2026, 11, 13)
    val all = sampleSchedules(today)
    SeniorAssistantTheme {
        CalendarScreen(
            CalendarUiState(today, YearMonth.from(today), today, all.map { it.date }.toSet(), all.on(today)),
            CalendarActions(),
        )
    }
}

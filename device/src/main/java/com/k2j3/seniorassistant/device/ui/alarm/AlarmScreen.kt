package com.k2j3.seniorassistant.device.ui.alarm

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.data.schedule.ScheduleKind
import com.k2j3.seniorassistant.device.ui.common.koreanDate
import com.k2j3.seniorassistant.device.ui.common.koreanTime
import com.k2j3.seniorassistant.device.ui.schedule.EmptySchedule
import com.k2j3.seniorassistant.device.ui.theme.AppCard
import com.k2j3.seniorassistant.device.ui.theme.AppColor
import com.k2j3.seniorassistant.device.ui.theme.AppDialog
import com.k2j3.seniorassistant.device.ui.theme.AppIcons
import com.k2j3.seniorassistant.device.ui.theme.AppScreen
import com.k2j3.seniorassistant.device.ui.theme.AppType
import com.k2j3.seniorassistant.device.ui.theme.DialogButton
import com.k2j3.seniorassistant.device.ui.theme.PillButton
import com.k2j3.seniorassistant.device.ui.theme.SeniorAssistantTheme
import com.k2j3.seniorassistant.device.ui.theme.SummaryTable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun AlarmRoute(viewModel: AlarmViewModel, onBack: () -> Unit, onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.uiState.collectAsState()
    // 창이 떠 있을 때 기기의 뒤로 가기는 창만 닫는다
    BackHandler(enabled = state.deleting != null) { viewModel.closeDialog() }
    AlarmScreen(state, onBack, onAdd, onEdit, viewModel::askDelete, viewModel::delete, viewModel::closeDialog)
}

/** 알람 확인하기: 오늘부터 앞으로의 알람 (고치기 · 지우기), 오른쪽 위 알람 추가 */
@Composable
fun AlarmScreen(
    state: AlarmUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onAskDelete: (Schedule) -> Unit,
    onDelete: () -> Unit,
    onCloseDialog: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AppScreen(
            title = "알람 확인하기",
            onBack = onBack,
            trailing = {
                PillButton(
                    "알람 추가", onAdd, leading = AppIcons.Plus,
                    container = AppColor.AmberSoft, border = AppColor.Amber, content = AppColor.Amber,
                )
            },
        ) {
            AppCard(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 28.dp)) {
                    if (state.alarms.isEmpty()) {
                        EmptySchedule("맞춰 둔 알람이 없어요", AppIcons.Bell)
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(state.alarms, key = { it.id }) { alarm ->
                                AlarmItem(alarm, state.now, { onEdit(alarm.id) }, { onAskDelete(alarm) })
                            }
                        }
                    }
                }
            }
        }
        state.deleting?.let { DeleteDialog(it, state.now.toLocalDate(), onCloseDialog, onDelete) }
    }
}

private val ItemShape = RoundedCornerShape(20.dp)
private val ItemText = AppType.Body.copy(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)

/** 오늘 · 내일 · 11월 20일 (목) */
private fun dayText(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "오늘"
    today.plusDays(1) -> "내일"
    else -> date.koreanDate()
}

/** 알람 한 칸: 종 · 언제 · 무엇 / 고치기 · 지우기. 오늘 이미 울린 것은 흐리게 */
@Composable
private fun AlarmItem(alarm: Schedule, now: LocalDateTime, onEdit: () -> Unit, onDelete: () -> Unit) {
    val rang = alarm.rangBy(now)
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (rang) 0.45f else 1f)
            .clip(ItemShape)
            .background(AppColor.Background)
            .border(2.dp, AppColor.Divider, ItemShape)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(32.dp)).background(AppColor.AmberSoft), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Bell, null, Modifier.size(36.dp), tint = AppColor.Amber)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                buildAnnotatedString {
                    append(dayText(alarm.date, now.toLocalDate()) + " ")
                    withStyle(SpanStyle(color = AppColor.Amber)) { append(alarm.time?.koreanTime() ?: "") }
                    if (rang) withStyle(SpanStyle(color = AppColor.TextQuiet)) { append("  울렸어요") }
                },
                style = ItemText,
            )
            Text(alarm.title, style = ItemText.copy(fontWeight = FontWeight.Bold), color = AppColor.TextSub)
        }
        ItemButton("고치기", AppIcons.PencilSmall, AppColor.Text, AppColor.TextQuietBorder, onEdit)
        ItemButton("지우기", AppIcons.Trash, AppColor.Red, AppColor.Red, onDelete)
    }
}

@Composable
private fun ItemButton(text: String, icon: ImageVector, content: Color, border: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(AppColor.Card)
            .border(2.dp, border, RoundedCornerShape(32.dp))
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(26.dp), tint = content)
        Text(text, style = ItemText.copy(fontSize = 26.sp), color = content)
    }
}

@Composable
private fun DeleteDialog(alarm: Schedule, today: LocalDate, onNo: () -> Unit, onYes: () -> Unit) {
    AppDialog(
        buildAnnotatedString {
            append("이 알람을 ")
            withStyle(SpanStyle(color = AppColor.Red)) { append("지울까요?") }
        },
        watermark = AppIcons.TrashOpen, watermarkColor = AppColor.Red,
    ) {
        SummaryTable(
            listOf("언제" to "${dayText(alarm.date, today)} ${alarm.time?.koreanTime() ?: ""}".trim(), "무엇" to alarm.title),
            AppColor.Red,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DialogButton("아니요", onNo, Modifier.weight(1f))
            DialogButton("지울게요", onYes, Modifier.weight(1f), container = AppColor.Red, content = Color.White, bordered = false)
        }
    }
}

private val previewNow = LocalDateTime.of(2026, 11, 17, 16, 40)
private val sampleAlarms = listOf(
    Schedule(1, "약 복용", previewNow.toLocalDate(), LocalTime.of(16, 30), kind = ScheduleKind.ALARM),
    Schedule(2, "혈압약 먹기", previewNow.toLocalDate(), LocalTime.of(21, 0), kind = ScheduleKind.ALARM),
    Schedule(3, "기상", previewNow.toLocalDate().plusDays(1), LocalTime.of(6, 30), kind = ScheduleKind.ALARM),
    Schedule(4, "보청기 충전", previewNow.toLocalDate().plusDays(3), LocalTime.of(20, 55), kind = ScheduleKind.ALARM),
)

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun AlarmPreview() = SeniorAssistantTheme {
    AlarmScreen(AlarmUiState(previewNow, sampleAlarms), {}, {}, {}, {}, {}, {})
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun AlarmEmptyPreview() = SeniorAssistantTheme {
    AlarmScreen(AlarmUiState(previewNow), {}, {}, {}, {}, {}, {})
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun AlarmDeletePreview() = SeniorAssistantTheme {
    AlarmScreen(AlarmUiState(previewNow, sampleAlarms, deleting = sampleAlarms[2]), {}, {}, {}, {}, {}, {})
}

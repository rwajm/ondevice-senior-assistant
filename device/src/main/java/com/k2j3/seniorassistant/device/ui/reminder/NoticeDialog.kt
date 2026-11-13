package com.k2j3.seniorassistant.device.ui.reminder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.server.Notice
import com.k2j3.seniorassistant.device.server.NoticeKind
import com.k2j3.seniorassistant.device.ui.common.koreanDate
import com.k2j3.seniorassistant.device.ui.common.koreanTime
import com.k2j3.seniorassistant.device.ui.theme.AppColor
import com.k2j3.seniorassistant.device.ui.theme.AppDialog
import com.k2j3.seniorassistant.device.ui.theme.AppIcons
import com.k2j3.seniorassistant.device.ui.theme.DialogButton
import com.k2j3.seniorassistant.device.ui.theme.SeniorAssistantTheme
import com.k2j3.seniorassistant.device.ui.theme.SummaryTable
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 선제 알림 창 (FR-06). 어느 화면에 있든 맨 위에 뜨고, '확인했어요'를 눌러야 닫힌다.
 * 알림 하나에 할 일이 하나면 언제·무엇·어디서·챙길것, 여러 개(아침 요약)면 시간·할 일 목록.
 */
@Composable
fun NoticeDialog(notice: Notice, onListen: () -> Unit, onConfirm: () -> Unit, now: LocalDateTime = LocalDateTime.now()) {
    BackHandler { } // 확인 전에는 뒤로 가기로 닫히지 않게
    val (lead, rest) = notice.headline(now)
    AppDialog(
        buildAnnotatedString {
            withStyle(SpanStyle(color = AppColor.Green)) { append(lead) }
            append(" $rest")
        },
        watermark = AppIcons.AlarmClock, watermarkColor = AppColor.Green,
    ) {
        val one = notice.schedules.singleOrNull()
        if (one != null) {
            SummaryTable(
                listOf("언제" to one.whenText(now.toLocalDate()), "무엇" to one.title, "어디서" to one.place, "챙길것" to one.items.joinToString(", ")),
                AppColor.Green,
            )
        } else {
            SummaryTable(notice.schedules.take(4).map { (it.time?.koreanTime() ?: "하루 종일") to it.title }, AppColor.Green)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (notice.audio != null) {
                DialogButton("소리로 듣기", onListen, Modifier.weight(1f), container = AppColor.Blue, content = Color.White, bordered = false)
            }
            DialogButton("확인했어요", onConfirm, Modifier.weight(1f), container = AppColor.Green, content = Color.White, bordered = false)
        }
    }
}

/** 제목: 초록 부분 + 나머지 */
private fun Notice.headline(now: LocalDateTime): Pair<String, String> {
    if (kind == NoticeKind.DAILY_SUMMARY || schedules.size != 1) return "오늘" to "할 일이 ${schedules.size}개 있어요"
    val s = schedules.first()
    val today = now.toLocalDate()
    if (s.date == today.plusDays(1)) return "내일" to "할 일이 있어요"
    if (s.date != today) return s.date.koreanDate() to "할 일이 있어요"
    val time = s.time ?: return "오늘" to "할 일이 있어요"
    val minutes = Duration.between(now, s.date.atTime(time)).toMinutes()
    return when {
        minutes <= 5 -> "지금" to "할 일 시간이에요"
        minutes < 60 -> "${minutes}분 뒤" to "할 일이 있어요"
        else -> "${(minutes + 30) / 60}시간 뒤" to "할 일이 있어요"
    }
}

private fun Schedule.whenText(today: LocalDate): String {
    val day = when (date) { today -> "오늘"; today.plusDays(1) -> "내일"; else -> date.koreanDate() }
    return "$day ${time?.koreanTime() ?: ""}".trim()
}

// ---------- 미리보기 ----------

private val previewNow = LocalDateTime.of(2026, 11, 13, 13, 0)

@Preview(widthDp = 1280, heightDp = 720) @Composable
private fun NoticePreview() = SeniorAssistantTheme {
    Box(Modifier.fillMaxSize()) {
        NoticeDialog(
            Notice(1, NoticeKind.REMINDER, listOf(Schedule(title = "병원 진료", date = previewNow.toLocalDate(), time = LocalTime.of(14, 0), place = "○○내과", items = listOf("신분증", "먹는 약"))), "", ByteArray(0)),
            onListen = {}, onConfirm = {}, now = previewNow,
        )
    }
}

@Preview(widthDp = 1280, heightDp = 720) @Composable
private fun DailyPreview() = SeniorAssistantTheme {
    Box(Modifier.fillMaxSize()) {
        NoticeDialog(
            Notice(
                2, NoticeKind.DAILY_SUMMARY,
                listOf(
                    Schedule(title = "병원 진료", date = previewNow.toLocalDate(), time = LocalTime.of(14, 0)),
                    Schedule(title = "약 먹기", date = previewNow.toLocalDate()),
                ),
                "", null,
            ),
            onListen = {}, onConfirm = {}, now = previewNow,
        )
    }
}

package com.k2j3.seniorassistant.device.ui.voice

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.k2j3.seniorassistant.device.ui.theme.AppCard
import com.k2j3.seniorassistant.device.ui.theme.AppColor
import com.k2j3.seniorassistant.device.ui.theme.AppIcons
import com.k2j3.seniorassistant.device.ui.theme.SeniorAssistantTheme
import com.k2j3.seniorassistant.device.ui.theme.AppType
import com.k2j3.seniorassistant.device.ui.theme.PillButton
import com.k2j3.seniorassistant.device.ui.theme.keepAll
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.ui.common.koreanDate
import com.k2j3.seniorassistant.device.ui.common.koreanTime
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay

@Composable
fun VoiceRoute(viewModel: VoiceViewModel, userName: String, onStop: () -> Unit, onSaved: (LocalDate) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    // 저장했으면 안내 문장을 잠깐 보여 준 뒤 그 날의 달력으로
    LaunchedEffect(state.savedDate) {
        state.savedDate?.let { delay(2_500); onSaved(it) }
    }
    VoiceScreen(state, userName, onStop, viewModel::again)
}

/** 말로 부탁하기 대화: 왼쪽 대화, 오른쪽 그만하기 + 지금 상태 */
@Composable
fun VoiceScreen(state: VoiceUiState, userName: String, onStop: () -> Unit, onAgain: () -> Unit) {
    Row(
        Modifier.fillMaxSize().background(AppColor.Background).padding(start = 40.dp, end = 40.dp, top = 28.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        AppCard(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 36.dp),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 32.dp),
                ) {
                    items(state.messages.reversed()) { Bubble(it, userName) }
                }
                Box(
                    Modifier.fillMaxWidth().height(120.dp)
                        .background(Brush.verticalGradient(0.35f to AppColor.Card, 1f to AppColor.Card.copy(alpha = 0f))),
                )
            }
        }
        Column(Modifier.width(280.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            PillButton("그만하기", onStop, Modifier.fillMaxWidth(), leading = AppIcons.Back)
            AppCard(Modifier.weight(1f).fillMaxWidth()) {
                StatusPanel(state, onAgain)
            }
        }
    }
}

private val BubbleText = AppType.Body.copy(lineHeight = 43.sp)
private val NameText = AppType.BodySmall.copy(fontWeight = FontWeight.ExtraBold)

/** 날짜·시간(11월 15일 (일), 오전 11시 30분)을 초록으로 강조 */
private val Emphasis = Regex("""\d+월 \d+일( \(.\))?|(오전|오후) \d+시( \d+분)?""")

@Composable
private fun Bubble(m: VoiceMessage, userName: String) {
    when (m) {
        is VoiceMessage.Bot -> BotBubble {
            Text(
                buildAnnotatedString {
                    var last = 0
                    Emphasis.findAll(m.text).forEach { r ->
                        append(m.text.substring(last, r.range.first).keepAll())
                        withStyle(SpanStyle(color = AppColor.Green, fontWeight = FontWeight.ExtraBold)) { append(r.value.keepAll()) }
                        last = r.range.last + 1
                    }
                    append(m.text.substring(last).keepAll())
                },
                style = BubbleText,
                color = if (m.isError) AppColor.Red else AppColor.Text,
            )
            if (m.example != null) {
                Text("예를 들면 \"${m.example}\"".keepAll(), style = AppType.Hint.copy(fontWeight = FontWeight.Bold), color = AppColor.TextQuiet, modifier = Modifier.padding(top = 6.dp))
            }
        }
        is VoiceMessage.Thinking -> BotBubble { ThinkingDots() }
        is VoiceMessage.Draft -> BotBubble {
            Text(m.title, style = BubbleText)
            Column(
                Modifier.padding(top = 14.dp).clip(RoundedCornerShape(14.dp)).background(AppColor.Card)
                    .border(2.dp, AppColor.Border, RoundedCornerShape(14.dp)).padding(horizontal = 22.dp, vertical = 14.dp),
            ) { DraftRows(m.draft, m.original) }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("맞으면 ", style = AppType.Hint.copy(fontWeight = FontWeight.Bold), color = AppColor.TextQuiet)
                Chip("응", AppColor.GreenSoft, AppColor.Green)
                Text(", 아니면 ", style = AppType.Hint.copy(fontWeight = FontWeight.Bold), color = AppColor.TextQuiet)
                Chip("아니", AppColor.RedSoft, AppColor.Red)
            }
        }
        is VoiceMessage.Me -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${userName}님", style = NameText, color = AppColor.TextQuiet)
            Text(
                m.text.keepAll(),
                style = BubbleText,
                color = Color.White,
                modifier = Modifier.widthIn(max = 560.dp)
                    .clip(RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                    .background(AppColor.Blue)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    }
}

private val RowText = BubbleText.copy(fontSize = 28.sp)

/** 초안 표: 넣기는 언제·무엇(·어디서·챙길것), 고치기는 할 일·언제(바뀐 시간) */
@Composable
private fun DraftRows(draft: Schedule, original: Schedule?) {
    @Composable
    fun row(k: String, content: androidx.compose.ui.text.AnnotatedString) = Row {
        Text(k, style = RowText, color = AppColor.TextQuiet, modifier = Modifier.width(96.dp))
        Text(content, style = RowText.copy(fontWeight = FontWeight.ExtraBold))
    }
    fun time(t: LocalTime?) = t?.koreanTime() ?: ""
    if (original == null) {
        row("언제", buildAnnotatedString { withStyle(SpanStyle(color = AppColor.Green)) { append("${draft.date.koreanDate()} ${time(draft.time)}") } })
        row("무엇", buildAnnotatedString { append(draft.title) })
        if (draft.place.isNotBlank()) row("어디서", buildAnnotatedString { append(draft.place) })
        if (draft.items.isNotEmpty()) row("챙길것", buildAnnotatedString { append(draft.items.joinToString(", ")) })
    } else {
        row("할 일", buildAnnotatedString { append(listOf(draft.place, draft.title).filter { it.isNotBlank() }.joinToString(" ")) })
        row("언제", buildAnnotatedString {
            val sameDay = draft.date == original.date
            if (sameDay) append("${draft.date.koreanDate()} ") else {
                withStyle(SpanStyle(color = Color(0xFF9CA3AF), textDecoration = TextDecoration.LineThrough)) { append(original.date.koreanDate()) }
                append(" → ")
                withStyle(SpanStyle(color = AppColor.Green)) { append(draft.date.koreanDate()) }
                append(" ")
            }
            if (draft.time != original.time) {
                withStyle(SpanStyle(color = Color(0xFF9CA3AF), textDecoration = TextDecoration.LineThrough)) { append(time(original.time)) }
                append(" → ")
                withStyle(SpanStyle(color = AppColor.Green)) { append(time(draft.time)) }
            } else append(time(draft.time))
        })
        if (draft.title != original.title) row("무엇", buildAnnotatedString { append(draft.title) })
    }
}

@Composable
private fun BotBubble(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(0.84f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("도우미", style = NameText, color = AppColor.Blue)
        Column(
            Modifier.clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)).background(Color(0xFFEEF1F6)).padding(horizontal = 24.dp, vertical = 16.dp),
        ) { content() }
    }
}

@Composable
private fun Chip(text: String, bg: Color, fg: Color) {
    Text(
        text,
        style = AppType.Body.copy(fontWeight = FontWeight.ExtraBold),
        color = fg,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 10.dp),
    )
}

@Composable
private fun ThinkingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 200), RepeatMode.Reverse), label = "d$i")
            Box(Modifier.size(14.dp).alpha(a).clip(CircleShape).background(AppColor.TextQuiet))
        }
    }
}

@Composable
private fun StatusPanel(state: VoiceUiState, onAgain: () -> Unit) {
    val (label, color) = when (state.status) {
        VoiceStatus.LISTEN -> "듣고 있어요" to AppColor.Blue
        VoiceStatus.THINK -> "잠시만요" to AppColor.TextQuiet
        VoiceStatus.SPEAK -> "대답하고\n있어요" to AppColor.Green
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically)) {
            StatusIndicator(state.status)
            Text(label, style = AppType.TileTitle, color = color, textAlign = TextAlign.Center)
        }
        when {
            state.finished -> {
                val more = when (state.mode) { VoiceMode.ASK -> "또 물어보기"; VoiceMode.WRITE -> "또 넣기"; VoiceMode.EDIT -> "또 고치기" }
                Column(
                    Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onAgain).padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier.size(104.dp).clip(CircleShape).background(AppColor.BlueSoft).padding(8.dp).clip(CircleShape).background(AppColor.Blue),
                        contentAlignment = Alignment.Center,
                    ) { Icon(AppIcons.Mic, null, Modifier.size(48.dp), tint = Color.White) }
                    Text(more, style = AppType.BodySmall.copy(fontWeight = FontWeight.ExtraBold), color = AppColor.Blue)
                }
            }
        }
    }
}

/** 듣는 중: 막대 5개 / 생각 중: 회색 점 3개 / 대답 중: 초록 점 3개 */
@Composable
private fun StatusIndicator(status: VoiceStatus) {
    val t = rememberInfiniteTransition(label = "ind")
    Row(Modifier.height(96.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (status == VoiceStatus.LISTEN) {
            listOf(44, 96, 64, 80, 52).forEachIndexed { i, h ->
                val s by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(500, delayMillis = i * 100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b$i")
                Box(Modifier.width(14.dp).height(h.dp).graphicsLayer { scaleY = s }.clip(RoundedCornerShape(7.dp)).background(AppColor.Blue))
            }
        } else {
            val c = if (status == VoiceStatus.THINK) AppColor.TextQuiet else AppColor.Green
            repeat(3) { i ->
                val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 200), RepeatMode.Reverse), label = "p$i")
                val y by t.animateFloat(0f, if (status == VoiceStatus.SPEAK) -14f else 0f, infiniteRepeatable(tween(700, delayMillis = i * 180), RepeatMode.Reverse), label = "y$i")
                Box(Modifier.size(24.dp).graphicsLayer { translationY = y }.alpha(if (status == VoiceStatus.THINK) a else 1f).clip(CircleShape).background(c))
            }
        }
    }
}

private val sampleDraft = Schedule(1, "치과 가기", java.time.LocalDate.of(2026, 11, 15), LocalTime.of(11, 0))
private val sampleHospital = Schedule(2, "병원 진료", java.time.LocalDate.of(2026, 11, 13), LocalTime.of(14, 0), "○○내과", listOf("신분증", "먹는 약"))

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun VoiceAddDraftPreview() = SeniorAssistantTheme {
    VoiceScreen(
        VoiceUiState(VoiceMode.WRITE, listOf(
            VoiceMessage.Bot("언제, 무슨 일이 있으세요?", example = "모레 11시에 치과 가야 해"),
            VoiceMessage.Me("모레 11시에 치과 가야 해"),
            VoiceMessage.Draft("이렇게 넣을까요?", sampleDraft, null),
        ), VoiceStatus.LISTEN),
        "어르신", {}, {},
    )
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun VoiceAddDonePreview() = SeniorAssistantTheme {
    VoiceScreen(
        VoiceUiState(VoiceMode.WRITE, listOf(
            VoiceMessage.Draft("이렇게 넣을까요?", sampleDraft, null),
            VoiceMessage.Me("응, 그렇게 해 줘"),
            VoiceMessage.Bot("넣었어요. 11월 15일 (일) 오전 11시 치과 가기, 하루 전에 미리 알려 드릴게요."),
        ), VoiceStatus.SPEAK, finished = true),
        "어르신", {}, {},
    )
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun VoiceEditDraftPreview() = SeniorAssistantTheme {
    VoiceScreen(
        VoiceUiState(VoiceMode.EDIT, listOf(
            VoiceMessage.Bot("어떤 할 일을 바꾸거나 지울까요?", example = "병원 3시로 바꿔 줘"),
            VoiceMessage.Me("오늘 병원 3시로 바꿔 줘"),
            VoiceMessage.Draft("이렇게 바꿀까요?", sampleHospital.copy(time = LocalTime.of(15, 0)), sampleHospital),
        ), VoiceStatus.LISTEN),
        "어르신", {}, {},
    )
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun VoiceAskPreview() = SeniorAssistantTheme {
    VoiceScreen(
        VoiceUiState(VoiceMode.ASK, listOf(
            VoiceMessage.Bot("무엇이 궁금하세요?", example = "오늘 병원 몇 시야?"),
            VoiceMessage.Me("오늘 병원 몇 시야?"),
            VoiceMessage.Thinking,
        ), VoiceStatus.THINK),
        "어르신", {}, {},
    )
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun VoiceErrorPreview() = SeniorAssistantTheme {
    VoiceScreen(
        VoiceUiState(VoiceMode.ASK, listOf(
            VoiceMessage.Bot("무엇이 궁금하세요?", example = "오늘 병원 몇 시야?"),
            VoiceMessage.Bot("잘 못 들었어요. 다시 말씀해 주세요", isError = true),
        ), VoiceStatus.LISTEN),
        "어르신", {}, {},
    )
}

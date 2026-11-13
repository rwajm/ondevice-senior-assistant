package com.k2j3.seniorassistant.device.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.buildAnnotatedString
import com.k2j3.seniorassistant.device.ui.theme.DialogButton
import com.k2j3.seniorassistant.device.ui.theme.AppDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.k2j3.seniorassistant.device.ui.theme.AppCard
import com.k2j3.seniorassistant.device.ui.theme.AppColor
import com.k2j3.seniorassistant.device.ui.theme.AppIcons
import com.k2j3.seniorassistant.device.ui.theme.SeniorAssistantTheme
import com.k2j3.seniorassistant.device.ui.theme.AppType
import com.k2j3.seniorassistant.device.ui.theme.TouchHeight
import com.k2j3.seniorassistant.device.ui.common.amPm
import com.k2j3.seniorassistant.device.ui.common.clock12
import com.k2j3.seniorassistant.device.ui.common.koreanDate
import java.time.LocalDateTime

/** 메인 화면에서 갈 수 있는 곳 */
data class HomeActions(
    val openTodo: () -> Unit = {},
    val openWrite: () -> Unit = {},
    val openDocument: () -> Unit = {},
    val openVoice: () -> Unit = {},
    val openSettings: () -> Unit = {},
)

@Composable
fun HomeRoute(viewModel: HomeViewModel, actions: HomeActions, onExit: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.uiState.collectAsState()
    var askExit by remember { mutableStateOf(false) }
    // 메인에서 기기의 뒤로 가기: 바로 끄지 않고 한 번 묻는다
    BackHandler { askExit = !askExit }
    Box(Modifier.fillMaxSize()) {
        HomeScreen(state, actions)
        if (askExit) ExitDialog(onNo = { askExit = false }, onYes = onExit)
    }
}

@Composable
fun ExitDialog(onNo: () -> Unit, onYes: () -> Unit) {
    AppDialog(buildAnnotatedString { append("생활도우미를 끌까요?") }) {
        Text("끄면 미리 알려 드리는 소리를 들을 수 없어요.", style = AppType.Body.copy(fontWeight = FontWeight.Medium), color = AppColor.TextSub)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DialogButton("아니요", onNo, Modifier.weight(1f))
            DialogButton("끌게요", onYes, Modifier.weight(1f), container = AppColor.Red, content = Color.White, bordered = false)
        }
    }
}

@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions) {
    Column(
        Modifier
            .fillMaxSize()
            .background(AppColor.Background)
            .padding(start = 40.dp, end = 40.dp, top = 28.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(TouchHeight), contentAlignment = Alignment.Center) {
            Text(if (state.userName.isBlank()) "안녕하세요, 어르신" else "안녕하세요, ${state.userName}님", style = AppType.ScreenTitle)
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                MainTile("할 일 보기", "오늘 · 내일 · 앞으로", AppIcons.Calendar, AppColor.TileGreen, AppColor.IconGreen, actions.openTodo)
                MainTile("할 일 직접 적기", "할 일을 직접 넣어요", AppIcons.Pencil, AppColor.TileYellow, AppColor.IconYellow, actions.openWrite)
                MainTile("문서 읽기", "안내문을 대신 읽어 드려요", AppIcons.Document, AppColor.TileBlue, AppColor.IconBlue, actions.openDocument)
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    ClockTile(state.now, Modifier.weight(1f).fillMaxHeight())
                    SettingsTile(Modifier.fillMaxHeight().aspectRatio(1f), actions.openSettings)
                }
                VoiceTile(Modifier.weight(2f).fillMaxWidth(), actions.openVoice)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.MainTile(
    title: String,
    sub: String,
    icon: ImageVector,
    color: Color,
    iconColor: Color,
    onClick: () -> Unit,
) {
    AppCard(Modifier.weight(1f).fillMaxWidth(), color = color, onClick = onClick) {
        Row(
            Modifier.fillMaxSize().padding(start = 48.dp, end = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = AppType.TileTitle)
                Text(sub, style = AppType.Caption, color = AppColor.TextSub)
            }
            Icon(icon, null, Modifier.size(56.dp), tint = iconColor)
        }
    }
}

/** 날짜·시간 (누르는 기능 없음) */
@Composable
private fun ClockTile(now: LocalDateTime, modifier: Modifier) {
    AppCard(modifier) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(now.toLocalDate().koreanDate(), style = AppType.TileTitle.copy(fontWeight = FontWeight.Bold), color = AppColor.TextSub)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(now.toLocalTime().amPm(), style = AppType.TileTitle.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 10.dp))
                Text(now.toLocalTime().clock12(), style = AppType.TileTitle.copy(fontSize = 71.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp))
            }
        }
    }
}

@Composable
private fun SettingsTile(modifier: Modifier, onClick: () -> Unit) {
    AppCard(modifier, onClick = onClick) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            Icon(AppIcons.Gear, null, Modifier.size(56.dp), tint = AppColor.TextSub)
            Text("설정", style = AppType.TileTitle.copy(fontSize = 34.sp))
        }
    }
}

@Composable
private fun VoiceTile(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(32.dp))
            .background(AppColor.Blue)
            .clickable(onClick = onClick)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Mic, null, Modifier.size(52.dp), tint = AppColor.Blue)
            }
            Spacer(Modifier.height(16.dp))
            Text("말로 부탁하기", style = AppType.TileTitle.copy(fontSize = 46.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp), color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text("누르고 편하게 말씀하세요", style = AppType.Caption, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppColor.BlueDeep)
                    .padding(horizontal = 28.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(AppIcons.Chat, null, Modifier.size(34.dp), tint = Color.White)
                Text("오늘 병원 몇 시야?", style = AppType.BodySmall, color = Color.White)
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun ExitPreview() {
    SeniorAssistantTheme {
        Box(Modifier.fillMaxSize()) {
            HomeScreen(HomeUiState(userName = "어르신", now = LocalDateTime.of(2026, 11, 13, 14, 6)), HomeActions())
            ExitDialog({}, {})
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun HomePreview() {
    SeniorAssistantTheme {
        HomeScreen(HomeUiState(userName = "어르신", now = LocalDateTime.of(2026, 11, 13, 14, 6)), HomeActions())
    }
}


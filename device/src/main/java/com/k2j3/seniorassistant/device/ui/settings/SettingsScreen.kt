package com.k2j3.seniorassistant.device.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.k2j3.seniorassistant.device.data.settings.FontSize
import com.k2j3.seniorassistant.device.data.settings.Settings
import com.k2j3.seniorassistant.device.data.settings.SpeechRate
import java.time.Duration
import com.k2j3.seniorassistant.device.ui.theme.AppColor
import com.k2j3.seniorassistant.device.ui.theme.AppIcons
import com.k2j3.seniorassistant.device.ui.theme.AppScreen
import com.k2j3.seniorassistant.device.ui.theme.SeniorAssistantTheme
import com.k2j3.seniorassistant.device.ui.theme.AppType

data class SettingsActions(
    val onBack: () -> Unit = {},
    val onChange: ((Settings) -> Settings) -> Unit = {},
    val onPreview: () -> Unit = {},
)

@Composable
fun SettingsRoute(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    SettingsScreen(state, SettingsActions(onBack, { t -> viewModel.change(t) }, viewModel::preview))
}

/** 설정: 카드 4개 (캔버스 디자인) */
@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions) {
    val s = state.settings
    val change = actions.onChange
    Box(Modifier.fillMaxSize()) {
        AppScreen(title = "설정", onBack = actions.onBack) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    SettingCard("글자 크기", "모든 화면 글씨가 바뀌어요", AppIcons.TextSize, AppColor.TileGreen, AppColor.IconGreen, Color(0xFF6FBF86)) {
                        Options(FontSize.entries, s.fontSize, { t -> change { it.copy(fontSize = t) } }, AppColor.IconGreen, Color(0xFF6FBF86)) {
                            when (it) { FontSize.NORMAL -> "보통"; FontSize.LARGE -> "크게"; FontSize.EXTRA_LARGE -> "아주 크게" }
                        }
                    }
                    SettingCard(
                        "말 빠르기", "읽어 주는 소리의 빠르기", AppIcons.SpeakerIcon, AppColor.TileYellow, AppColor.IconYellow, Color(0xFFE9B058),
                        extra = {
                            Row(
                                Modifier.height(52.dp).clip(RoundedCornerShape(26.dp)).background(AppColor.Card).clickable(onClick = actions.onPreview).padding(start = 16.dp, end = 22.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(AppIcons.Play, null, Modifier.size(22.dp), tint = AppColor.IconYellow)
                                Text(if (state.previewing) "읽는 중…" else "들어보기", style = AppType.Hint.copy(fontWeight = FontWeight.ExtraBold), color = AppColor.IconYellow)
                            }
                        },
                    ) {
                        Options(SpeechRate.entries, s.speechRate, { v -> change { it.copy(speechRate = v) } }, AppColor.IconYellow, Color(0xFFE9B058)) {
                            when (it) { SpeechRate.SLOW -> "천천히"; SpeechRate.NORMAL -> "보통"; SpeechRate.FAST -> "빠르게" }
                        }
                    }
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    SettingCard("미리 알려주기", "원하는 때를 모두 눌러 주세요", AppIcons.AlarmClock, AppColor.TileBlue, AppColor.IconBlue, Color(0xFF86A8EE)) {
                        MultiOptions(RemindOptions, s.remindBefore.toSet(), { r ->
                            change { st ->
                                val next = if (r in st.remindBefore) st.remindBefore - r else st.remindBefore + r
                                st.copy(remindBefore = next.ifEmpty { st.remindBefore }.sortedDescending()) // 하나는 꼭 남긴다
                            }
                        }, AppColor.IconBlue, Color(0xFF86A8EE)) {
                            when (it) { RemindOptions[0] -> "하루 전"; RemindOptions[1] -> "1시간 전"; else -> "정각" }
                        }
                    }
                    SettingCard("성함", "첫 화면에 \"안녕하세요, ${s.userName.ifBlank { "어르신" }}${if (s.userName.isBlank()) "" else "님"}\"으로 보여요", AppIcons.Person, AppColor.Divider, AppColor.TextSub, AppColor.Border) {
                        NameField(s.userName) { name -> change { it.copy(userName = name) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.SettingCard(
    title: String, desc: String, icon: ImageVector, bg: Color, fg: Color, accent: Color,
    extra: (@Composable () -> Unit)? = null,
    control: @Composable () -> Unit,
) {
    Column(
        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp)).background(bg).padding(horizontal = 32.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = AppType.TileTitle, modifier = Modifier.weight(1f))
            Icon(icon, null, Modifier.size(52.dp).alpha(0.35f), tint = fg)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(desc, style = AppType.Caption, color = fg, modifier = Modifier.weight(1f))
            extra?.invoke()
        }
        control()
    }
}

/** 미리 알려주기 선택지: 하루 전 / 1시간 전 / 정각 */
private val RemindOptions = listOf(Duration.ofDays(1), Duration.ofHours(1), Duration.ZERO)

private val GroupShape = RoundedCornerShape(20.dp)
private val ItemShape = RoundedCornerShape(14.dp)

@Composable
private fun <T> Options(all: List<T>, selected: T, onPick: (T) -> Unit, fg: Color, accent: Color, label: (T) -> String) =
    MultiOptions(all, setOf(selected), onPick, fg, accent, label)

/** 알약 안의 선택지들 (여러 개 고를 수도 있음) */
@Composable
private fun <T> MultiOptions(all: List<T>, selected: Set<T>, onPick: (T) -> Unit, fg: Color, accent: Color, label: (T) -> String) {
    Row(
        Modifier.fillMaxWidth().height(66.dp).clip(GroupShape).background(Color.White.copy(alpha = 0.5f)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        all.forEach { o ->
            val on = o in selected
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .then(if (on) Modifier.shadow(3.dp, ItemShape).clip(ItemShape).background(AppColor.Card).border(2.dp, accent, ItemShape) else Modifier.clip(ItemShape))
                    .clickable { onPick(o) },
                contentAlignment = Alignment.Center,
            ) { Text(label(o), style = AppType.Caption.copy(fontWeight = FontWeight.ExtraBold), color = fg) }
        }
    }
}

@Composable
private fun NameField(current: String, onSave: (String) -> Unit) {
    var text by remember(current) { mutableStateOf(current) }
    val focus = LocalFocusManager.current
    val save = { if (text.isNotBlank()) onSave(text.trim()); focus.clearFocus() }
    Row(
        Modifier.fillMaxWidth().height(66.dp).clip(GroupShape).background(Color.White.copy(alpha = 0.5f)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicTextField(
            text, { text = it }, singleLine = true,
            textStyle = AppType.Caption.copy(fontWeight = FontWeight.ExtraBold),
            cursorBrush = SolidColor(AppColor.Text),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier.weight(1f).fillMaxHeight(),
            decorationBox = { inner -> Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) { inner() } },
        )
        Box(
            Modifier.width(150.dp).fillMaxHeight().shadow(3.dp, ItemShape).clip(ItemShape).background(AppColor.Card).clickable { save() },
            contentAlignment = Alignment.Center,
        ) { Text("바꾸기", style = AppType.Hint.copy(fontWeight = FontWeight.ExtraBold)) }
    }
}

@Preview(widthDp = 1280, heightDp = 720) @Composable
private fun SettingsPreview() = SeniorAssistantTheme { SettingsScreen(SettingsUiState(Settings(userName = "어르신")), SettingsActions()) }

package com.k2j3.seniorassistant.device.ui.voice

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.k2j3.seniorassistant.device.ui.theme.AppColor
import com.k2j3.seniorassistant.device.ui.theme.AppIcons
import com.k2j3.seniorassistant.device.ui.theme.AppScreen
import com.k2j3.seniorassistant.device.ui.theme.SeniorAssistantTheme
import com.k2j3.seniorassistant.device.ui.theme.AppType

private data class ChoiceCard(
    val mode: VoiceMode,
    val chip: String,
    val chipIcon: ImageVector,
    val chipBg: Color,
    val chipFg: Color,
    val question: String,
    val example: String,
)

private val cards = listOf(
    ChoiceCard(VoiceMode.WRITE, "할 일 넣기", AppIcons.PlusBold, AppColor.BlueSoft, AppColor.IconBlue, "새로\n할 일이\n있으세요?", "\"모레 치과 가야 해\""),
    ChoiceCard(VoiceMode.ASK, "물어보기", AppIcons.Question, AppColor.GreenSoft, AppColor.IconGreen, "할 일이\n언제였는지\n헷갈리세요?", "\"병원 언제였지?\""),
    ChoiceCard(VoiceMode.EDIT, "고치기 · 지우기", AppIcons.PencilBold, Color(0xFFFDF0CF), AppColor.IconYellow, "할 일이\n바뀌었거나\n취소됐어요?", "\"병원 3시로 바꿔 줘\""),
)

/** 말로 부탁하기: 무엇을 할지 카드 3개 중에 고른다 */
@Composable
fun VoiceChooseScreen(onBack: () -> Unit, onChoose: (VoiceMode) -> Unit) {
    AppScreen(title = "말로 부탁하기", onBack = onBack) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            cards.forEach { c -> Card(c, Modifier.weight(1f).fillMaxHeight()) { onChoose(c.mode) } }
        }
    }
}

@Composable
private fun Card(c: ChoiceCard, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier
            .clip(shape)
            .background(AppColor.Card)
            .border(3.dp, AppColor.Border, shape)
            .clickable(onClick = onClick)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(999.dp)).background(c.chipBg).padding(start = 12.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(c.chipIcon, null, Modifier.size(26.dp), tint = c.chipFg)
            Text(c.chip, style = AppType.Hint.copy(fontWeight = FontWeight.ExtraBold), color = c.chipFg)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Text(c.question, style = AppType.TileTitle.copy(fontSize = 44.sp, lineHeight = 57.sp, letterSpacing = (-1.2).sp))
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(AppColor.Divider))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("예를 들면", style = AppType.Hint.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = AppColor.TextQuiet)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(AppIcons.Chat, null, Modifier.size(30.dp), tint = AppColor.TextSub)
                Text(c.example, style = AppType.BodySmall, color = AppColor.TextSub)
            }
        }
    }
}

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun VoiceChoosePreview() = SeniorAssistantTheme { VoiceChooseScreen({}, {}) }

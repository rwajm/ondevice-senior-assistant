package com.k2j3.seniorassistant.device.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 화면 위에 뜨는 확인 창. 뒤는 어둡게 가린다.
 * 같은 화면 안에 그려서 화면 크기 맞춤(SeniorAssistantTheme)이 그대로 적용된다.
 */
@Composable
fun AppDialog(
    title: AnnotatedString,
    watermark: ImageVector? = null,
    watermarkColor: Color = AppColor.Green,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xAD111827))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        // 창이 뜬 직후에는 앞 화면에서 이어진 손가락이 버튼을 누르지 않게 잠깐 막는다
        var ready by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { delay(400); ready = true }
        Box(Modifier.width(720.dp).clip(RoundedCornerShape(32.dp)).background(AppColor.Card)) {
            if (watermark != null) {
                Icon(watermark, null, Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 48.dp).size(140.dp).alpha(0.14f), tint = watermarkColor)
            }
            Column(Modifier.fillMaxWidth().padding(48.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                Text(title, style = AppType.ScreenTitle.copy(fontSize = 48.sp, letterSpacing = (-1).sp))
                content()
            }
            if (!ready) Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                },
            )
        }
    }
}

/** 확인 창 안의 요약 표: 언제 / 무엇 / 어디서 / 챙길것 */
@Composable
fun SummaryTable(rows: List<Pair<String, String>>, highlightFirst: Color) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppColor.Background).padding(horizontal = 32.dp, vertical = 8.dp)) {
        rows.forEachIndexed { i, (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = AppType.BodySmall, color = AppColor.TextQuiet, modifier = Modifier.width(140.dp))
                Text(
                    value.ifBlank { "-" }.keepAll(),
                    style = AppType.Body.copy(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold),
                    color = if (i == 0) highlightFirst else AppColor.Text,
                )
            }
            if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(AppColor.Border))
        }
    }
}

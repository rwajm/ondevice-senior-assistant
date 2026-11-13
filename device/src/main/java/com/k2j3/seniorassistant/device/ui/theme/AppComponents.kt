package com.k2j3.seniorassistant.device.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.unit.dp

/** 모서리 둥글기 */
object AppShape {
    val Card = RoundedCornerShape(28.dp)
    val Pill = RoundedCornerShape(36.dp)
    val Field = RoundedCornerShape(16.dp)
}

/** 버튼·입력 칸 높이. 어르신이 누르기 쉽게 이보다 작게 만들지 않는다. */
val TouchHeight = 72.dp

/**
 * 모든 화면의 기본 틀: 배경 + 여백 + 위쪽 제목줄.
 * 시연 기준 여백(위 32, 양옆 40)을 720dp 높이에 맞춰 조금 줄였다.
 */
@Composable
fun AppScreen(
    title: String,
    onBack: (() -> Unit)?,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(AppColor.Background)
            .padding(start = 40.dp, end = 40.dp, top = 28.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(TouchHeight)) {
            if (onBack != null) {
                PillButton("다시 선택하기", onBack, Modifier.align(Alignment.CenterStart), leading = AppIcons.Back)
            }
            Text(title, style = AppType.ScreenTitle, modifier = Modifier.align(Alignment.Center))
            Row(Modifier.align(Alignment.CenterEnd), content = trailing)
        }
        content()
    }
}

/** 알약 모양 버튼 (다시 선택하기, 다른 날짜, 할 일 지우기 등) */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    container: Color = AppColor.Card,
    border: Color = AppColor.Border,
    content: Color = AppColor.Text,
) {
    Row(
        modifier
            .height(TouchHeight)
            .clip(AppShape.Pill)
            .background(container)
            .border(3.dp, border, AppShape.Pill)
            .clickable(onClick = onClick)
            .padding(PaddingValues(start = if (leading != null) 20.dp else 28.dp, end = if (trailing != null) 20.dp else 28.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (leading != null) Icon(leading, null, Modifier.size(32.dp), tint = content)
        Text(text, style = AppType.BodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = content)
        if (trailing != null) Icon(trailing, null, Modifier.size(32.dp), tint = content)
    }
}

/** 흰 바탕 둥근 카드. 누를 수 있으면 onClick을 준다. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    color: Color = AppColor.Card,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(AppShape.Card)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

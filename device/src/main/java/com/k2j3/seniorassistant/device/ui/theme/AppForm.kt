package com.k2j3.seniorassistant.device.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

private val FieldText = AppType.Body
private val FieldFill = Color(0xFFF9FAFB)
private val Placeholder = Color(0xFF6B7280)

/** 글자 입력 칸. 비어 있으면 예시를 회색으로, 꼭 적어야 하는데 비었으면 빨간색으로 보여 준다. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    last: Boolean = false, // 마지막 칸이면 키보드에 '완료', 아니면 '다음'
) {
    val focus = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = if (last) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(
            onNext = { focus.moveFocus(FocusDirection.Down) },
            onDone = { focus.clearFocus() },
        ),
        textStyle = FieldText,
        cursorBrush = SolidColor(AppColor.Text),
        modifier = modifier
            .height(TouchHeight)
            .clip(AppShape.Field)
            .background(FieldFill)
            .border(2.dp, if (isError) AppColor.Red else AppColor.Border, AppShape.Field),
        decorationBox = { inner ->
            Box(Modifier.fillMaxHeight().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = FieldText, color = if (isError) AppColor.Red else Placeholder)
                inner()
            }
        },
    )
}

/** 고르기 칸 (월, 일, 시, 분). 누르면 큰 글씨 목록이 펼쳐진다. */
@Composable
fun <T> AppSelect(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(TouchHeight)
                .clip(AppShape.Field)
                .background(AppColor.Card)
                .border(2.dp, AppColor.Border, AppShape.Field)
                .clickable { open = true }
                .padding(start = 20.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label(selected), style = FieldText, modifier = Modifier.weight(1f))
            Icon(AppIcons.ChevronDown, null, Modifier.size(30.dp), tint = AppColor.Text)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 480.dp).background(AppColor.Card)) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option), style = FieldText, color = if (option == selected) AppColor.Green else AppColor.Text) },
                    onClick = { onSelect(option); open = false },
                    modifier = Modifier.height(64.dp),
                )
            }
        }
    }
}

/** 둘 중 하나 고르기 (오전 | 오후) */
@Composable
fun AppSegment(left: String, right: String, rightSelected: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(TouchHeight)
            .clip(AppShape.Field)
            .border(2.dp, AppColor.Border, AppShape.Field),
    ) {
        listOf(false to left, true to right).forEach { (isRight, text) ->
            val on = isRight == rightSelected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (on) AppColor.Green else AppColor.Card)
                    .clickable { onChange(isRight) },
                contentAlignment = Alignment.Center,
            ) {
                Text(text, style = AppType.BodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = if (on) Color.White else AppColor.Text)
            }
            if (!isRight) Box(Modifier.size(2.dp, TouchHeight).background(AppColor.Border))
        }
    }
}

/** 두 칸 버튼 줄 (아니요 | 지울게요) 등에서 쓰는 큰 버튼 */
@Composable
fun DialogButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, container: Color = AppColor.Card, content: Color = AppColor.Text, bordered: Boolean = true, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    Row(
        modifier
            .height(74.dp)
            .clip(shape)
            .background(container)
            .then(if (bordered) Modifier.border(3.dp, AppColor.Border, shape) else Modifier)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(34.dp), tint = content)
        Text(text, style = AppType.TileTitle, color = content)
    }
}

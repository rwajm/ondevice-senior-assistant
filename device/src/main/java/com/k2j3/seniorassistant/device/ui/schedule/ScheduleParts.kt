package com.k2j3.seniorassistant.device.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.ui.theme.AppColor
import com.k2j3.seniorassistant.device.ui.theme.AppIcons
import com.k2j3.seniorassistant.device.ui.theme.AppType
import com.k2j3.seniorassistant.device.ui.common.koreanTime

private val ItemShape = RoundedCornerShape(20.dp)
private val ItemText = AppType.Body.copy(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)

/** 할 일 보기의 할 일 한 칸: 시간 · 고치기 / 무엇 / 챙길것 */
@Composable
fun ScheduleItem(schedule: Schedule, onEdit: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ItemShape)
            .background(AppColor.Background)
            .border(2.dp, AppColor.Divider, ItemShape)
            .clickable(onClick = onEdit)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(schedule.time?.koreanTime() ?: "하루 종일", style = ItemText, color = AppColor.Green, modifier = Modifier.weight(1f))
            Row(
                Modifier
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(AppColor.Card)
                    .border(2.dp, AppColor.TextQuietBorder, RoundedCornerShape(26.dp))
                    .padding(start = 16.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(AppIcons.PencilSmall, null, Modifier.size(24.dp), tint = AppColor.Text)
                Text("고치기", style = ItemText.copy(fontSize = 24.sp))
            }
        }
        Text(schedule.title, style = ItemText)
        if (schedule.items.isNotEmpty()) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = AppColor.TextSub)) { append("챙길것: ") }
                    append(schedule.items.joinItems())
                },
                style = ItemText.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

/** 달력 오른쪽의 짧은 할 일 칸: 시간 / 무엇 / > */
@Composable
fun ScheduleRow(schedule: Schedule, onEdit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppColor.Background)
            .border(2.dp, AppColor.Divider, RoundedCornerShape(18.dp))
            .clickable(onClick = onEdit)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(schedule.time?.koreanTime() ?: "하루 종일", style = ItemText.copy(fontSize = 22.sp), color = AppColor.Green)
            Text(schedule.title, style = ItemText)
        }
        Icon(AppIcons.Forward, null, Modifier.size(28.dp), tint = AppColor.TextQuietBorder)
    }
}

/** 할 일이 없는 날 */
@Composable
fun ColumnScope.EmptySchedule(text: String) {
    Column(
        Modifier.fillMaxWidth().weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(AppIcons.Calendar, null, Modifier.size(72.dp), tint = AppColor.TextQuietBorder)
        Text(text, style = AppType.TileTitle, color = AppColor.TextSub)
    }
}

package com.k2j3.seniorassistant.device.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonButton
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonScreen

@Composable
fun CalendarScreen(
    onBackClick: () -> Unit,
    onDateSelect: () -> Unit,
    onEditClick: () -> Unit,
    onWriteForDateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkeletonScreen(
        title = "달력",
        buttons = listOf(
            SkeletonButton("이전으로", onBackClick),
            SkeletonButton("날짜 선택", onDateSelect),
            SkeletonButton("일정 수정", onEditClick),
            SkeletonButton("일정 등록", onWriteForDateClick),
        ),
        modifier = modifier,
    )
}

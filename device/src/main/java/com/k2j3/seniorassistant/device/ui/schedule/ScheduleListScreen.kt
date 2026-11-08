package com.k2j3.seniorassistant.device.ui.schedule

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonButton
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonScreen

@Composable
fun ScheduleListScreen(
    showSavedNotice: Boolean,
    onBackClick: () -> Unit,
    onOtherDateClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkeletonScreen(
        title = if (showSavedNotice) "일정 목록 (저장 완료)" else "일정 목록",
        buttons = listOf(
            SkeletonButton("이전으로", onBackClick),
            SkeletonButton("달력", onOtherDateClick),
            SkeletonButton("일정 수정", onEditClick),
        ),
        modifier = modifier,
    )
}

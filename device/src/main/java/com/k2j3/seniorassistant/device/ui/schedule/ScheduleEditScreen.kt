package com.k2j3.seniorassistant.device.ui.schedule

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonButton
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonScreen

@Composable
fun ScheduleEditScreen(
    scheduleId: String,
    onBackClick: () -> Unit,
    onVoiceEditClick: () -> Unit,
    onSaveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkeletonScreen(
        title = "일정 수정 ($scheduleId)",
        buttons = listOf(
            SkeletonButton("이전으로", onBackClick),
            SkeletonButton("음성 입력", onVoiceEditClick),
            SkeletonButton("저장", onSaveClick),
            SkeletonButton("삭제", onDeleteClick),
        ),
        modifier = modifier,
    )
}

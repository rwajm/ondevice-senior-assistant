package com.k2j3.seniorassistant.device.ui.schedule

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonButton
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonScreen

@Composable
fun ScheduleCreateScreen(
    date: String,
    onBackClick: () -> Unit,
    onVoiceWriteClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkeletonScreen(
        title = "일정 등록 ($date)",
        buttons = listOf(
            SkeletonButton("이전으로", onBackClick),
            SkeletonButton("음성 입력", onVoiceWriteClick),
            SkeletonButton("저장", onSaveClick),
        ),
        modifier = modifier,
    )
}

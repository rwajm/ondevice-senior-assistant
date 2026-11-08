package com.k2j3.seniorassistant.device.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonButton
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonScreen

@Composable
fun HomeScreen(
    onScheduleListClick: () -> Unit,
    onScheduleWriteClick: () -> Unit,
    onDocumentClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onReminderPreviewClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkeletonScreen(
        title = "홈",
        buttons = listOf(
            SkeletonButton("일정 목록", onScheduleListClick),
            SkeletonButton("일정 등록", onScheduleWriteClick),
            SkeletonButton("문서 읽기", onDocumentClick),
            SkeletonButton("음성 질의", onVoiceClick),
            SkeletonButton("설정", onSettingsClick),
            SkeletonButton("(개발) 선제 알림 표시", onReminderPreviewClick),
        ),
        modifier = modifier,
    )
}

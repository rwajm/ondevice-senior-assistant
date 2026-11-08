package com.k2j3.seniorassistant.device.ui.document

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonButton
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonScreen

@Composable
fun DocumentResultScreen(
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onAddToScheduleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkeletonScreen(
        title = "문서 분석 결과",
        buttons = listOf(
            SkeletonButton("이전으로", onBackClick),
            SkeletonButton("일정 수정", onEditClick),
            SkeletonButton("일정 등록", onAddToScheduleClick),
        ),
        modifier = modifier,
    )
}

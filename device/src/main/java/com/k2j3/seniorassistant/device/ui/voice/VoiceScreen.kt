package com.k2j3.seniorassistant.device.ui.voice

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonButton
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonScreen

@Composable
fun VoiceScreen(
    onBackClick: () -> Unit,
    onStartClick: () -> Unit,
    onAnswerPlay: () -> Unit,
    onCreateDraftClick: () -> Unit,
    onEditDraftClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkeletonScreen(
        title = "음성 질의",
        buttons = listOf(
            SkeletonButton("이전으로", onBackClick),
            SkeletonButton("음성 입력 시작", onStartClick),
            SkeletonButton("(자동) 음성 재생", onAnswerPlay),
            SkeletonButton("(초안) 일정 등록", onCreateDraftClick),
            SkeletonButton("(초안) 일정 수정", onEditDraftClick),
        ),
        modifier = modifier,
    )
}

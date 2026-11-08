package com.k2j3.seniorassistant.device.ui.document

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonButton
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonScreen

@Composable
fun DocumentSourceScreen(
    onBackClick: () -> Unit,
    onPickFileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkeletonScreen(
        title = "문서 읽기",
        buttons = listOf(
            SkeletonButton("이전으로", onBackClick),
            SkeletonButton("파일 선택", onPickFileClick),
        ),
        modifier = modifier,
    )
}

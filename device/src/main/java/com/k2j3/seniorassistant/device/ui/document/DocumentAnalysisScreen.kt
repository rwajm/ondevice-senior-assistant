package com.k2j3.seniorassistant.device.ui.document

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonButton
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonScreen

@Composable
fun DocumentAnalysisScreen(
    onBackClick: () -> Unit,
    onAnalysisComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkeletonScreen(
        title = "문서 분석 중",
        buttons = listOf(
            SkeletonButton("이전으로", onBackClick),
            SkeletonButton("(자동) 분석 완료", onAnalysisComplete),
        ),
        modifier = modifier,
    )
}

package com.k2j3.seniorassistant.device.ui.reminder

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonButton
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonScreen

@Composable
fun ReminderDialog(
    onListenClick: () -> Unit,
    onConfirmClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkeletonScreen(
        title = "선제 알림",
        buttons = listOf(
            SkeletonButton("(자동) 음성 재생", onListenClick),
            SkeletonButton("알림 확인", onConfirmClick),
        ),
        modifier = modifier,
    )
}

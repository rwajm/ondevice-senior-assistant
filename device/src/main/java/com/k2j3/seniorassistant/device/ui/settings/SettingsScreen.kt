package com.k2j3.seniorassistant.device.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonButton
import com.k2j3.seniorassistant.device.ui.skeleton.SkeletonScreen

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onFontSizeClick: () -> Unit,
    onSpeechRateClick: () -> Unit,
    onSpeechPreviewClick: () -> Unit,
    onRemindBeforeClick: () -> Unit,
    onUserNameClick: () -> Unit,
    onNotificationMethodClick: () -> Unit,
    onPhonePairingClick: () -> Unit,
    onExternalConsentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkeletonScreen(
        title = "설정",
        buttons = listOf(
            SkeletonButton("이전으로", onBackClick),
            SkeletonButton("글자 크기 변경", onFontSizeClick),
            SkeletonButton("말 빠르기 변경", onSpeechRateClick),
            SkeletonButton("말 빠르기 미리 듣기", onSpeechPreviewClick),
            SkeletonButton("알림 시점 변경", onRemindBeforeClick),
            SkeletonButton("사용자 이름 변경", onUserNameClick),
            SkeletonButton("알림 방식 변경", onNotificationMethodClick),
            SkeletonButton("휴대폰 연결", onPhonePairingClick),
            SkeletonButton("외부 전송 동의 변경", onExternalConsentClick),
        ),
        modifier = modifier,
    )
}

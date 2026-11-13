package com.k2j3.seniorassistant.device.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/** 디자인 기준 화면 크기(dp). 1920×1080 보드에서는 1.5배로 보인다. */
const val DESIGN_WIDTH = 1280f
const val DESIGN_HEIGHT = 720f

/**
 * 앱 전체 테마.
 * 1) 보드 화면 밀도와 상관없이 1280×720dp 기준 디자인이 화면에 꽉 차도록 크기를 맞춘다.
 * 2) 키보드가 올라오면 화면 크기는 그대로 두고, 누른 입력 칸이 키보드 위로 보이도록 화면을 밀어 올린다.
 */
@Composable
fun SeniorAssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = AppColor.Blue,
            background = AppColor.Background,
            surface = AppColor.Card,
            onSurface = AppColor.Text,
        ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val current = LocalDensity.current
            val widthPx = with(current) { maxWidth.toPx() }
            val heightPx = with(current) { maxHeight.toPx() }
            val scale = minOf(widthPx / DESIGN_WIDTH, heightPx / DESIGN_HEIGHT)
            CompositionLocalProvider(LocalDensity provides Density(scale)) {
                // 화면 전체 크기(키보드와 상관없이 고정)
                val screenW = (widthPx / scale).dp
                val screenH = (heightPx / scale).dp
                Box(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
                    Box(Modifier.size(screenW, screenH)) { content() }
                }
            }
        }
    }
}


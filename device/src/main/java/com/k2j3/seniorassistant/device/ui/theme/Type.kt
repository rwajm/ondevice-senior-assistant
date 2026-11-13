package com.k2j3.seniorassistant.device.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 글자 크기 / 굵기. 시연 파일의 px 값을 그대로 sp로 옮겼다.
 */
object AppType {
    private fun style(size: TextUnit, weight: FontWeight, spacing: TextUnit = 0.sp) =
        TextStyle(fontFamily = AppFont, fontSize = size, fontWeight = weight, letterSpacing = spacing, color = AppColor.Text)

    val ScreenTitle = style(40.sp, FontWeight.ExtraBold, (-0.5).sp)   // 위쪽 제목줄
    val TileTitle = style(36.sp, FontWeight.ExtraBold)                // 메인 버튼 제목
    val Body = style(30.sp, FontWeight.Bold)                          // 목록, 입력 칸, 대화
    val BodySmall = style(28.sp, FontWeight.Bold)                     // 알약 버튼 글자
    val Caption = style(26.sp, FontWeight.Medium)                     // 버튼 아래 설명
    val Hint = style(24.sp, FontWeight.Medium)                        // 예시, 작은 안내

    val LineHeight = 1.3.em
}

package com.k2j3.seniorassistant.device.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.k2j3.seniorassistant.device.R

/**
 * Noto Sans KR (SIL OFL). 한글 상용 2,350자와 영문·기호만 남겨 용량을 줄였다.
 * 없는 글자는 기기 기본 글꼴로 자동 대체된다.
 */
val AppFont: FontFamily = FontFamily(
    Font(R.font.noto_sans_kr_medium, FontWeight.Medium),
    Font(R.font.noto_sans_kr_bold, FontWeight.Bold),
    Font(R.font.noto_sans_kr_extrabold, FontWeight.ExtraBold),
)

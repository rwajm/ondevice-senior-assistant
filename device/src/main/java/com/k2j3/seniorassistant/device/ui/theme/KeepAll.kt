package com.k2j3.seniorassistant.device.ui.theme

/**
 * 한글이 단어 중간에서 줄바꿈되지 않게 한다 (CSS의 word-break: keep-all).
 * 글자 사이에 '줄바꿈 금지' 보이지 않는 문자(U+2060)를 넣어 띄어쓰기에서만 줄이 바뀐다.
 */
fun String.keepAll(): String = buildString {
    this@keepAll.forEachIndexed { i, c ->
        append(c)
        val next = this@keepAll.getOrNull(i + 1)
        if (next != null && !c.isWhitespace() && !next.isWhitespace()) append('⁠')
    }
}

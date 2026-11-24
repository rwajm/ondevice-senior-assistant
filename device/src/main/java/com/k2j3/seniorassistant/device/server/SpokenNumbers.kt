package com.k2j3.seniorassistant.device.server

/**
 * TTS에 넘기기 전에 단위가 붙은 숫자를 읽는 말로 바꾼다. TTS(espeak)는 숫자를 모두 한자어로 읽어서
 * "3시"를 "삼시", "2개"를 "이개"라고 한다 (2026-11-17 보드에서 들음).
 *
 * - 고유어: 시·시간·개·명·살 ("세 시", "두 시간", "두 개"). 99를 넘으면 한자어
 * - 한자어: 분·월·일 ("삼십 분", "유월", "시월", "십오 일")
 * - 단위가 없는 숫자(전화번호 등)는 그대로 둔다
 *
 * 화면에 보이는 글은 숫자 그대로 두고, 소리로 읽을 때만 쓴다 ([Speaker]).
 */
internal fun forSpeech(text: String): String = UNIT_NUMBER.replace(text) { m ->
    val number = m.groupValues[1].toLongOrNull() ?: return@replace m.value
    when (val unit = m.groupValues[2]) {
        "월" -> month(number)
        "분", "일" -> "${sino(number)} $unit"
        else -> "${native(number) ?: sino(number)} $unit"
    }
}

// 시간 before 시; "2일정" is not "2일"
private val UNIT_NUMBER = Regex("""(\d+)\s?(시간|시|분|월|일(?!정)|개|명|살)""")

private fun month(n: Long): String = when (n) {
    6L -> "유월"
    10L -> "시월"
    else -> sino(n) + "월"
}

private val SINO_DIGITS = listOf("", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구")
private val SINO_UNITS = listOf("", "십", "백", "천")

/** 한자어 수: 3 삼, 30 삼십, 115 백십오, 2026 이천이십육, 0 영 */
internal fun sino(n: Long): String {
    if (n == 0L) return "영"
    fun below10000(x: Int): String = buildString {
        x.toString().forEachIndexed { i, c ->
            val d = c - '0'
            val place = x.toString().length - 1 - i
            if (d == 0) return@forEachIndexed
            if (!(d == 1 && place > 0)) append(SINO_DIGITS[d]) // 십, 백, 천 (일십이 아니라)
            append(SINO_UNITS[place])
        }
    }
    val man = n / 10_000
    val rest = (n % 10_000).toInt()
    return when {
        man == 0L -> below10000(rest)
        man >= 10_000 -> n.toString() // 억 넘는 수는 쓸 일이 없다
        else -> (if (man == 1L) "" else below10000(man.toInt())) + "만" + below10000(rest)
    }
}

private val NATIVE_ONES = listOf("", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉")
private val NATIVE_TENS = listOf("", "열", "스물", "서른", "마흔", "쉰", "예순", "일흔", "여든", "아흔")

/** 고유어 수 (1~99): 1 한, 12 열두, 20 스무, 21 스물한. 그 밖은 null */
internal fun native(n: Long): String? {
    if (n !in 1..99) return null
    if (n == 20L) return "스무"
    return NATIVE_TENS[(n / 10).toInt()] + NATIVE_ONES[(n % 10).toInt()]
}

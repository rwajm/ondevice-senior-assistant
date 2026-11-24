package com.k2j3.seniorassistant.device.ui.voice

/**
 * 화면의 "내가 한 말" 말풍선에 보일 STT 글자의 띄어쓰기를 정리한다. LLM에는 STT 글자를 그대로 넣는다
 * (모델이 이런 STT 글자로 학습했다). STT(SenseVoice)는 말을 잘게 끊어 "약 먹 으라고", "7 시"처럼 내놓는다.
 *
 * 확실한 것만 붙인다: 숫자+단위, 혼자서는 낱말이 될 수 없는 조사·어미, "명사 + 해/하는/할".
 * "가", "이", "도"처럼 낱말일 수도 있는 것("병원 가")은 건드리지 않는다. 잘못 알아들은 글자는 그대로다.
 */
internal fun tidyTranscript(raw: String): String {
    val text = raw.replace(WHITESPACE, " ").trim().trimEnd('.', '。').trim()
    val joined = NUMBER_UNIT.replace(text) { it.groupValues[1] + it.groupValues[2] }
    val words = mutableListOf<String>()
    for (word in joined.split(" ")) {
        val core = word.trimEnd('?', '!', ',', '.')
        val previous = words.lastOrNull()
        val attach = previous != null && previous.last().isHangul() && (
            core in BOUND || core in HA && previous.length >= 2 && previous.all { it.isHangul() } && previous.last() !in VERB_ENDINGS
        )
        if (attach) words[words.lastIndex] = previous + word else words += word
    }
    return words.joinToString(" ")
}

private val WHITESPACE = Regex("""\s+""")

private val NUMBER_UNIT = Regex("""(\d)\s+(시간|시|분|월|일|개|명|살)""")

/** 앞말에 붙어야만 하는 조사·어미 ("뒤 에", "먹 으라고", "가 는", "먹 기") */
private val BOUND = setOf("에", "에서", "에게", "한테", "은", "는", "을", "를", "의", "으로", "까지", "부터", "으라고", "기")

/** 명사 + 하다 ("등록 해 줘" → "등록해 줘") */
private val HA = setOf("해", "하는", "할")

/** 이렇게 끝나면 명사가 아니라 말끝이 끝난 것 ("간다고 해", "가라 해"): 해를 붙이지 않는다 */
private val VERB_ENDINGS = setOf('고', '다', '라', '요', '서', '게', '지', '면', '며', '니', '까', '야')

private fun Char.isHangul() = this in '가'..'힣'

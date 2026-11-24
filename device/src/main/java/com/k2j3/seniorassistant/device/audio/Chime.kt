package com.k2j3.seniorassistant.device.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 일정 선제 알림·아침 요약 앞에 한 번 울리는 "딩동" (파일 없이 만든다).
 * 딩(A5, 880 Hz) - 동(F5, 698 Hz): 배음을 조금 섞고 종처럼 서서히 작아진다. 사용자가 들어 보고 고른 소리 (2026-11-17).
 */
object Chime {
    private const val RATE = 22_050

    val wav: ByteArray by lazy { Wav.encode((tone(880.0, 0.7) + tone(698.46, 1.3)).toPcm16(), RATE) }

    private fun tone(freq: Double, seconds: Double): FloatArray = FloatArray((RATE * seconds).toInt()) { n ->
        val t = n.toDouble() / RATE
        val envelope = exp(-3.0 * t) * minOf(1.0, t * 200) // 5 ms에 커졌다가 천천히 줄어든다
        val wave = 0.6 * sin(2 * PI * freq * t) + 0.25 * sin(4 * PI * freq * t) + 0.1 * sin(6 * PI * freq * t)
        (envelope * wave * 0.8).toFloat()
    }
}

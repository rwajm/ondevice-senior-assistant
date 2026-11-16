package com.k2j3.seniorassistant.device.ai.tts

import com.k2j3.seniorassistant.device.audio.Wav
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** 진짜 TTS 대신 짧은 '삐' 소리를 낸다. 스피커 재생이 되는지 귀로 확인하는 용도 */
class FakeTtsModule : TtsModule {
    override suspend fun load() = Unit

    override suspend fun synthesize(text: String, speed: Float): ByteArray = beepWav()

    private fun beepWav(): ByteArray { // 0.25 s, 880 Hz, 16 kHz mono 16-bit
        val count = SAMPLE_RATE / 4
        val fade = SAMPLE_RATE / 100 // 앞뒤 10 ms를 부드럽게 해서 '딱' 소리가 나지 않게
        val samples = ShortArray(count) { i ->
            val envelope = min(1.0, min(i, count - 1 - i).toDouble() / fade)
            (sin(2 * PI * TONE_HZ * i / SAMPLE_RATE) * envelope * AMPLITUDE).toInt().toShort()
        }
        return Wav.encode(samples, SAMPLE_RATE)
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val TONE_HZ = 880.0
        const val AMPLITUDE = 0.2 * Short.MAX_VALUE
    }
}

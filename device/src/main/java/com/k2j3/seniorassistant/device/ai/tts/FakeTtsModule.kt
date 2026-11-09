package com.k2j3.seniorassistant.device.ai.tts

import java.nio.ByteBuffer
import java.nio.ByteOrder

class FakeTtsModule : TtsModule {
    override suspend fun load() = Unit

    override suspend fun synthesize(text: String, speed: Float): ByteArray = silentWav()

    private fun silentWav(): ByteArray { // 0.1 s of 16 kHz mono 16-bit silence
        val dataSize = SAMPLE_RATE / 10 * 2
        return ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVE".toByteArray())
            .put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(dataSize)
            .array()
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}

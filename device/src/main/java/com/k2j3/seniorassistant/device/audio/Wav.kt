package com.k2j3.seniorassistant.device.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 16-bit PCM 소리. data는 little-endian 샘플이고, 채널이 여럿이면 번갈아 들어 있다 */
class Pcm16(val sampleRate: Int, val channels: Int, val data: ByteArray) {
    val frames: Int get() = data.size / (BYTES_PER_SAMPLE * channels)
    val durationMillis: Long get() = frames * 1_000L / sampleRate
}

/**
 * 16-bit PCM WAV (RIFF). TTS 출력과 STT 입력(녹음)이 모두 이 형식이다.
 * 모델마다 앞에 LIST 같은 덧붙은 조각이 있을 수 있어서 조각을 차례로 건너뛰며 fmt와 data를 찾는다.
 */
object Wav {
    private const val HEADER_SIZE = 44
    private const val PCM_FORMAT = 1

    fun encode(pcm: Pcm16): ByteArray {
        val blockAlign = pcm.channels * BYTES_PER_SAMPLE
        return ByteBuffer.allocate(HEADER_SIZE + pcm.data.size).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + pcm.data.size).put("WAVE".toByteArray())
            .put("fmt ".toByteArray()).putInt(16).putShort(PCM_FORMAT.toShort())
            .putShort(pcm.channels.toShort()).putInt(pcm.sampleRate)
            .putInt(pcm.sampleRate * blockAlign).putShort(blockAlign.toShort())
            .putShort((BYTES_PER_SAMPLE * 8).toShort())
            .put("data".toByteArray()).putInt(pcm.data.size).put(pcm.data)
            .array()
    }

    /** 모노 샘플을 WAV로 */
    fun encode(samples: ShortArray, sampleRate: Int): ByteArray {
        val data = ByteBuffer.allocate(samples.size * BYTES_PER_SAMPLE).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { data.putShort(it) }
        return encode(Pcm16(sampleRate, 1, data.array()))
    }

    /** 16-bit PCM WAV가 아니면 null */
    fun decode(wav: ByteArray): Pcm16? {
        if (wav.size < 12 || wav.tag(0) != "RIFF" || wav.tag(8) != "WAVE") return null
        val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        var format = -1
        var channels = 0
        var sampleRate = 0
        var bits = 0
        var offset = 12
        while (offset + 8 <= wav.size) {
            val id = wav.tag(offset)
            val size = buffer.getInt(offset + 4).toLong() and 0xFFFF_FFFFL
            val start = offset + 8
            when (id) {
                "fmt " -> {
                    if (size < 16 || start + 16 > wav.size) return null
                    format = buffer.getShort(start).toInt()
                    channels = buffer.getShort(start + 2).toInt()
                    sampleRate = buffer.getInt(start + 4)
                    bits = buffer.getShort(start + 14).toInt()
                }
                "data" -> {
                    if (format != PCM_FORMAT || bits != 16 || channels !in 1..2 || sampleRate <= 0) return null
                    val end = minOf(start + size, wav.size.toLong()).toInt() // 길이를 모르는 녹음은 끝까지
                    val frameBytes = channels * BYTES_PER_SAMPLE
                    val usable = (end - start) / frameBytes * frameBytes // 잘린 마지막 샘플은 버린다
                    return Pcm16(sampleRate, channels, wav.copyOfRange(start, start + usable))
                }
            }
            offset = (start + size + (size and 1)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() // 홀수 길이는 1바이트 채움
        }
        return null
    }

    private fun ByteArray.tag(at: Int) = String(this, at, 4, Charsets.US_ASCII)
}

private const val BYTES_PER_SAMPLE = 2

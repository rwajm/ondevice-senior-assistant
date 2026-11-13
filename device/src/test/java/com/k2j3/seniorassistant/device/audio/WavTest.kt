package com.k2j3.seniorassistant.device.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WavTest {
    @Test
    fun encodedMonoDecodesBack() {
        val samples = shortArrayOf(0, 1000, -1000, Short.MAX_VALUE, Short.MIN_VALUE)
        val pcm = Wav.decode(Wav.encode(samples, 16_000))

        assertNotNull(pcm)
        assertEquals(16_000, pcm!!.sampleRate)
        assertEquals(1, pcm.channels)
        assertEquals(samples.size, pcm.frames)
        assertArrayEquals(littleEndian(samples), pcm.data)
    }

    @Test
    fun headerIs44Bytes() {
        assertEquals(44 + 2 * 3, Wav.encode(ShortArray(3), 22_050).size)
    }

    @Test
    fun durationFollowsSampleRate() {
        assertEquals(500L, Wav.decode(Wav.encode(ShortArray(11_025), 22_050))!!.durationMillis)
    }

    @Test
    fun skipsExtraChunksBeforeData() { // TTS 모델에 따라 fmt와 data 사이에 LIST가 있다
        val samples = shortArrayOf(1, 2, 3)
        val plain = Wav.encode(samples, 24_000)
        val list = ByteBuffer.allocate(8 + 5 + 1).order(ByteOrder.LITTLE_ENDIAN) // 홀수 길이 + 채움 1바이트
            .put("LIST".toByteArray()).putInt(5).put(ByteArray(6)).array()
        val withList = plain.copyOfRange(0, 36) + list + plain.copyOfRange(36, plain.size)

        val pcm = Wav.decode(withList)

        assertEquals(24_000, pcm!!.sampleRate)
        assertArrayEquals(littleEndian(samples), pcm.data)
    }

    @Test
    fun dropsTruncatedLastSample() {
        val wav = Wav.encode(shortArrayOf(1, 2), 16_000)
        assertEquals(1, Wav.decode(wav.copyOf(wav.size - 1))!!.frames)
    }

    @Test
    fun rejectsNonWav() {
        assertNull(Wav.decode(ByteArray(0)))
        assertNull(Wav.decode("not a wav file at all".toByteArray()))
    }

    @Test
    fun rejectsNon16BitPcm() {
        val wav = Wav.encode(shortArrayOf(1, 2), 16_000)
        val float32 = wav.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putShort(20, 3) }
        assertNull(Wav.decode(float32))
    }

    private fun littleEndian(samples: ShortArray): ByteArray =
        ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            .also { b -> samples.forEach { b.putShort(it) } }.array()
}

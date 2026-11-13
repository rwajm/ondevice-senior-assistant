package com.k2j3.seniorassistant.device.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SamplesTest {
    @Test
    fun floatSamplesBecome16Bit() {
        val pcm = floatArrayOf(0f, 1f, -1f, 0.5f).toPcm16()
        assertArrayEquals(shortArrayOf(0, 32767, -32767, 16383), pcm)
    }

    @Test
    fun samplesOutOfRangeAreClipped() {
        val pcm = floatArrayOf(1.7f, -3f).toPcm16()
        assertArrayEquals(shortArrayOf(32767, -32767), pcm)
    }

    @Test
    fun pcm16BecomesFloatsInRange() {
        val floats = shortArrayOf(0, 16384, Short.MIN_VALUE, 99).toFloats(count = 3)
        assertArrayEquals(floatArrayOf(0f, 0.5f, -1f), floats, 0f)
    }

    @Test
    fun wavBecomesTheSameFloatsItWasMadeFrom() {
        val samples = shortArrayOf(0, 16384, -16384, Short.MIN_VALUE, 1)
        val pcm = Wav.decode(Wav.encode(samples, 16_000))!!
        assertArrayEquals(samples.toFloats(), pcm.toMonoFloats(), 0f)
    }

    @Test
    fun stereoIsAveragedToMono() {
        // frame 1: (16384, -16384) → 0, frame 2: (16384, 16384) → 0.5
        val data = byteArrayOf(0x00, 0x40, 0x00, 0xC0.toByte(), 0x00, 0x40, 0x00, 0x40)
        assertArrayEquals(floatArrayOf(0f, 0.5f), Pcm16(16_000, 2, data).toMonoFloats(), 0f)
    }
}

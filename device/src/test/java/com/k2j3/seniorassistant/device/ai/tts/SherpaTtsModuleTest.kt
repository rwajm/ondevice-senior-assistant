package com.k2j3.seniorassistant.device.ai.tts

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SherpaTtsModuleTest {
    @Test
    fun floatSamplesBecome16Bit() {
        val pcm = SherpaTtsModule.toPcm16(floatArrayOf(0f, 1f, -1f, 0.5f))
        assertArrayEquals(shortArrayOf(0, 32767, -32767, 16383), pcm)
    }

    @Test
    fun samplesOutOfRangeAreClipped() {
        val pcm = SherpaTtsModule.toPcm16(floatArrayOf(1.7f, -3f))
        assertArrayEquals(shortArrayOf(32767, -32767), pcm)
    }
}

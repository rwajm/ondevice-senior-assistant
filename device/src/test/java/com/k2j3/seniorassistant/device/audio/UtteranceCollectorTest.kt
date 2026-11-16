package com.k2j3.seniorassistant.device.audio

import com.k2j3.seniorassistant.device.ai.vad.SpeechDetector
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UtteranceCollectorTest {
    /**
     * 소리 크기로 말을 판단한다: [LOUD] 이상이면 말. 조용한 조각이 [silentWindows]번 이어지면 말이 끝난다.
     * 실제 VAD처럼 약한 소리([SOFT])는 말로 보지 않는다.
     */
    private class LoudnessDetector(private val silentWindows: Int = 2) : SpeechDetector {
        override val windowSize = WINDOW
        override val onsetDelaySamples = 0
        override val endDelaySamples = silentWindows * WINDOW
        private var speaking = false
        private var ended = false
        private var quiet = 0
        var resets = 0

        override fun accept(window: FloatArray) {
            if (window.any { it >= LOUD }) {
                speaking = true; quiet = 0
            } else if (speaking && ++quiet >= silentWindows) {
                speaking = false; ended = true; quiet = 0
            }
        }

        override fun isSpeaking() = speaking
        override fun popEnded() = ended.also { ended = false }
        override fun reset() { speaking = false; ended = false; quiet = 0; resets++ }
    }

    private val loud = FloatArray(WINDOW) { LOUD }
    private val soft = FloatArray(WINDOW) { SOFT }
    private val silent = FloatArray(WINDOW)

    private fun collector(detector: SpeechDetector = LoudnessDetector()) =
        UtteranceCollector(
            detector, sampleRate = 1_000, waitForSpeechMillis = 1_000, maxUtteranceMillis = 2_000,
            preRollMillis = 100, tailMillis = 0,
        ).also { it.start() }

    @Test
    fun endsAfterSpeechThenSilence() {
        val c = collector()
        assertFalse(c.accept(loud))
        assertFalse(c.accept(loud))
        assertFalse(c.accept(silent))
        assertTrue(c.accept(silent))
        assertArrayEquals(loud + loud, c.speech(), 0f)
    }

    @Test
    fun keepsTheSoftStartBeforeTheVadNoticedSpeech() {
        val c = collector()
        c.accept(silent); c.accept(silent); c.accept(soft) // "여"
        c.accept(loud); c.accept(silent)
        assertTrue(c.accept(silent))
        assertArrayEquals(soft + loud, c.speech(), 0f) // 100 ms pre-roll, older silence dropped
    }

    @Test
    fun givesUpWhenNobodySpeaks() {
        val c = collector()
        repeat(9) { assertFalse(c.accept(silent)) } // 900 ms
        assertTrue(c.accept(silent)) // 1000 ms
        assertEquals(0, c.speech().size)
    }

    @Test
    fun stopsLongSpeechAtTheLimitAndKeepsIt() {
        val c = collector()
        var windows = 1
        while (!c.accept(loud)) windows++
        assertEquals(20, windows) // 2000 ms of speech
        assertEquals(20 * WINDOW, c.speech().size)
    }

    @Test
    fun startResetsThePreviousUtterance() {
        val detector = LoudnessDetector()
        val c = collector(detector)
        c.accept(loud); c.accept(silent); c.accept(silent)
        c.start()
        assertEquals(0, c.speech().size)
        assertEquals(2, detector.resets)
    }

    private companion object {
        const val WINDOW = 100 // 100 ms at 1 kHz
        const val LOUD = 0.5f
        const val SOFT = 0.1f
    }
}

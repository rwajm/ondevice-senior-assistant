package com.k2j3.seniorassistant.device.audio

import com.k2j3.seniorassistant.device.ai.vad.SpeechDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UtteranceCollectorTest {
    /** 소리 크기(0이 아닌 값)로 말을 판단하고, 조용한 조각이 [silentWindows]번 이어지면 구간을 내보낸다 */
    private class LoudnessDetector(private val silentWindows: Int = 2) : SpeechDetector {
        override val windowSize = WINDOW
        private val current = mutableListOf<Float>()
        private val done = mutableListOf<FloatArray>()
        private var quiet = 0
        var resets = 0

        override fun accept(window: FloatArray) {
            if (window.any { it != 0f }) {
                current += window.toList(); quiet = 0
            } else if (current.isNotEmpty() && ++quiet >= silentWindows) {
                flush()
            }
        }

        override fun isSpeaking() = current.isNotEmpty()
        override fun takeSegments() = done.toList().also { done.clear() }
        override fun flush() {
            if (current.isNotEmpty()) done += current.toFloatArray()
            current.clear(); quiet = 0
        }
        override fun reset() { current.clear(); done.clear(); quiet = 0; resets++ }
    }

    private val loud = FloatArray(WINDOW) { 0.5f }
    private val silent = FloatArray(WINDOW)

    private fun collector(detector: SpeechDetector = LoudnessDetector()) =
        UtteranceCollector(detector, sampleRate = 1_000, waitForSpeechMillis = 1_000, maxUtteranceMillis = 2_000)
            .also { it.start() }

    @Test
    fun endsAfterSpeechThenSilence() {
        val c = collector()
        assertFalse(c.accept(loud))
        assertFalse(c.accept(loud))
        assertFalse(c.accept(silent))
        assertTrue(c.accept(silent))
        assertEquals(2 * WINDOW, c.speech().size)
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
        var windows = 0
        while (!c.accept(loud)) windows++
        assertEquals(20, windows + 1) // 2000 ms of speech
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
    }
}

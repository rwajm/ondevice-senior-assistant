package com.k2j3.seniorassistant.device.audio

import com.k2j3.seniorassistant.device.ai.vad.SpeechDetector

/**
 * 마이크 소리를 VAD에 넣으면서 어르신의 말 한 번(발화)을 모은다.
 *
 * 끝나는 때 (셋 중 먼저):
 * - 말을 한 뒤 VAD가 말이 끝났다고 볼 만큼 조용할 때
 * - 말을 시작하지 않고 [waitForSpeechMillis]가 지났을 때 (빈 결과)
 * - 말을 시작하고 [maxUtteranceMillis]가 지났을 때 (그때까지의 말)
 *
 * 시간은 받은 샘플 수로 센다: 녹음이 밀려도 실제 소리 길이 기준으로 끊는다.
 */
class UtteranceCollector(
    private val detector: SpeechDetector,
    sampleRate: Int = 16_000,
    waitForSpeechMillis: Long = 8_000,
    maxUtteranceMillis: Long = 20_000,
) {
    private val waitLimit = sampleRate * waitForSpeechMillis / 1000
    private val maxLength = sampleRate * maxUtteranceMillis / 1000

    private val speech = mutableListOf<FloatArray>()
    private var received = 0L
    private var speechStartedAt = -1L

    fun start() {
        detector.reset()
        speech.clear()
        received = 0
        speechStartedAt = -1
    }

    /** 마이크에서 읽은 한 조각 (크기는 [SpeechDetector.windowSize]). 발화가 끝났으면 true */
    fun accept(window: FloatArray): Boolean {
        detector.accept(window)
        received += window.size
        speech += detector.takeSegments()
        val speaking = detector.isSpeaking()
        if (speechStartedAt < 0 && (speaking || speech.isNotEmpty())) speechStartedAt = received - window.size

        return when {
            speech.isNotEmpty() && !speaking -> true
            speechStartedAt < 0 -> received >= waitLimit
            received - speechStartedAt >= maxLength -> {
                detector.flush()
                speech += detector.takeSegments()
                true
            }
            else -> false
        }
    }

    /** 모은 말 (말이 없었으면 빈 배열) */
    fun speech(): FloatArray {
        val all = FloatArray(speech.sumOf { it.size })
        var at = 0
        for (segment in speech) {
            segment.copyInto(all, at)
            at += segment.size
        }
        return all
    }
}

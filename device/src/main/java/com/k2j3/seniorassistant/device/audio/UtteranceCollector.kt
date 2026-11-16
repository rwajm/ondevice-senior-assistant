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
 * 돌려주는 소리는 VAD가 자른 구간이 아니라 마이크 원본에서 자른다.
 * VAD는 "여덟"의 "여"처럼 약하게 시작하는 소리를 말로 보지 않아서, 말의 시작보다 [preRollMillis] 앞부터 넣는다.
 * 끝은 말을 멈춘 뒤 [tailMillis]까지 넣는다 (뒤의 긴 침묵은 뺀다).
 *
 * 시간은 받은 샘플 수로 센다: 녹음이 밀려도 실제 소리 길이 기준으로 끊는다.
 */
class UtteranceCollector(
    private val detector: SpeechDetector,
    sampleRate: Int = 16_000,
    waitForSpeechMillis: Long = 8_000,
    maxUtteranceMillis: Long = 20_000,
    preRollMillis: Long = 300,
    tailMillis: Long = 200,
) {
    private val waitLimit = sampleRate * waitForSpeechMillis / 1000
    private val maxLength = sampleRate * maxUtteranceMillis / 1000
    private val lookback = detector.onsetDelaySamples + sampleRate * preRollMillis / 1000
    private val tail = sampleRate * tailMillis / 1000

    private val audio = ArrayDeque<FloatArray>() // 받은 조각들. 말하기 전에는 lookback만큼만 남긴다
    private var audioStart = 0L // audio.first()의 첫 샘플 위치
    private var received = 0L
    private var speechStartedAt = -1L
    private var end = -1L

    fun start() {
        detector.reset()
        audio.clear()
        audioStart = 0
        received = 0
        speechStartedAt = -1
        end = -1
    }

    /** 마이크에서 읽은 한 조각 (크기는 [SpeechDetector.windowSize]). 발화가 끝났으면 true */
    fun accept(window: FloatArray): Boolean {
        detector.accept(window)
        audio.addLast(window.copyOf())
        received += window.size
        val ended = detector.popEnded()
        val speaking = detector.isSpeaking()
        if (speechStartedAt < 0 && (speaking || ended)) speechStartedAt = received - window.size

        if (speechStartedAt < 0) {
            dropBefore(received - lookback)
            return received >= waitLimit
        }
        return when {
            ended && !speaking -> {
                end = received - detector.endDelaySamples + tail
                true
            }
            !speaking -> { // 말로 알아챘다가 바로 아니라고 본 소리 (딸깍 등): 다시 기다린다
                speechStartedAt = -1
                received >= waitLimit
            }
            received - speechStartedAt >= maxLength -> {
                end = received
                true
            }
            else -> false
        }
    }

    /** 모은 말 (말이 없었으면 빈 배열) */
    fun speech(): FloatArray {
        if (speechStartedAt < 0 || end < 0) return FloatArray(0)
        val from = maxOf(audioStart, speechStartedAt - lookback)
        val to = minOf(end, received)
        if (to <= from) return FloatArray(0)
        val out = FloatArray((to - from).toInt())
        var at = audioStart
        for (window in audio) {
            val s = maxOf(from, at)
            val e = minOf(to, at + window.size)
            if (s < e) window.copyInto(out, (s - from).toInt(), (s - at).toInt(), (e - at).toInt())
            at += window.size
        }
        return out
    }

    private fun dropBefore(position: Long) {
        while (audio.isNotEmpty() && audioStart + audio.first().size <= position) {
            audioStart += audio.removeFirst().size
        }
    }
}

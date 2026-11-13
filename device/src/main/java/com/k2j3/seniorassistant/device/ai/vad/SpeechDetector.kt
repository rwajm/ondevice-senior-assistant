package com.k2j3.seniorassistant.device.ai.vad

/** 소리 조각(window)을 받아 말한 구간을 잘라 주는 VAD. 한 번에 한 곳에서만 쓴다 */
interface SpeechDetector {
    /** [accept]에 한 번에 넣는 샘플 수 (16 kHz mono, -1..1) */
    val windowSize: Int

    fun accept(window: FloatArray)

    /** 지금 말하는 중인지 */
    fun isSpeaking(): Boolean

    /** 끝난 말 구간들을 꺼낸다 (꺼낸 것은 지워진다) */
    fun takeSegments(): List<FloatArray>

    /** 말하는 중인 구간도 끝난 것으로 보고 내보낸다 */
    fun flush()

    /** 새 녹음 전에 처음 상태로 */
    fun reset()
}

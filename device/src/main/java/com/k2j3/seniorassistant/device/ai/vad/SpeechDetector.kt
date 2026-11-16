package com.k2j3.seniorassistant.device.ai.vad

/** 소리 조각(window)을 받아 말이 시작되고 끝나는 때를 알려 주는 VAD. 한 번에 한 곳에서만 쓴다 */
interface SpeechDetector {
    /** [accept]에 한 번에 넣는 샘플 수 (16 kHz mono, -1..1) */
    val windowSize: Int

    /** 실제로 말을 시작한 때부터 [isSpeaking]이 true가 될 때까지 늦어지는 샘플 수 */
    val onsetDelaySamples: Int

    /** 말을 멈춘 때부터 [popEnded]가 true가 될 때까지 늦어지는 샘플 수 (말 끝으로 보는 조용한 시간) */
    val endDelaySamples: Int

    fun accept(window: FloatArray)

    /** 지금 말하는 중인지 */
    fun isSpeaking(): Boolean

    /** 지난번 이후로 끝난 말 구간이 있었는지. 확인한 구간은 지워진다 */
    fun popEnded(): Boolean

    /** 새 녹음 전에 처음 상태로 */
    fun reset()
}

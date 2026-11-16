package com.k2j3.seniorassistant.device.ai.vad

import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File

/**
 * sherpa-onnx Silero VAD (CPU).
 *
 * @param minSilenceSeconds 이만큼 조용하면 말이 끝난 것으로 본다. 어르신은 말 사이에 쉬는 일이 많아서
 *   기본값(0.25 s)보다 길게 둔다 (설계서: 0.8 s)
 * @param maxSpeechSeconds 한 구간의 최대 길이. 넘으면 VAD가 구간을 나누지만, 녹음은 이어서 한다
 */
class SileroSpeechDetector(
    model: File,
    threshold: Float = 0.5f,
    minSilenceSeconds: Float = 0.8f,
    minSpeechSeconds: Float = 0.25f,
    maxSpeechSeconds: Float = 10f,
) : SpeechDetector {
    override val windowSize = WINDOW_SIZE

    // sherpa-onnx는 말을 알아차린 때보다 창 2개 + 최소 말 길이만큼 앞을 말의 시작으로 본다
    override val onsetDelaySamples = 2 * WINDOW_SIZE + (minSpeechSeconds * SAMPLE_RATE).toInt()

    override val endDelaySamples = (minSilenceSeconds * SAMPLE_RATE).toInt()

    private val vad = Vad(
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = model.absolutePath,
                threshold = threshold,
                minSilenceDuration = minSilenceSeconds,
                minSpeechDuration = minSpeechSeconds,
                windowSize = WINDOW_SIZE,
                maxSpeechDuration = maxSpeechSeconds,
            ),
            sampleRate = SAMPLE_RATE,
            numThreads = 1,
        ),
    )

    override fun accept(window: FloatArray) = vad.acceptWaveform(window)

    override fun isSpeaking() = vad.isSpeechDetected()

    override fun popEnded(): Boolean {
        var ended = false
        while (!vad.empty()) {
            vad.pop() // 소리는 UtteranceCollector가 원본에서 자른다 (VAD 구간은 약한 첫소리를 버린다)
            ended = true
        }
        return ended
    }

    override fun reset() = vad.reset()

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val WINDOW_SIZE = 512 // Silero: 32 ms at 16 kHz
    }
}

package com.k2j3.seniorassistant.device.ai.stt

import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2j3.seniorassistant.device.ai.ModelFiles
import com.k2j3.seniorassistant.device.audio.Wav
import com.k2j3.seniorassistant.device.audio.toMonoFloats
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * sherpa-onnx SenseVoice STT (CPU). 모델은 [ModelFiles.sttFiles].
 *
 * 한국어로 고정하고(자동 감지는 짧은 말을 다른 언어로 잘못 듣는 일이 있다), ITN을 켜서
 * "세 시" 같은 말을 "3시"처럼 숫자로 받는다.
 * 호출은 [com.k2j3.seniorassistant.device.ai.queue.InferenceQueue]가 한 번에 하나씩만 한다.
 */
class SherpaSttModule(
    private val files: ModelFiles,
    private val numThreads: Int = 2,
) : SttModule {
    @Volatile
    private var recognizer: OfflineRecognizer? = null

    override suspend fun load() = withContext(Dispatchers.IO) {
        if (recognizer != null) return@withContext
        val missing = files.missing(files.sttFiles)
        if (missing.isNotEmpty()) throw FileNotFoundException("STT model files missing: $missing")
        val start = SystemClock.elapsedRealtime()
        recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = files.sttModel.absolutePath,
                        language = "ko",
                        useInverseTextNormalization = true,
                    ),
                    tokens = files.sttTokens.absolutePath,
                    numThreads = numThreads,
                ),
            ),
        )
        Log.i(TAG, "loaded in ${SystemClock.elapsedRealtime() - start} ms")
    }

    override suspend fun transcribe(audio: ByteArray): String = withContext(Dispatchers.Default) {
        val engine = checkNotNull(recognizer) { "STT not loaded" }
        val pcm = Wav.decode(audio) ?: return@withContext "" // 빈 녹음 (아무 말도 없었다)
        if (pcm.durationMillis < MIN_SPEECH_MILLIS) return@withContext ""
        val start = SystemClock.elapsedRealtime()
        val stream = engine.createStream()
        try {
            stream.acceptWaveform(pcm.toMonoFloats(), pcm.sampleRate)
            engine.decode(stream)
            engine.getResult(stream).text.trim()
        } finally {
            stream.release()
        }.also {
            Log.i(TAG, "${SystemClock.elapsedRealtime() - start} ms for ${pcm.durationMillis} ms of speech: \"$it\"")
        }
    }

    private companion object {
        const val TAG = "SherpaStt"
        const val MIN_SPEECH_MILLIS = 200L // 기침·딸깍 소리
    }
}

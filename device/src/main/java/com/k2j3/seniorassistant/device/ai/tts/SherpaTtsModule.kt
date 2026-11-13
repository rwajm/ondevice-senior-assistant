package com.k2j3.seniorassistant.device.ai.tts

import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2j3.seniorassistant.device.ai.ModelFiles
import com.k2j3.seniorassistant.device.audio.Wav
import com.k2j3.seniorassistant.device.audio.toPcm16
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * sherpa-onnx VITS 한국어 TTS (CPU). 모델은 [ModelFiles.ttsFiles].
 *
 * espeak-ng로 발음을 만들기 때문에 sherpa-onnx 1.x가 필요하다 (2.0부터 espeak-ng가 빠졌다).
 * 호출은 [com.k2j3.seniorassistant.device.ai.queue.InferenceQueue]가 한 번에 하나씩만 한다.
 */
class SherpaTtsModule(
    private val files: ModelFiles,
    private val numThreads: Int = 2,
) : TtsModule {
    @Volatile
    private var tts: OfflineTts? = null

    override suspend fun load() = withContext(Dispatchers.IO) {
        if (tts != null) return@withContext
        val missing = files.missing(files.ttsFiles)
        if (missing.isNotEmpty()) throw FileNotFoundException("TTS model files missing: $missing")
        val start = SystemClock.elapsedRealtime()
        tts = OfflineTts(
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = files.ttsModel.absolutePath,
                        tokens = files.ttsTokens.absolutePath,
                        dataDir = files.ttsDataDir.absolutePath,
                    ),
                    numThreads = numThreads,
                ),
            ),
        )
        Log.i(TAG, "loaded in ${SystemClock.elapsedRealtime() - start} ms")
    }

    override suspend fun synthesize(text: String, speed: Float): ByteArray =
        withContext(Dispatchers.Default) {
            val engine = checkNotNull(tts) { "TTS not loaded" }
            val start = SystemClock.elapsedRealtime()
            val audio = engine.generate(text.trim(), sid = 0, speed = speed)
            check(audio.samples.isNotEmpty()) { "TTS produced no audio for: $text" }
            val audioMillis = audio.samples.size * 1000L / audio.sampleRate
            Log.i(TAG, "${SystemClock.elapsedRealtime() - start} ms for $audioMillis ms of speech")
            Wav.encode(audio.samples.toPcm16(), audio.sampleRate)
        }

    private companion object {
        const val TAG = "SherpaTts"
    }
}

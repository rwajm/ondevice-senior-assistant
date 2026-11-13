package com.k2j3.seniorassistant.device

import android.app.Application
import android.util.Log
import com.k2j3.seniorassistant.device.ai.ModelFiles
import com.k2j3.seniorassistant.device.ai.document.DocumentAnalyzer
import com.k2j3.seniorassistant.device.ai.document.FakeExternalAiClient
import com.k2j3.seniorassistant.device.ai.llm.FakeLlmModule
import com.k2j3.seniorassistant.device.ai.queue.InferenceQueue
import com.k2j3.seniorassistant.device.ai.stt.FakeSttModule
import com.k2j3.seniorassistant.device.ai.tts.FakeTtsModule
import com.k2j3.seniorassistant.device.ai.tts.SherpaTtsModule
import com.k2j3.seniorassistant.device.ai.tts.TtsModule
import com.k2j3.seniorassistant.device.audio.AudioPlayer
import com.k2j3.seniorassistant.device.server.BoardApiServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SeniorAssistantApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val server: BoardApiServer by lazy { // STT, LLM and the OpenAI client are still fakes
        BoardApiServer.create(
            this,
            InferenceQueue(FakeSttModule(), FakeLlmModule(), tts()),
            DocumentAnalyzer(FakeExternalAiClient()),
        )
    }

    val player: AudioPlayer by lazy { AudioPlayer(this) } // one speaker for the whole app

    val models: ModelFiles by lazy { ModelFiles.onDevice(this) } // pushed with adb, see ModelFiles

    override fun onCreate() {
        super.onCreate()
        scope.launch { server.start(scope) }
        scope.launch { logModelFiles() }
    }

    // 모델을 안 올린 기기(에뮬레이터 등)에서는 '삐' 소리로 대신해 나머지 기능을 계속 쓸 수 있게 한다
    private fun tts(): TtsModule =
        if (models.missing(models.ttsFiles).isEmpty()) SherpaTtsModule(models) else FakeTtsModule()

    private fun logModelFiles() {
        val missing = models.missing()
        if (missing.isEmpty()) Log.i(TAG, "model files ready in ${models.root}")
        else Log.w(TAG, "model files missing in ${models.root}: $missing")
    }

    private companion object {
        const val TAG = "SeniorAssistantApp"
    }
}

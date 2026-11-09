package com.k2j3.seniorassistant.device

import android.app.Application
import com.k2j3.seniorassistant.device.ai.document.DocumentAnalyzer
import com.k2j3.seniorassistant.device.ai.document.FakeExternalAiClient
import com.k2j3.seniorassistant.device.ai.llm.FakeLlmModule
import com.k2j3.seniorassistant.device.ai.queue.InferenceQueue
import com.k2j3.seniorassistant.device.ai.stt.FakeSttModule
import com.k2j3.seniorassistant.device.ai.tts.FakeTtsModule
import com.k2j3.seniorassistant.device.server.BoardApiServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SeniorAssistantApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val server: BoardApiServer by lazy { // fakes until the model bundle and the OpenAI client
        BoardApiServer.create(
            this,
            InferenceQueue(FakeSttModule(), FakeLlmModule(), FakeTtsModule()),
            DocumentAnalyzer(FakeExternalAiClient()),
        )
    }

    override fun onCreate() {
        super.onCreate()
        scope.launch { server.start(scope) }
    }
}

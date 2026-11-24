package com.k2j3.seniorassistant.device

import android.app.Application
import android.util.Log
import com.k2j3.seniorassistant.device.ai.ModelFiles
import com.k2j3.seniorassistant.device.ai.document.DocumentAnalyzer
import com.k2j3.seniorassistant.device.ai.document.OpenAiClient
import com.k2j3.seniorassistant.device.ai.llm.FakeLlmModule
import com.k2j3.seniorassistant.device.ai.llm.GenieDialog
import com.k2j3.seniorassistant.device.ai.llm.GenieLlmModule
import com.k2j3.seniorassistant.device.ai.llm.LlmModule
import com.k2j3.seniorassistant.device.ai.queue.InferenceQueue
import com.k2j3.seniorassistant.device.ai.stt.FakeSttModule
import com.k2j3.seniorassistant.device.ai.stt.SherpaSttModule
import com.k2j3.seniorassistant.device.ai.stt.SttModule
import com.k2j3.seniorassistant.device.ai.tts.FakeTtsModule
import com.k2j3.seniorassistant.device.ai.tts.SherpaTtsModule
import com.k2j3.seniorassistant.device.ai.tts.TtsModule
import com.k2j3.seniorassistant.device.ai.vad.SileroSpeechDetector
import com.k2j3.seniorassistant.device.audio.AudioPlayer
import com.k2j3.seniorassistant.device.audio.MicVoiceRecorder
import com.k2j3.seniorassistant.device.audio.SilentVoiceRecorder
import com.k2j3.seniorassistant.device.audio.VoiceRecorder
import com.k2j3.seniorassistant.device.server.BoardApiServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SeniorAssistantApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val server: BoardApiServer by lazy {
        BoardApiServer.create(
            this,
            InferenceQueue(stt(), llm(), tts()),
            // 키가 비어 있으면 OpenAiClient가 실패를 돌려주고 문서 화면은 기존의 안내 문구를 보여 준다.
            DocumentAnalyzer(OpenAiClient(BuildConfig.OPENAI_API_KEY)),
        )
    }

    val player: AudioPlayer by lazy { AudioPlayer(this) } // one speaker for the whole app

    val recorder: VoiceRecorder by lazy { // one microphone for the whole app
        if (models.missing(models.vadFiles).isEmpty()) MicVoiceRecorder(this) { SileroSpeechDetector(models.vadModel) }
        else SilentVoiceRecorder()
    }

    val models: ModelFiles by lazy { ModelFiles.onDevice(this) } // pushed with adb, see ModelFiles

    override fun onCreate() {
        super.onCreate()
        scope.launch { server.start(scope) }
        scope.launch { logModelFiles() }
    }

    // 모델을 안 올린 기기(에뮬레이터 등)에서는 가짜 모듈(정해진 문장, '삐' 소리)로 나머지 기능을 계속 쓸 수 있게 한다
    private fun stt(): SttModule =
        if (models.missing(models.sttFiles).isEmpty()) SherpaSttModule(models) else FakeSttModule()

    private fun tts(): TtsModule =
        if (models.missing(models.ttsFiles).isEmpty()) SherpaTtsModule(models) else FakeTtsModule()

    // Qualcomm 라이브러리(jniLibs)와 LLM 번들(models/llm)이 둘 다 있어야 NPU의 실제 모델을 쓴다
    private fun llm(): LlmModule {
        val missing = models.missing(models.llmFiles)
        val unavailable = GenieDialog.unavailableReason
        if (missing.isEmpty() && unavailable == null) return GenieLlmModule(models, applicationInfo.nativeLibraryDir)
        Log.w(TAG, "fake LLM: " + (unavailable?.let { "Genie not available ($it)" } ?: "missing $missing"))
        return FakeLlmModule()
    }

    private fun logModelFiles() {
        val missing = models.missing()
        if (missing.isEmpty()) Log.i(TAG, "model files ready in ${models.root}")
        else Log.w(TAG, "model files missing in ${models.root}: $missing")
    }

    private companion object {
        const val TAG = "SeniorAssistantApp"
    }
}

package com.k2j3.seniorassistant.device.ai.llm

import android.os.SystemClock
import android.util.Log
import com.k2j3.seniorassistant.device.ai.ModelFiles
import java.io.FileNotFoundException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * 일정 조회·설정 기능을 고르는 LLM (Llama 3.2 1B, Qualcomm Genie, NPU). 모델은 [ModelFiles.llmFiles].
 *
 * 프롬프트는 모델 번들의 runtime_prompt_template.txt를 그대로 쓰고 요청 시각과 발화만 채운다.
 * 그래서 다시 학습한 모델로 바꿀 때는 llm 폴더의 파일만 바꾸면 된다.
 *
 * 불러오지 못해도 [load]는 실패하지 않는다. 그러면 STT·TTS(알림 음성)까지 같이 멈추기 때문이다.
 * 대신 [generate]가 실패해서 음성 대화만 "도와드리기 어려워요"로 답한다.
 *
 * 모델은 프로세스에 하나만 연다. 불러오는 몇 초 동안 보드 메모리를 3.2 GB까지 쓰고 그 뒤에는 1.7 GB를
 * 쓴다(use-mmap false; 이 보드에서는 mmap으로 열면 Genie가 실패한다). 두 벌이거나 STT·TTS를 올린 뒤에 불러오면
 * lowmemorykiller가 앱을 죽인다. 그래서 InferenceQueue는 이것부터 불러오고, 이 클래스를 여러 개 만들어도
 * 같은 모델을 함께 쓴다.
 */
class GenieLlmModule(
    private val files: ModelFiles,
    private val nativeLibraryDir: String,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : LlmModule {
    override suspend fun load() = withContext(genieThread) {
        if (opened?.config == files.llmConfig.absolutePath) return@withContext
        try {
            val missing = files.missing(files.llmFiles)
            if (missing.isNotEmpty()) throw FileNotFoundException("LLM model files missing: $missing")
            val start = SystemClock.elapsedRealtime()
            val template = files.llmPromptTemplate.readText()
            opened?.dialog?.close() // a different model folder: never two in memory
            opened = null
            opened = Opened(files.llmConfig.absolutePath, GenieDialog.open(files.llmConfig, nativeLibraryDir), template)
            loadError = null
            Log.i(TAG, "loaded in ${SystemClock.elapsedRealtime() - start} ms")
        } catch (e: Exception) {
            loadError = e
            Log.e(TAG, "not loaded; voice requests will fail until the app restarts", e)
        }
    }

    override suspend fun generate(text: String, requestTime: LocalDateTime): String = withContext(genieThread) {
        val model = opened ?: throw IllegalStateException("LLM not loaded", loadError)
        val prompt = fillPrompt(model.template, requestTime, zone, text)
        val start = SystemClock.elapsedRealtime()
        val output = model.dialog.query(prompt).trim()
        Log.i(TAG, "${SystemClock.elapsedRealtime() - start} ms: \"$text\" -> $output")
        output
    }

    private class Opened(val config: String, val dialog: GenieDialog, val template: String)

    private companion object {
        const val TAG = "GenieLlm"

        // Genie is only ever called from this one thread, as in every board run that was checked.
        // opened and loadError are touched only on it.
        val genieThread = Executors.newSingleThreadExecutor { Thread(it, "genie") }.asCoroutineDispatcher()
        var opened: Opened? = null
        var loadError: Throwable? = null
    }
}

/**
 * 학습 때와 같은 입력: 템플릿의 {{request_timestamp}}에 ISO-8601 요청 시각(초까지, 시간대 포함),
 * {{utterance}}에 STT가 들은 말을 넣는다. BOS 토큰은 Genie가 붙인다 (inference_contract.json).
 */
internal fun fillPrompt(template: String, requestTime: LocalDateTime, zone: ZoneId, utterance: String): String {
    val timestamp = requestTime.withNano(0).atZone(zone).format(TIMESTAMP)
    val spoken = utterance.replace(Regex("""\s+"""), " ").trim() // one line, as in the training data
    return template.replace("{{request_timestamp}}", timestamp).replace("{{utterance}}", spoken)
}

private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

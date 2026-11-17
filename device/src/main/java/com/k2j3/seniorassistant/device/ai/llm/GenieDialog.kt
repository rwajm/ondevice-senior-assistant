package com.k2j3.seniorassistant.device.ai.llm

import android.system.Os
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 한 번 열어 두고 계속 묻는 Genie 대화 (Qualcomm QAIRT, NPU).
 *
 * 묻기마다 KV 캐시를 비우고 시작하므로 앞의 질문이 다음 답에 섞이지 않는다.
 * 한 번에 하나만 물어야 한다 (InferenceQueue가 줄을 세운다). 한 번 묻는 데 보드에서 8초쯤 걸린다.
 */
class GenieDialog private constructor(private var handle: Long) : AutoCloseable {
    fun query(prompt: String): String {
        check(handle != 0L) { "the dialog is closed" }
        return GenieNative.nativeQuery(handle, prompt.encodeToByteArray()).decodeToString()
    }

    override fun close() {
        if (handle == 0L) return
        GenieNative.nativeFree(handle)
        handle = 0
    }

    companion object {
        /** Qualcomm 라이브러리가 들어 있지 않은 빌드면 그 이유 (에뮬레이터, 라이브러리를 복사하지 않은 PC) */
        val unavailableReason: String? get() = GenieNative.unavailableReason

        /**
         * [config]: 번들의 genie_config_npu.json. 그 안의 상대 경로는 config가 있는 폴더 기준이다.
         * [nativeLibraryDir]: 앱의 lib 폴더. NPU(DSP) 쪽 libQnnHtpV68Skel.so를 여기서 찾게 한다.
         * 보드에서 10초쯤 걸린다.
         */
        fun open(config: File, nativeLibraryDir: String): GenieDialog {
            unavailableReason?.let { throw IllegalStateException("Genie is not available: $it") }
            // the DSP loader reads this, not the app's linker; set before the first FastRPC session
            Os.setenv("ADSP_LIBRARY_PATH", "$nativeLibraryDir;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp;/dsp", true)
            val json = withoutPolling(withAbsolutePaths(config.readText(), config.absoluteFile.parentFile!!))
            return GenieDialog(GenieNative.nativeCreate(json.encodeToByteArray()))
        }
    }
}

/** genie_config의 상대 경로(tokenizer path, ctx-bins, extensions)를 [dir] 기준 절대 경로로. 앱의 작업 폴더는 "/"다 */
internal fun withAbsolutePaths(configJson: String, dir: File): String {
    fun fix(element: JsonElement, key: String?): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (k, v) -> fix(v, k) })
        is JsonArray -> JsonArray(element.map { fix(it, key) })
        is JsonPrimitive ->
            if (element.isString && key in PATH_KEYS && !File(element.content).isAbsolute) {
                JsonPrimitive(File(dir, element.content).path)
            } else {
                element
            }
    }
    return fix(Json.parseToJsonElement(configJson), null).toString()
}

private val PATH_KEYS = setOf("path", "ctx-bins", "extensions")

/**
 * 번들 설정의 "poll": true를 false로. true면 Genie 스레드 3개가 NPU를 기다리며 쉬지 않고 돌아서, 아무 요청이
 * 없을 때도 앱이 CPU 코어 3개(약 290%)를 쓰고 보드가 74°C(요청 중 94°C)까지 오른다. false면 대기 중 CPU 4%,
 * 55°C쯤이고 답 하나가 0.2초쯤(7.24초 → 7.44초) 늦어질 뿐이다 (2026-11-17 보드 측정).
 * 앱이 덮어쓰므로 학습팀이 새 번들을 줄 때마다 설정을 고치지 않아도 된다.
 */
internal fun withoutPolling(configJson: String): String {
    fun fix(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (k, v) -> if (k == "poll") JsonPrimitive(false) else fix(v) })
        is JsonArray -> JsonArray(element.map(::fix))
        is JsonPrimitive -> element
    }
    return fix(Json.parseToJsonElement(configJson)).toString()
}

/** genie_jni.c */
internal object GenieNative {
    val unavailableReason: String? by lazy {
        try {
            System.loadLibrary("genie_jni")
            nativeOpen()
        } catch (e: UnsatisfiedLinkError) {
            e.message ?: "libgenie_jni.so is missing"
        }
    }

    @JvmStatic external fun nativeOpen(): String?

    @JvmStatic external fun nativeCreate(configJson: ByteArray): Long

    @JvmStatic external fun nativeQuery(handle: Long, prompt: ByteArray): ByteArray

    @JvmStatic external fun nativeFree(handle: Long)
}

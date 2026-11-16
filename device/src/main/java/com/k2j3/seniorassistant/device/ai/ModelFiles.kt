package com.k2j3.seniorassistant.device.ai

import android.content.Context
import java.io.File

/**
 * 보드에 adb로 올려 두는 온디바이스 모델 파일 (배포 및 통합 가이드 6.3, 모델은 APK·git에 넣지 않는다).
 *
 * 위치: 앱 내부 저장소 /data/data/com.k2j3.seniorassistant.device/files/models/
 * Android 13에서는 adb가 외부 저장소의 앱 폴더(Android/data) 안에 하위 폴더를 만들 수 없어서 내부 저장소를 쓴다.
 * 올리는 방법 (debug 빌드, run-as):
 * ```
 * tar -cf - -C <모델 폴더> . | adb exec-in run-as com.k2j3.seniorassistant.device \
 *     sh -c 'mkdir -p files/models && tar -xf - -C files/models'
 * ```
 * ```
 * models/
 *   vad/silero_vad.onnx                          Silero VAD
 *   stt/model.int8.onnx, tokens.txt              SenseVoice (sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17)
 *                                                2025-09-09 판은 한국어를 광둥어로 알아들어서 쓰지 않는다
 *   tts/ko_KO-kss_low.onnx, tokens.txt,          VITS 한국어 (vits-mimic3-ko_KO-kss_low)
 *       espeak-ng-data/
 *   llm/genie_config_npu.json,                   양자화한 LLM 번들 (QAIRT 2.40, QCS6490 NPU)에서 그대로 복사
 *       runtime_prompt_template.txt,             config가 가리키는 파일(tokenizer.json, *.bin,
 *       tokenizer.json, llama32_hf_v68.bin,      htp_backend_ext_config.json)도 같은 폴더에 둔다
 *       htp_backend_ext_config.json
 * ```
 * 모델을 바꿀 때는 폴더 안의 파일만 같은 이름으로 바꿔 올린다. LLM은 config와 템플릿을 함께 바꾼다.
 */
class ModelFiles(val root: File) {
    val vadModel = File(root, "vad/silero_vad.onnx")

    val sttModel = File(root, "stt/model.int8.onnx")
    val sttTokens = File(root, "stt/tokens.txt")

    val ttsModel = File(root, "tts/ko_KO-kss_low.onnx")
    val ttsTokens = File(root, "tts/tokens.txt")
    val ttsDataDir = File(root, "tts/espeak-ng-data") // 한국어 발음 변환에 필요한 사전

    val llmConfig = File(root, "llm/genie_config_npu.json")
    val llmPromptTemplate = File(root, "llm/runtime_prompt_template.txt")
    val llmTokenizer = File(root, "llm/tokenizer.json")
    val llmModel = File(root, "llm/llama32_hf_v68.bin") // 다른 이름의 모델이면 config의 ctx-bins를 따른다
    val llmBackendExtensions = File(root, "llm/htp_backend_ext_config.json")

    val vadFiles get() = listOf(vadModel)
    val sttFiles get() = listOf(sttModel, sttTokens)
    val ttsFiles get() = listOf(ttsModel, ttsTokens, ttsDataDir)
    val llmFiles get() = listOf(llmConfig, llmPromptTemplate, llmTokenizer, llmModel, llmBackendExtensions)

    /** 아직 올리지 않았거나, 비었거나, 앱이 읽을 수 없는 파일. root 기준 상대 경로 */
    fun missing(files: List<File> = vadFiles + sttFiles + ttsFiles + llmFiles): List<String> =
        files.filterNot { it.isPresent() }.map { it.relativeTo(root).path }

    // root로 올리는 등 소유자가 앱이 아니면 못 읽을 수 있어서 canRead까지 본다
    private fun File.isPresent() =
        if (isDirectory) canRead() && list()?.isNotEmpty() == true else isFile && length() > 0 && canRead()

    companion object {
        fun onDevice(context: Context) = ModelFiles(File(context.filesDir, "models"))
    }
}

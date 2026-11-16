package com.k2j3.seniorassistant.device.ai.llm

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real model on the NPU, inside the app. Runs only on the QCS6490 board with the Qualcomm libraries in
 * jniLibs and the LLM bundle in files/models/llm; skipped elsewhere (emulator, no model).
 */
@RunWith(AndroidJUnit4::class)
class GenieDialogTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.filesDir, "models/llm")

    @Test
    fun answersLikeTheBoardRunsDid() {
        assumeTrue("Genie: ${GenieDialog.unavailableReason}", GenieDialog.unavailableReason == null)
        assumeTrue("no model in $dir", File(dir, "genie_config_npu.json").exists())
        val template = File(dir, "runtime_prompt_template.txt").readText()
        fun prompt(utterance: String) = template
            .replace("{{request_timestamp}}", "2026-11-16T18:52:00+09:00")
            .replace("{{utterance}}", utterance)

        var started = System.nanoTime()
        GenieDialog.open(File(dir, "genie_config_npu.json"), context.applicationInfo.nativeLibraryDir).use { dialog ->
            Log.i(TAG, "loaded in ${(System.nanoTime() - started) / 1_000_000} ms")
            val expected = mapOf( // what genie-t2t-run and genie_batch answered on the board
                "내일 오후 7시에 헬스 가야돼. 등록해줘" to
                    """<maum_1>{"date_expression":"내일","time_expression":"오후 7시","title":"헬스","type":1}""",
                "다음 일정이 뭐야?" to
                    """<maum_0>{"date_expression":"","time_expression":"","place":"","type":3}""",
            )
            repeat(2) { // the second round proves each query starts from an empty cache
                expected.forEach { (utterance, answer) ->
                    started = System.nanoTime()
                    val output = dialog.query(prompt(utterance))
                    Log.i(TAG, "${(System.nanoTime() - started) / 1_000_000} ms: $output")
                    assertEquals(answer, output.trim())
                }
            }
        }
    }

    private companion object {
        const val TAG = "GenieDialogTest"
    }
}

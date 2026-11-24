package com.k2j3.seniorassistant.device.server

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2j3.seniorassistant.device.SeniorAssistantApp
import com.k2j3.seniorassistant.device.ai.llm.GenieDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 보드 성능 측정: 앱의 실제 음성 흐름(STT → LLM → 대답 TTS)을 여러 번 돌리며 단계별 시간과 메모리를 로그로 남긴다.
 * 입력 음성은 앱의 TTS로 만든다. 등록 요청은 초안만 만들고 저장하지 않는다.
 * 보드에서만 의미가 있고, 직접 실행할 때만 쓴다:
 * adb shell am instrument -w -e class com.k2j3.seniorassistant.device.server.VoicePerfTest -e runs 30 \
 *   com.k2j3.seniorassistant.device.test/androidx.test.runner.AndroidJUnitRunner
 * 결과: adb logcat -s VoicePerf
 */
@RunWith(AndroidJUnit4::class)
class VoicePerfTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as SeniorAssistantApp
    private val runs = InstrumentationRegistry.getArguments().getString("runs")?.toInt() ?: 30

    @Test
    fun repeatedVoiceRequests(): Unit = runBlocking {
        assumeTrue("Genie: ${GenieDialog.unavailableReason}", GenieDialog.unavailableReason == null)
        val voice = app.server.voice

        val start = SystemClock.elapsedRealtime() // the app started loading its models when the process started
        var probe = voice.speak("준비")
        while (probe == null && SystemClock.elapsedRealtime() - start < READY_TIMEOUT_MILLIS) {
            delay(200)
            probe = voice.speak("준비")
        }
        assertNotNull("models not ready", probe)
        Log.i(TAG, "ready ${SystemClock.elapsedRealtime() - start} ms after the test started, pss ${pssMb()} MB")

        val inputs = UTTERANCES.map { it to checkNotNull(voice.speak(it)) } // the user's voice, made once
        Log.i(TAG, "inputs ready, pss ${pssMb()} MB")

        repeat(runs) { i ->
            val (said, audio) = inputs[i % inputs.size]
            val t0 = SystemClock.elapsedRealtime()
            val answer = voice.ask(audio)
            val total = SystemClock.elapsedRealtime() - t0
            val transcript = when (answer) {
                is VoiceAnswer.Answered -> answer.transcript
                is VoiceAnswer.Draft -> answer.transcript
                is VoiceAnswer.AskHalfDay -> answer.transcript
                is VoiceAnswer.AskAgain -> answer.transcript
                VoiceAnswer.NotReady -> ""
            }
            Log.i(
                TAG,
                "run ${i + 1}/$runs total $total ms, ${answer::class.simpleName}, audio ${answer.audio?.size ?: 0} B, " +
                    "pss ${pssMb()} MB | said \"$said\" heard \"$transcript\"",
            )
        }
        Log.i(TAG, "done, pss ${pssMb()} MB")
    }

    private fun pssMb() = Debug.getPss() / 1024

    private companion object {
        const val TAG = "VoicePerf"
        const val READY_TIMEOUT_MILLIS = 180_000L
        val UTTERANCES = listOf(
            "내일 오후 일곱 시에 헬스 가야 돼. 등록해 줘",
            "다음 일정이 뭐야?",
            "모레 오전 열 시에 병원 가는 거 적어 줘",
            "오늘 저녁에 뭐 있어?",
            "삼십 분 뒤에 약 먹으라고 알람 맞춰 줘",
            "이번 주 토요일 낮 열두 시에 가족이랑 점심 먹기로 했어. 달력에 써 줘",
        )
    }
}

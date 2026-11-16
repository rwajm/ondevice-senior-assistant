package com.k2j3.seniorassistant.device.server

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2j3.seniorassistant.device.ai.ModelFiles
import com.k2j3.seniorassistant.device.ai.document.DocumentAnalyzer
import com.k2j3.seniorassistant.device.ai.document.FakeExternalAiClient
import com.k2j3.seniorassistant.device.ai.llm.GenieDialog
import com.k2j3.seniorassistant.device.ai.llm.GenieLlmModule
import com.k2j3.seniorassistant.device.ai.queue.ModelState
import com.k2j3.seniorassistant.device.ai.stt.SttModule
import com.k2j3.seniorassistant.device.ai.tts.FakeTtsModule
import com.k2j3.seniorassistant.device.data.AppDatabase
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.ai.queue.InferenceQueue
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the user said (as text, the STT is scripted) → the real LLM on the NPU → the voice API's answer.
 * Runs only on the QCS6490 board with the Qualcomm libraries and models/llm; skipped elsewhere.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceVoiceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val models = ModelFiles.onDevice(context)
    private lateinit var db: AppDatabase

    private val now = LocalDateTime.of(2026, 11, 16, 18, 52)
    private val tomorrow = now.toLocalDate().plusDays(1)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun spokenRequestsBecomeDraftsAnswersAndQuestions() = runBlocking {
        assumeTrue("Genie: ${GenieDialog.unavailableReason}", GenieDialog.unavailableReason == null)
        assumeTrue("missing ${models.missing(models.llmFiles)}", models.missing(models.llmFiles).isEmpty())
        val heard = ScriptedStt()
        val queue = InferenceQueue(heard, GenieLlmModule(models, context.applicationInfo.nativeLibraryDir), FakeTtsModule())
        val server = BoardApiServer(context, db, queue, DocumentAnalyzer(FakeExternalAiClient()) { now }, { now }) {}
        val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            server.start(serverScope)
            assertEquals(ModelState.READY, queue.state.value)

            heard.text = "내일 오후 7시에 헬스 가야돼. 등록해줘"
            val health = server.voice.ask(ByteArray(0))
            Log.i(TAG, "${heard.text} -> Draft(${health::class.simpleName})")
            assertEquals(Schedule(title = "헬스", date = tomorrow, time = LocalTime.of(19, 0)), (health as VoiceAnswer.Draft).draft)

            server.schedules.addSchedule(health.draft, emptyList())
            heard.text = "다음 일정이 뭐야?"
            val next = server.voice.ask(ByteArray(0))
            Log.i(TAG, "${heard.text} -> Answered(${next::class.simpleName})")
            assertEquals(listOf("헬스"), (next as VoiceAnswer.Answered).schedules.map { it.title })

            heard.text = "모레 세 시에 병원 가는 거 적어 줘"
            val hospital = server.voice.ask(ByteArray(0))
            Log.i(TAG, "${heard.text} -> AskHalfDay(${hospital::class.simpleName})")
            assertEquals(LocalTime.of(15, 0), (hospital as VoiceAnswer.AskHalfDay).afternoon.time)
            assertEquals(tomorrow.plusDays(1), hospital.afternoon.date)
        } finally {
            serverScope.cancel()
        }
    }

    private class ScriptedStt : SttModule {
        var text = ""

        override suspend fun load() = Unit

        override suspend fun transcribe(audio: ByteArray) = text
    }

    private companion object {
        const val TAG = "OnDeviceVoiceTest"
    }
}

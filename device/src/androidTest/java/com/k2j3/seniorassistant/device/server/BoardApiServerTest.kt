package com.k2j3.seniorassistant.device.server

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2j3.seniorassistant.device.ai.document.DocumentAnalyzer
import com.k2j3.seniorassistant.device.ai.document.ExternalAiClient
import com.k2j3.seniorassistant.device.ai.document.ExternalAiException
import com.k2j3.seniorassistant.device.ai.document.ExternalAiRequest
import com.k2j3.seniorassistant.device.ai.llm.LlmModule
import com.k2j3.seniorassistant.device.ai.queue.InferenceQueue
import com.k2j3.seniorassistant.device.ai.stt.FakeSttModule
import com.k2j3.seniorassistant.device.ai.tts.FakeTtsModule
import com.k2j3.seniorassistant.device.data.AppDatabase
import com.k2j3.seniorassistant.device.data.schedule.Reminder
import com.k2j3.seniorassistant.device.data.schedule.ReminderWithSchedule
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.data.schedule.ScheduleKind
import com.k2j3.seniorassistant.device.data.settings.Settings
import com.k2j3.seniorassistant.device.data.settings.SpeechRate
import com.k2j3.seniorassistant.device.scheduler.ReminderEvents
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoardApiServerTest {
    private lateinit var db: AppDatabase
    private lateinit var server: BoardApiServer
    private val llm = ScriptedLlm()
    private val external = ScriptedExternalAi()
    private val logs = mutableListOf<String>()

    private val now = LocalDateTime.now().withSecond(0).withNano(0)
    private val tomorrow = now.toLocalDate().plusDays(1)

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val queue = InferenceQueue(FakeSttModule("내일 병원 몇 시야?"), llm, FakeTtsModule())
        server = BoardApiServer(context, db, queue, DocumentAnalyzer(external) { now }, { now }) {
            logs += it
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun alarmsRemindOnTimeAndOthersUseTheSetting() = runTest {
        val alarm = Schedule(title = "약 먹기", date = tomorrow, kind = ScheduleKind.ALARM)
        val alarmId = server.schedules.addSchedule(alarm, listOf(Duration.ofDays(1)))
        val checkupId = server.schedules.addSchedule(Schedule(title = "건강검진", date = tomorrow))

        assertEquals(listOf(Duration.ZERO), remindTimes(alarmId))
        val defaults = server.settings.getSettings().remindBefore.toSet()
        assertEquals(defaults, remindTimes(checkupId).toSet())
        assertTrue(server.schedules.deleteSchedule(checkupId))
        assertNull(server.schedules.getSchedule(checkupId))
    }

    @Test
    fun settingsKeepAtLeastOneRemindTimeAndPreviewSpeech() = runTest {
        val before = server.settings.getSettings()
        assertFalse(server.settings.updateSettings(before.copy(remindBefore = emptyList())))
        assertEquals(before, server.settings.getSettings())

        assertNull(server.settings.previewSpeech(SpeechRate.SLOW)) // models not loaded yet
        server.start(backgroundScope)
        assertNotNull(server.settings.previewSpeech(SpeechRate.SLOW))
    }

    @Test
    fun voiceIsNotReadyUntilStarted() = runTest {
        assertEquals(VoiceAnswer.NotReady, server.voice.ask(ByteArray(0)))
    }

    @Test
    fun datelessTimeQueryLooksFromNowOn() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixed = LocalDateTime.of(2030, 5, 10, 15, 0)
        val queue = InferenceQueue(FakeSttModule(), llm, FakeTtsModule())
        val analyzer = DocumentAnalyzer(external) { fixed }
        val at3pm = BoardApiServer(context, db, queue, analyzer, { fixed }) {}
        at3pm.start(backgroundScope)
        val today = fixed.toLocalDate()
        listOf(
            Schedule(title = "오늘 아침", date = today, time = LocalTime.of(9, 0)),
            Schedule(title = "오늘 저녁", date = today, time = LocalTime.of(17, 0)),
            Schedule(title = "내일 저녁", date = today.plusDays(1), time = LocalTime.of(17, 0)),
            Schedule(title = "모레 아침", date = today.plusDays(2), time = LocalTime.of(9, 0)),
        ).forEach { at3pm.schedules.addSchedule(it, emptyList()) }

        llm.output = query(type = 1, time = "17:00")
        assertEquals(listOf("오늘 저녁", "내일 저녁"), answeredTitles(at3pm))
        llm.output = query(type = 1, time = "09:00")
        assertEquals(listOf("모레 아침"), answeredTitles(at3pm)) // 9 o'clock today has passed
    }

    @Test
    fun voiceQueryAnswersWithTheNextSchedule() = runTest {
        server.start(backgroundScope)
        val checkup = Schedule(
            title = "진료",
            date = tomorrow,
            time = LocalTime.of(14, 0),
            place = "○○내과",
            items = listOf("신분증"),
        )
        server.schedules.addSchedule(checkup)
        llm.output = query(type = 3)

        val answer = server.voice.ask(ByteArray(0)) as VoiceAnswer.Answered
        Log.d(TAG, "answer: ${answer.text}")
        assertEquals("내일 병원 몇 시야?", answer.transcript)
        assertEquals(listOf("진료"), answer.schedules.map { it.title })
        assertEquals("내일 오후 2시, ○○내과 진료 일정이 있어요. 챙길 것은 신분증.", answer.text)
        assertNotNull(answer.audio)
    }

    @Test
    fun voiceSetBecomesADraftWithoutSaving() = runTest {
        server.start(backgroundScope)
        val time = now.toLocalTime().minusMinutes(1)
        llm.output = set(time = time.toString(), title = "약 먹기", type = 2)

        val draft = (server.voice.ask(ByteArray(0)) as VoiceAnswer.Draft).draft
        Log.d(TAG, "draft: $draft")
        assertEquals(ScheduleKind.ALARM, draft.kind)
        assertEquals(tomorrow, draft.date) // the time has passed today
        assertTrue(server.schedules.getSchedules(now.toLocalDate(), tomorrow).isEmpty())
    }

    @Test
    fun voiceEditChangesOnlyTheSpokenFields() = runTest {
        server.start(backgroundScope)
        val existing = Schedule(id = 7, title = "진료", date = tomorrow, time = LocalTime.of(9, 0))
        llm.output = set(time = "11:00", title = "", type = 1)

        val draft = (server.voice.ask(ByteArray(0), existing) as VoiceAnswer.Draft).draft
        assertEquals(existing.copy(time = LocalTime.of(11, 0)), draft)
    }

    @Test
    fun unclearOrInvalidOutputAsksAgain() = runTest {
        server.start(backgroundScope)
        llm.output = query(type = -1)
        assertEquals(Sentences.ASK_AGAIN, askAgainText())
        llm.output = "내일 2시에 병원이 있어요"
        assertEquals(Sentences.UNSUPPORTED, askAgainText())
    }

    @Test
    fun documentsNeedConsentAndAreReadAloud() = runTest {
        server.start(backgroundScope)
        assertEquals(DocumentReading.ConsentRequired, server.documents.read(png))

        changeSettings { it.copy(externalTransferConsent = true) }
        val reading = server.documents.read(png) as DocumentReading.Extracted
        Log.d(TAG, "document: ${reading.text}")
        assertEquals("건강검진", reading.fields.title)
        assertTrue(reading.text.startsWith("이런 내용이 있어요."))

        external.fail = true
        val failed = server.documents.read(png) as DocumentReading.Failed
        assertEquals(Sentences.DOCUMENT_UNAVAILABLE, failed.text)
        assertEquals(listOf("document unavailable: SERVICE_FAILED"), logs)
    }

    @Test
    fun remindersStayOpenUntilDismissedAndSpeakUnlessTurnedOff() = runTest {
        server.start(backgroundScope)
        val checkup = Schedule(id = 1, title = "건강검진", date = tomorrow, time = LocalTime.of(9, 0))
        val reminder = Reminder(scheduleId = 1, remindBefore = Duration.ofDays(1))
        val due = ReminderWithSchedule(reminder, checkup)

        ReminderEvents.emitDue(due) // before anyone looks at the notices
        val notice = server.notices.notices.first { it.isNotEmpty() }.single()
        Log.d(TAG, "notice: ${notice.text}")
        assertEquals(NoticeKind.REMINDER, notice.kind)
        assertEquals("내일 오전 9시, 건강검진 일정이 있어요.", notice.text)
        assertNotNull(notice.audio)
        server.notices.dismiss(notice.id)
        assertTrue(server.notices.notices.value.isEmpty())

        changeSettings { it.copy(voiceNotificationEnabled = false) }
        ReminderEvents.emitDue(due)
        assertNull(server.notices.notices.first { it.isNotEmpty() }.single().audio)
    }

    private suspend fun askAgainText(): String =
        (server.voice.ask(ByteArray(0)) as VoiceAnswer.AskAgain).text

    private suspend fun changeSettings(change: (Settings) -> Settings) {
        server.settings.updateSettings(change(server.settings.getSettings()))
    }

    private suspend fun remindTimes(id: Long): List<Duration> =
        server.schedules.getSchedule(id)!!.reminders.map { it.remindBefore }

    private fun query(type: Int, time: String = "") =
        """<maum_0>{"date": "", "time": "$time", "place": "", "type": $type}<maum_end>"""

    private suspend fun answeredTitles(server: BoardApiServer): List<String> =
        (server.voice.ask(ByteArray(0)) as VoiceAnswer.Answered).schedules.map { it.title }

    private fun set(time: String, title: String, type: Int) = """<maum_1>{"date": "",
        "time": "$time", "place": "", "title": "$title", "type": $type}<maum_end>"""

    private class ScriptedLlm : LlmModule {
        var output = ""

        override suspend fun load() = Unit

        override suspend fun generate(text: String, requestTime: LocalDateTime) = output
    }

    private class ScriptedExternalAi : ExternalAiClient {
        var fail = false

        override suspend fun extract(request: ExternalAiRequest): String {
            if (fail) throw ExternalAiException("500")
            return """{"document_detected": true, "title": "건강검진", "date": "", "time": "",
                "place": "", "items": [], "uncertain_fields": []}"""
        }
    }

    private companion object {
        const val TAG = "BoardApiServerTest"
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    }
}

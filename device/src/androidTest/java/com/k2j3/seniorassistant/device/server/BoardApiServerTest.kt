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
import com.k2j3.seniorassistant.device.ai.stt.SttModule
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

// model outputs are in the board v3 model's format (Genie leaves out <maum_end>)
@RunWith(AndroidJUnit4::class)
class BoardApiServerTest {
    private lateinit var db: AppDatabase
    private lateinit var server: BoardApiServer
    private val stt = ScriptedStt()
    private val llm = ScriptedLlm()
    private val external = ScriptedExternalAi()
    private val logs = mutableListOf<String>()

    private val now = LocalDateTime.now().withSecond(0).withNano(0)
    private val tomorrow = now.toLocalDate().plusDays(1)

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val queue = InferenceQueue(stt, llm, FakeTtsModule())
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
        val queue = InferenceQueue(stt, llm, FakeTtsModule())
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

        llm.output = query(type = 1, time = "오후 다섯 시")
        assertEquals(listOf("오늘 저녁", "내일 저녁"), answeredTitles(at3pm))
        llm.output = query(type = 1, time = "오전 아홉 시")
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
    fun voiceQueryUsesTheSpokenDateAndTime() = runTest {
        server.start(backgroundScope)
        addAll(
            Schedule(title = "치과", date = tomorrow, time = LocalTime.of(14, 0)),
            Schedule(title = "산책", date = tomorrow, time = LocalTime.of(10, 0)),
            Schedule(title = "모레 치과", date = tomorrow.plusDays(1), time = LocalTime.of(14, 0)),
        )
        llm.output = query(type = 1, date = "내일", time = "오후 두 시")
        assertEquals(listOf("치과"), answeredTitles(server))
        llm.output = query(type = 1, date = "내일")
        assertEquals(listOf("산책", "치과"), answeredTitles(server))
    }

    @Test
    fun voiceQueryWithoutMorningOrAfternoonFindsBoth() = runTest {
        server.start(backgroundScope)
        addAll(
            Schedule(title = "약", date = tomorrow, time = LocalTime.of(3, 0)),
            Schedule(title = "병원", date = tomorrow, time = LocalTime.of(15, 0)),
            Schedule(title = "산책", date = tomorrow, time = LocalTime.of(16, 0)),
        )
        llm.output = query(type = 1, date = "내일", time = "세 시")
        assertEquals(listOf("약", "병원"), answeredTitles(server))
    }

    @Test
    fun voiceQueryWithATimeRangeLooksAtTheWholeDay() = runTest {
        server.start(backgroundScope)
        addAll(
            Schedule(title = "약", date = tomorrow, time = LocalTime.of(9, 0)),
            Schedule(title = "병원", date = tomorrow, time = LocalTime.of(17, 0)),
        )
        llm.output = query(type = 1, date = "내일", time = "16시 십 분부터")
        assertEquals(listOf("약", "병원"), answeredTitles(server))
        llm.output = query(type = 1, date = "이번 주말") // not a single day
        assertEquals(Sentences.ASK_AGAIN, askAgainText())
    }

    @Test
    fun voiceSetBecomesADraftWithoutSaving() = runTest {
        server.start(backgroundScope)
        val time = now.toLocalTime().minusMinutes(1)
        llm.output = set(time = "%02d:%02d".format(time.hour, time.minute), title = "약 먹기", type = 2)

        val draft = (server.voice.ask(ByteArray(0)) as VoiceAnswer.Draft).draft
        Log.d(TAG, "draft: $draft")
        assertEquals(ScheduleKind.ALARM, draft.kind)
        assertEquals(tomorrow, draft.date) // the time has passed today
        assertTrue(server.schedules.getSchedules(now.toLocalDate(), tomorrow).isEmpty())
    }

    @Test
    fun voiceSetResolvesTheSpokenDateAndTime() = runTest {
        server.start(backgroundScope)
        llm.output = set(date = "내일", time = "오후 7시", title = "헬스", type = 1) // the board's real answer
        val health = (server.voice.ask(ByteArray(0)) as VoiceAnswer.Draft).draft
        assertEquals(Schedule(title = "헬스", date = tomorrow, time = LocalTime.of(19, 0)), health)

        llm.output = set(time = "삼십 분 뒤", title = "물 마시기", type = 2)
        val water = (server.voice.ask(ByteArray(0)) as VoiceAnswer.Draft).draft
        val later = now.plusMinutes(30)
        assertEquals(later.toLocalDate(), water.date)
        assertEquals(later.toLocalTime(), water.time)
        assertEquals(ScheduleKind.ALARM, water.kind)
    }

    @Test
    fun voiceSetWithoutMorningOrAfternoonAsksBackWithSpeechOnly() = runTest {
        server.start(backgroundScope)
        llm.output = set(date = "내일", time = "세 시", title = "병원", type = 1)
        val question = server.voice.ask(ByteArray(0)) as VoiceAnswer.AskHalfDay
        assertEquals("오전 3시요, 오후 3시요?", question.text)
        assertEquals(LocalTime.of(3, 0), question.morning.time)
        assertEquals(LocalTime.of(15, 0), question.afternoon.time)
        assertNotNull(question.audio)

        val calls = llm.calls
        stt.text = "오후요"
        val draft = (server.voice.chooseHalfDay(question, ByteArray(0)) as VoiceAnswer.Draft).draft
        assertEquals(Schedule(title = "병원", date = tomorrow, time = LocalTime.of(15, 0)), draft)
        assertEquals(calls, llm.calls) // the short answer never reaches the LLM
    }

    @Test
    fun anUnclearHalfDayAnswerIsAskedTwiceThenGuessed() = runTest {
        server.start(backgroundScope)
        llm.output = set(date = "내일", time = "세 시", title = "병원", type = 1)
        var answer = server.voice.ask(ByteArray(0))
        stt.text = "몰라"
        repeat(2) {
            answer = server.voice.chooseHalfDay(answer as VoiceAnswer.AskHalfDay, ByteArray(0))
            assertEquals(Sentences.askHalfDayAgain(LocalTime.of(3, 0)), answer.text)
        }
        answer = server.voice.chooseHalfDay(answer as VoiceAnswer.AskHalfDay, ByteArray(0))
        assertEquals(LocalTime.of(15, 0), (answer as VoiceAnswer.Draft).draft.time) // 1-6 o'clock: afternoon
    }

    @Test
    fun hearingAShortAnswerUsesSpeechRecognitionOnly() = runTest {
        server.start(backgroundScope)
        stt.text = " 응 "
        val calls = llm.calls
        assertEquals("응", server.voice.hear(ByteArray(0)))
        assertEquals(calls, llm.calls)
    }

    @Test
    fun voiceEditChangesOnlyTheSpokenFields() = runTest {
        server.start(backgroundScope)
        val existing = Schedule(id = 7, title = "진료", date = tomorrow, time = LocalTime.of(9, 0), place = "○○내과")
        llm.output = set(time = "오전 11시", title = "", type = 1)

        val draft = (server.voice.ask(ByteArray(0), existing) as VoiceAnswer.Draft).draft
        assertEquals(existing.copy(time = LocalTime.of(11, 0)), draft)
    }

    @Test
    fun voiceEditKeepsAlarmOrCalendarEvenWhenTheModelIsUnsure() = runTest {
        server.start(backgroundScope)
        val alarm = Schedule(id = 8, title = "약", date = tomorrow, time = LocalTime.of(9, 0), kind = ScheduleKind.ALARM)
        llm.output = set(time = "오전 11시", title = "", type = -1) // "11시로 바꿔 줘"
        val draft = (server.voice.ask(ByteArray(0), alarm) as VoiceAnswer.Draft).draft
        assertEquals(alarm.copy(time = LocalTime.of(11, 0)), draft)
    }

    @Test
    fun theAlarmScreenAlwaysMakesAnAlarm() = runTest {
        server.start(backgroundScope)
        llm.output = set(date = "내일", time = "오후 7시", title = "약 먹기", type = 1)
        val draft = (server.voice.ask(ByteArray(0), kind = ScheduleKind.ALARM) as VoiceAnswer.Draft)
        assertEquals(ScheduleKind.ALARM, draft.draft.kind)
        assertTrue(draft.text, draft.text.startsWith("이렇게 알람을 맞출까요?"))
    }

    @Test
    fun unclearOrInvalidOutputAsksAgain() = runTest {
        server.start(backgroundScope)
        llm.output = query(type = -1)
        assertEquals(Sentences.ASK_AGAIN, askAgainText())
        llm.output = set(date = "다음 주", title = "병원", type = 1) // not a single day: never guessed
        assertEquals(Sentences.ASK_AGAIN, askAgainText())
        llm.output = set(date = "내일", time = "오전 열 시 70분", title = "병원", type = 1)
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

    private suspend fun addAll(vararg schedules: Schedule) =
        schedules.forEach { server.schedules.addSchedule(it, emptyList()) }

    private suspend fun askAgainText(): String =
        (server.voice.ask(ByteArray(0)) as VoiceAnswer.AskAgain).text

    private suspend fun changeSettings(change: (Settings) -> Settings) {
        server.settings.updateSettings(change(server.settings.getSettings()))
    }

    private suspend fun remindTimes(id: Long): List<Duration> =
        server.schedules.getSchedule(id)!!.reminders.map { it.remindBefore }

    private fun query(type: Int, date: String = "", time: String = "", place: String = "") =
        """<maum_0>{"date_expression":"$date","time_expression":"$time","place":"$place","type":$type}"""

    private suspend fun answeredTitles(server: BoardApiServer): List<String> =
        (server.voice.ask(ByteArray(0)) as VoiceAnswer.Answered).schedules.map { it.title }

    private fun set(date: String = "", time: String = "", title: String, type: Int) =
        """<maum_1>{"date_expression":"$date","time_expression":"$time","title":"$title","type":$type}"""

    private class ScriptedStt : SttModule {
        var text = "내일 병원 몇 시야?"

        override suspend fun load() = Unit

        override suspend fun transcribe(audio: ByteArray) = text
    }

    private class ScriptedLlm : LlmModule {
        var output = ""
        var calls = 0

        override suspend fun load() = Unit

        override suspend fun generate(text: String, requestTime: LocalDateTime): String {
            calls++
            return output
        }
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

package com.k2j3.seniorassistant.device.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2j3.seniorassistant.device.audio.AudioPlayer
import com.k2j3.seniorassistant.device.audio.VoiceRecorder
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.data.schedule.ScheduleKind
import com.k2j3.seniorassistant.device.server.BoardApiServer
import com.k2j3.seniorassistant.device.server.VoiceAnswer
import com.k2j3.seniorassistant.device.ui.common.koreanDate
import com.k2j3.seniorassistant.device.ui.common.koreanTime
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 대화를 어디서 열었는지. 첫마디와 끝난 뒤 버튼만 다르고, 무엇을 할지(묻기·넣기)는 LLM이 말을 듣고 정한다.
 * TALK: 처음 화면의 말로 부탁하기 / WRITE: 할 일 넣기 화면의 말로 넣기 / EDIT: 고르고 들어온 할 일을 말로 고치기
 * ALARM: 알람 맞추기 화면의 말로 맞추기 (모델이 일정으로 보더라도 알람으로 넣는다)
 */
enum class VoiceMode { TALK, WRITE, EDIT, ALARM }

/** 대화 말풍선 한 개 */
sealed interface VoiceMessage {
    /** 도우미의 말. 날짜·시간은 화면에서 초록으로 강조. example은 아래에 작게 '예를 들면' */
    data class Bot(val text: String, val example: String? = null, val isError: Boolean = false) : VoiceMessage
    /** 어르신의 말 (알아들은 말) */
    data class Me(val text: String) : VoiceMessage
    /** 도우미가 생각하는 중 */
    data object Thinking : VoiceMessage
    /** 초안 확인: 이렇게 넣을까요? / 바꿀까요? */
    data class Draft(val title: String, val draft: Schedule, val original: Schedule?) : VoiceMessage
}

enum class VoiceStatus { LISTEN, THINK, SPEAK }

data class VoiceUiState(
    val mode: VoiceMode,
    val messages: List<VoiceMessage>,
    val status: VoiceStatus,
    /** 대화가 끝나서 '또 물어보기/넣기/고치기'를 보여 줄 때 */
    val finished: Boolean = false,
    /** 저장한 할 일의 날짜 (저장한 뒤 그 날의 달력으로, 알람이면 알람 화면으로 넘어간다) */
    val savedDate: LocalDate? = null,
    val savedAlarm: Boolean = false,
)

/**
 * 말로 부탁하기 대화 (FR-02, FR-03, FR-05).
 * 듣기 → server.voice.ask → 대답 / 초안 / 오전·오후 되묻기 / 다시 묻기 / 준비 중.
 * 초안은 "응"을 들어야만 저장한다 (SCH-02 / SCH-03). 짧은 대답은 STT로만 듣는다 (server.voice.hear).
 */
class VoiceViewModel(
    private val server: BoardApiServer,
    private val player: AudioPlayer,
    private val recorder: VoiceRecorder,
    /** 소리를 만들지 못했을 때, 도우미가 말한 뒤 다시 듣기 전까지 기다리는 시간 (소리가 있으면 다 읽을 때까지) */
    private val speakHoldMillis: Long = 2_000,
) : ViewModel() {

    private var mode = VoiceMode.TALK
    private var editing: Schedule? = null
    private var job: Job? = null

    private val state = MutableStateFlow(VoiceUiState(mode, listOf(prompt()), VoiceStatus.LISTEN))
    val uiState: StateFlow<VoiceUiState> = state.asStateFlow()

    /** 대화 화면에 들어올 때. 고치기 화면의 '말로 고치기'면 editingId가 온다 */
    fun start(mode: VoiceMode, editingId: Long? = null) {
        this.mode = mode
        job?.cancel()
        state.value = VoiceUiState(mode, listOf(prompt()), VoiceStatus.LISTEN)
        job = viewModelScope.launch {
            editing = editingId?.let { server.schedules.getSchedule(it)?.schedule }
            sayText(prompt().text, holdIfSilent = false) // 첫마디
            converse()
        }
    }

    /** '또 말하기'는 이어서, '또 넣기/또 고치기'는 처음부터 */
    fun again() {
        if (mode != VoiceMode.TALK) return start(mode, editing?.id)
        job?.cancel()
        state.update { it.copy(finished = false) }
        job = viewModelScope.launch { converse() }
    }

    fun stop() { job?.cancel() }

    private suspend fun converse() {
        var halfDay: VoiceAnswer.AskHalfDay? = null // "오전 3시요, 오후 3시요?" 다음에는 그 대답으로 듣는다
        while (true) {
            val audio = listen()
            val question = halfDay
            halfDay = null
            val answer = think { if (question != null) server.voice.chooseHalfDay(question, audio) else askUntilReady(audio) }
            when (answer) {
                is VoiceAnswer.AskHalfDay -> {
                    if (answer.transcript.isNotBlank()) add(VoiceMessage.Me(answer.transcript))
                    add(VoiceMessage.Bot(answer.text)); say(answer.audio)
                    halfDay = answer
                }
                is VoiceAnswer.Answered -> {
                    add(VoiceMessage.Me(answer.transcript), VoiceMessage.Bot(answer.text))
                    say(answer.audio, holdIfSilent = false)
                    return finish()
                }
                is VoiceAnswer.AskAgain -> {
                    if (answer.transcript.isNotBlank()) add(VoiceMessage.Me(answer.transcript))
                    add(VoiceMessage.Bot(answer.text)); say(answer.audio)
                }
                VoiceAnswer.NotReady -> {
                    add(VoiceMessage.Bot(answer.text, isError = true)); say(answer.audio)
                }
                is VoiceAnswer.Draft -> {
                    val title = when {
                        editing != null -> "이렇게 바꿀까요?"
                        answer.draft.kind == ScheduleKind.ALARM -> "이렇게 알람을 맞출까요?"
                        else -> "이렇게 넣을까요?"
                    }
                    add(VoiceMessage.Me(answer.transcript), VoiceMessage.Draft(title, answer.draft, editing))
                    say(answer.audio)
                    if (confirm(answer.draft)) return finish()
                }
            }
        }
    }

    /** 초안 뒤 "응/아니"를 듣는다. 저장했으면 true */
    private suspend fun confirm(draft: Schedule): Boolean {
        while (true) {
            val audio = listen()
            val heard = think { server.voice.hear(audio) } // STT only: "응" does not need the LLM
            if (heard.isNotBlank()) add(VoiceMessage.Me(heard))
            when {
                YES.any { it in heard } && NO.none { it in heard } -> {
                    save(draft)
                    val done = doneText(draft)
                    add(VoiceMessage.Bot(done))
                    sayText(done, holdIfSilent = false) // savedDate가 바뀌면 화면을 떠나서 읽기가 끊기므로 먼저 읽는다
                    state.update { it.copy(savedDate = draft.date, savedAlarm = draft.kind == ScheduleKind.ALARM) }
                    return true
                }
                NO.any { it in heard } -> {
                    botSays("알겠어요. 다시 말씀해 주세요.")
                    return false
                }
                else -> {
                    botSays("맞으면 \"응\", 아니면 \"아니\"라고 말씀해 주세요.")
                }
            }
        }
    }

    private suspend fun askUntilReady(audio: ByteArray): VoiceAnswer {
        repeat(3) {
            val a = server.voice.ask(audio, editing, fixedKind())
            if (a == VoiceAnswer.NotReady) delay(1_000) else return a // 준비 중이면 1초 뒤 다시
        }
        return server.voice.ask(audio, editing, fixedKind())
    }

    private fun fixedKind(): ScheduleKind? = if (mode == VoiceMode.ALARM) ScheduleKind.ALARM else null

    private suspend fun save(draft: Schedule) {
        val e = editing
        if (e == null) {
            server.schedules.addSchedule(draft)
        } else {
            val reminders: List<Duration> = server.schedules.getSchedule(e.id)?.reminders?.map { it.remindBefore } ?: emptyList()
            server.schedules.updateSchedule(draft.copy(id = e.id), reminders)
        }
    }

    private fun doneText(d: Schedule): String {
        val whenText = "${d.date.koreanDate()} ${d.time?.koreanTime() ?: ""}".trim()
        return when {
            editing != null -> "바꿨어요. $whenText ${d.title}이에요."
            d.kind == ScheduleKind.ALARM -> "알람을 맞췄어요. $whenText ${d.title}, 그 시간에 알람으로 알려 드릴게요."
            else -> "넣었어요. $whenText ${d.title}, 때가 되면 미리 알려 드릴게요."
        }
    }

    /**
     * 도우미의 말을 스피커로 다 읽을 때까지 기다린다.
     * 소리가 없거나 읽지 못하면, holdIfSilent일 때 글을 읽을 시간만큼 기다린다.
     */
    private suspend fun say(audio: ByteArray?, holdIfSilent: Boolean = true) {
        if (audio != null && player.play(audio)) delay(AFTER_SPEAK_MILLIS)
        else if (holdIfSilent) delay(speakHoldMillis)
    }

    /** 대답이 아닌 도우미 자신의 말 (첫마디, 확인, 저장했어요) */
    private suspend fun sayText(text: String, holdIfSilent: Boolean = true) =
        say(server.voice.speak(text), holdIfSilent)

    private suspend fun botSays(text: String) {
        add(VoiceMessage.Bot(text))
        sayText(text)
    }

    private suspend fun listen(): ByteArray {
        state.update { it.copy(status = VoiceStatus.LISTEN) }
        return recorder.record()
    }

    private suspend fun <T> think(block: suspend () -> T): T {
        state.update { it.copy(status = VoiceStatus.THINK, messages = it.messages + VoiceMessage.Thinking) }
        val result = block()
        state.update { it.copy(messages = it.messages - VoiceMessage.Thinking) }
        return result
    }

    private fun add(vararg m: VoiceMessage) = state.update { it.copy(messages = it.messages + m, status = VoiceStatus.SPEAK) }

    private fun finish() = state.update { it.copy(status = VoiceStatus.SPEAK, finished = true) }

    /** 어디서 열었는지에 따라 다른 첫마디와 예시 */
    private fun prompt() = when (mode) {
        VoiceMode.TALK -> VoiceMessage.Bot("무엇을 도와드릴까요?", example = "\"내일 3시에 병원 가야 해\", \"병원 언제였지?\"")
        VoiceMode.WRITE -> VoiceMessage.Bot("언제, 무슨 일이 있으세요?", example = "\"모레 11시에 치과 가야 해\"")
        VoiceMode.EDIT -> VoiceMessage.Bot("어떻게 바꿀까요?", example = "\"3시로 바꿔 줘\"")
        VoiceMode.ALARM -> VoiceMessage.Bot("언제, 무엇을 알려 드릴까요?", example = "\"30분 뒤에 약 먹으라고 알려 줘\"")
    }

    private companion object {
        /** 다 읽은 뒤 잠깐 쉬고 듣는다: 스피커 끝소리(울림)가 녹음에 들어가지 않게 */
        const val AFTER_SPEAK_MILLIS = 300L
        val YES = listOf("응", "네", "예", "그래", "맞아", "좋아")
        val NO = listOf("아니", "아냐", "싫어", "틀려")
    }
}

package com.k2j3.seniorassistant.device.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2j3.seniorassistant.device.data.schedule.Schedule
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

/** 말로 부탁하기에서 고른 카드 */
enum class VoiceMode { WRITE, ASK, EDIT }

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
    /** 저장한 할 일의 날짜 (저장한 뒤 그 날의 달력으로 넘어간다) */
    val savedDate: LocalDate? = null,
)

/**
 * 말로 부탁하기 대화 (FR-02, FR-03, FR-05).
 * 듣기 → server.voice.ask → 대답 / 초안 / 다시 묻기 / 준비 중.
 * 초안은 "응"을 들어야만 저장한다 (SCH-02 / SCH-03).
 */
class VoiceViewModel(
    private val server: BoardApiServer,
    /** 도우미가 말한 뒤 다시 듣기 전까지 기다리는 시간 (실제로는 읽어 주기가 끝날 때까지) */
    private val speakHoldMillis: Long = 2_000,
) : ViewModel() {

    private var mode = VoiceMode.ASK
    private var editing: Schedule? = null
    private val recorder = VoiceRecorder()
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
            converse()
        }
    }

    /** '또 물어보기'는 이어서, '또 넣기/또 고치기'는 처음부터 */
    fun again() {
        if (mode != VoiceMode.ASK) return start(mode, editing?.id)
        job?.cancel()
        state.update { it.copy(finished = false) }
        job = viewModelScope.launch { converse() }
    }

    fun stop() { job?.cancel() }

    private suspend fun converse() {
        while (true) {
            val audio = listen()
            when (val answer = think { askUntilReady(audio) }) {
                is VoiceAnswer.Answered -> {
                    add(VoiceMessage.Me(answer.transcript), VoiceMessage.Bot(answer.text))
                    return finish()
                }
                is VoiceAnswer.AskAgain -> {
                    if (answer.transcript.isNotBlank()) add(VoiceMessage.Me(answer.transcript))
                    add(VoiceMessage.Bot(answer.text)); delay(speakHoldMillis)
                }
                VoiceAnswer.NotReady -> {
                    add(VoiceMessage.Bot(answer.text, isError = true)); delay(speakHoldMillis)
                }
                is VoiceAnswer.Draft -> {
                    val title = if (editing != null) "이렇게 바꿀까요?" else "이렇게 넣을까요?"
                    add(VoiceMessage.Me(answer.transcript), VoiceMessage.Draft(title, answer.draft, editing))
                    delay(speakHoldMillis)
                    if (confirm(answer.draft)) return finish()
                }
            }
        }
    }

    /** 초안 뒤 "응/아니"를 듣는다. 저장했으면 true */
    private suspend fun confirm(draft: Schedule): Boolean {
        while (true) {
            val audio = listen()
            val heard = think { transcriptOf(server.voice.ask(audio)) }
            if (heard.isNotBlank()) add(VoiceMessage.Me(heard))
            when {
                YES.any { it in heard } && NO.none { it in heard } -> {
                    save(draft)
                    add(VoiceMessage.Bot(doneText(draft)))
                    state.update { it.copy(savedDate = draft.date) }
                    return true
                }
                NO.any { it in heard } -> {
                    add(VoiceMessage.Bot("알겠어요. 다시 말씀해 주세요.")); delay(speakHoldMillis)
                    return false
                }
                else -> {
                    add(VoiceMessage.Bot("맞으면 \"응\", 아니면 \"아니\"라고 말씀해 주세요.")); delay(speakHoldMillis)
                }
            }
        }
    }

    private suspend fun askUntilReady(audio: ByteArray): VoiceAnswer {
        repeat(3) {
            val a = server.voice.ask(audio, editing)
            if (a == VoiceAnswer.NotReady) delay(1_000) else return a // 준비 중이면 1초 뒤 다시
        }
        return server.voice.ask(audio, editing)
    }

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
        return if (editing == null) "넣었어요. $whenText ${d.title}, 때가 되면 미리 알려 드릴게요." else "바꿨어요. $whenText ${d.title}이에요."
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

    /** 카드마다 다른 첫마디와 예시 */
    private fun prompt() = when (mode) {
        VoiceMode.WRITE -> VoiceMessage.Bot("언제, 무슨 일이 있으세요?", example = "모레 11시에 치과 가야 해")
        VoiceMode.ASK -> VoiceMessage.Bot("무엇이 궁금하세요?", example = "오늘 병원 몇 시야?")
        VoiceMode.EDIT -> VoiceMessage.Bot("어떤 할 일을 바꾸거나 지울까요?", example = "병원 3시로 바꿔 줘")
    }

    private fun transcriptOf(a: VoiceAnswer) = when (a) {
        is VoiceAnswer.Answered -> a.transcript
        is VoiceAnswer.Draft -> a.transcript
        is VoiceAnswer.AskAgain -> a.transcript
        VoiceAnswer.NotReady -> ""
    }

    private companion object {
        val YES = listOf("응", "네", "예", "그래", "맞아", "좋아")
        val NO = listOf("아니", "아냐", "싫어", "틀려")
    }
}

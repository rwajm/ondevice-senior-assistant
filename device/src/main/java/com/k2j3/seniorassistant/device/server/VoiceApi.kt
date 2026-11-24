package com.k2j3.seniorassistant.device.server

import com.k2j3.seniorassistant.device.ai.llm.FunctionCall
import com.k2j3.seniorassistant.device.ai.llm.QueryType
import com.k2j3.seniorassistant.device.ai.llm.SetType
import com.k2j3.seniorassistant.device.ai.queue.InferenceQueue
import com.k2j3.seniorassistant.device.ai.queue.InferenceResult
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.data.schedule.ScheduleKind
import com.k2j3.seniorassistant.device.data.schedule.ScheduleRepository
import com.k2j3.seniorassistant.device.data.schedule.ScheduleWithReminders
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

sealed interface VoiceAnswer {
    val text: String
    val audio: ByteArray?

    data class Answered(
        val transcript: String,
        val schedules: List<Schedule>,
        override val text: String,
        override val audio: ByteArray?,
    ) : VoiceAnswer

    data class Draft( // shown for the user to confirm; not saved
        val transcript: String,
        val draft: Schedule,
        override val text: String,
        override val audio: ByteArray?,
    ) : VoiceAnswer

    /** "세 시"처럼 오전·오후 없이 말한 등록: "오전 3시요, 오후 3시요?" 대답은 [VoiceApi.chooseHalfDay]로 */
    data class AskHalfDay(
        val transcript: String,
        val morning: Schedule,
        val afternoon: Schedule,
        val retries: Int,
        override val text: String,
        override val audio: ByteArray?,
    ) : VoiceAnswer

    data class AskAgain(
        val transcript: String,
        override val text: String,
        override val audio: ByteArray?,
    ) : VoiceAnswer

    data object NotReady : VoiceAnswer {
        override val text = Sentences.NOT_READY
        override val audio: ByteArray? = null
    }
}

class VoiceApi internal constructor(
    private val queue: InferenceQueue,
    private val schedules: ScheduleRepository,
    private val speaker: Speaker,
    private val now: () -> LocalDateTime,
) {
    // editing: the schedule being changed by voice (FR-02), null otherwise.
    // kind: what a new entry must be whatever the model says (the alarm screen's "말로 맞추기"), null to let it decide
    suspend fun ask(audio: ByteArray, editing: Schedule? = null, kind: ScheduleKind? = null): VoiceAnswer {
        val transcript = when (val result = queue.transcribe(audio)) {
            is InferenceResult.Success -> result.value.trim()
            InferenceResult.NotReady -> return VoiceAnswer.NotReady
            is InferenceResult.Failed -> ""
        }
        if (transcript.isEmpty()) return askAgain(transcript, Sentences.ASK_AGAIN)
        val requestTime = now()
        val call = when (val result = queue.inferFunctionCall(transcript, requestTime)) {
            is InferenceResult.Success -> result.value
            InferenceResult.NotReady -> return VoiceAnswer.NotReady
            is InferenceResult.Failed -> return askAgain(transcript, Sentences.UNSUPPORTED)
        }
        return when (call) {
            is FunctionCall.QuerySchedule -> answer(transcript, call, requestTime)
            is FunctionCall.SetSchedule -> draft(transcript, call, editing, kind, requestTime)
        }
    }

    /** 짧은 대답("응", "오후요")은 STT로만 듣는다. LLM은 8초쯤 걸린다. 못 들었거나 준비 중이면 "" */
    suspend fun hear(audio: ByteArray): String =
        (queue.transcribe(audio) as? InferenceResult.Success)?.value?.trim().orEmpty()

    /** [VoiceAnswer.AskHalfDay]에 대한 대답. 두 번 더 못 알아들으면 1~6시는 오후, 7~11시는 오전으로 본다 */
    suspend fun chooseHalfDay(question: VoiceAnswer.AskHalfDay, audio: ByteArray): VoiceAnswer {
        val heard = hear(audio)
        val chosen = when (halfDayOf(heard)) {
            HalfDay.MORNING -> question.morning
            HalfDay.AFTERNOON -> question.afternoon
            null -> if (question.retries < HALF_DAY_RETRIES) {
                val text = Sentences.askHalfDayAgain(question.morning.time!!)
                return question.copy(transcript = heard, retries = question.retries + 1, text = text, audio = speaker.speak(text))
            } else if (question.morning.time!!.hour in 1..6) {
                question.afternoon // "세 시에 병원"은 보통 오후
            } else {
                question.morning // "여덟 시에 깨워 줘"는 보통 아침
            }
        }
        return draftAnswer(heard, chosen, now().toLocalDate())
    }

    // the board's own words (confirm prompts, done) in the user's speech rate; null when it cannot be made
    suspend fun speak(text: String): ByteArray? = speaker.speak(text)

    private suspend fun answer(
        transcript: String,
        query: FunctionCall.QuerySchedule,
        requestTime: LocalDateTime,
    ): VoiceAnswer {
        val place = query.place.ifBlank { null }
        val found = when (query.type) {
            QueryType.CONDITION -> {
                val spoken = resolveSpokenWhen(query.dateExpression, query.timeExpression, requestTime, future = false)
                val times = when (val time = spoken.time) {
                    is Spoken.Value -> when (val clock = time.value) {
                        is ClockTime.Exact -> listOf(clock.time)
                        is ClockTime.AmOrPm -> listOf(clock.am, clock.pm) // "세 시에 뭐 있어?": 둘 다 찾는다
                    }
                    else -> listOf(null) // 시각을 말하지 않았거나 "세 시부터"처럼 찾을 수 없는 범위: 그날 전체
                }
                val matched = when (val date = spoken.date) {
                    is Spoken.Value -> times.flatMap { schedules.getSchedules(date.value, date.value, it, place) }
                    Spoken.None -> times.flatMap { upcoming(it, place, requestTime) }
                    Spoken.Unknown -> return askAgain(transcript, Sentences.ASK_AGAIN) // "다음 주", "이번 주말"
                }
                matched.distinctBy { it.schedule.id }.sortedWith(compareBy({ it.schedule.date }, { it.schedule.time }))
            }
            QueryType.RECENT -> schedules.getRecentSchedules(1, place)
            QueryType.NEXT -> schedules.getNextSchedules(requestTime, 1, place)
            QueryType.UNCLEAR -> return askAgain(transcript, Sentences.ASK_AGAIN)
        }.map { it.schedule }
        val text = Sentences.found(found, requestTime.toLocalDate())
        return VoiceAnswer.Answered(transcript, found, text, speaker.speak(text))
    }

    private suspend fun upcoming( // no date given: from now on
        time: LocalTime?,
        place: String?,
        requestTime: LocalDateTime,
    ): List<ScheduleWithReminders> {
        if (time == null) return schedules.getNextSchedules(requestTime, Int.MAX_VALUE, place)
        val today = requestTime.toLocalDate()
        val from = if (time > requestTime.toLocalTime()) today else today.plusDays(1)
        return schedules.getSchedules(from, OPEN_END, time, place)
    }

    private suspend fun draft(
        transcript: String,
        set: FunctionCall.SetSchedule,
        editing: Schedule?,
        fixedKind: ScheduleKind?,
        requestTime: LocalDateTime,
    ): VoiceAnswer {
        // changing one: it stays what it was ("3시로 바꿔 줘" says nothing about alarm or calendar)
        val kind = editing?.kind ?: fixedKind ?: when (set.type) {
            SetType.CALENDAR -> ScheduleKind.CALENDAR
            SetType.ALARM -> ScheduleKind.ALARM
            SetType.UNCLEAR -> return askAgain(transcript, Sentences.ASK_AGAIN) // never saved
        }
        if (editing == null && set.title.isBlank()) return askAgain(transcript, Sentences.ASK_AGAIN)
        val spoken = resolveSpokenWhen(set.dateExpression, set.timeExpression, requestTime, future = true)
        val date = when (val d = spoken.date) {
            is Spoken.Value -> d.value
            Spoken.None -> null
            Spoken.Unknown -> return askAgain(transcript, Sentences.ASK_AGAIN) // 잘못된 날로 저장하지 않는다
        }

        fun at(time: LocalTime?): Schedule = editing?.copy( // only the spoken fields change; the place stays
            title = set.title.ifBlank { editing.title },
            date = date ?: editing.date,
            time = time ?: editing.time,
            kind = kind,
        ) ?: Schedule(title = set.title, date = date ?: dayOf(time, requestTime), time = time, kind = kind)

        val today = requestTime.toLocalDate()
        return when (val time = spoken.time) {
            Spoken.None -> draftAnswer(transcript, at(null), today)
            Spoken.Unknown -> askAgain(transcript, Sentences.ASK_AGAIN)
            is Spoken.Value -> when (val clock = time.value) {
                is ClockTime.Exact -> draftAnswer(transcript, at(clock.time), today)
                is ClockTime.AmOrPm -> {
                    val text = Sentences.askHalfDay(clock.am)
                    VoiceAnswer.AskHalfDay(transcript, at(clock.am), at(clock.pm), 0, text, speaker.speak(text))
                }
            }
        }
    }

    private suspend fun draftAnswer(transcript: String, draft: Schedule, today: LocalDate): VoiceAnswer {
        val text = Sentences.draft(draft, today)
        return VoiceAnswer.Draft(transcript, draft, text, speaker.speak(text))
    }

    private fun dayOf(time: LocalTime?, requestTime: LocalDateTime): LocalDate {
        val today = requestTime.toLocalDate()
        val passed = time != null && time <= requestTime.toLocalTime()
        return if (passed) today.plusDays(1) else today // "8시에 알려줘" means the next 8 o'clock
    }

    private suspend fun askAgain(transcript: String, text: String) =
        VoiceAnswer.AskAgain(transcript, text, speaker.speak(text))

    private companion object {
        val OPEN_END: LocalDate = LocalDate.of(9999, 12, 31) // LocalDate.MAX breaks text order
        const val HALF_DAY_RETRIES = 2
    }
}

internal enum class HalfDay { MORNING, AFTERNOON }

/** "오후요", "아침에" 같은 짧은 대답. 둘 다 있거나("오전 말고 오후") 없으면 null */
internal fun halfDayOf(heard: String): HalfDay? {
    val morning = MORNING_WORDS.any { it in heard }
    val afternoon = AFTERNOON_WORDS.any { it in heard }
    return when {
        morning && !afternoon -> HalfDay.MORNING
        afternoon && !morning -> HalfDay.AFTERNOON
        else -> null
    }
}

private val MORNING_WORDS = listOf("오전", "아침", "새벽")
private val AFTERNOON_WORDS = listOf("오후", "점심", "낮", "저녁", "밤")

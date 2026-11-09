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
    // editing: the schedule being changed by voice (FR-02), null otherwise
    suspend fun ask(audio: ByteArray, editing: Schedule? = null): VoiceAnswer {
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
            is FunctionCall.SetSchedule -> draft(transcript, call, editing, requestTime)
        }
    }

    private suspend fun answer(
        transcript: String,
        query: FunctionCall.QuerySchedule,
        requestTime: LocalDateTime,
    ): VoiceAnswer {
        val place = query.place.ifBlank { null }
        val found = when (query.type) {
            QueryType.CONDITION -> query.date
                ?.let { schedules.getSchedules(it, it, query.time, place) }
                ?: upcoming(query.time, place, requestTime)
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
        requestTime: LocalDateTime,
    ): VoiceAnswer {
        val kind = when (set.type) {
            SetType.CALENDAR -> ScheduleKind.CALENDAR
            SetType.ALARM -> ScheduleKind.ALARM
            SetType.UNCLEAR -> return askAgain(transcript, Sentences.ASK_AGAIN) // never saved
        }
        val draft = editing?.copy( // only the spoken fields change
            title = set.title.ifBlank { editing.title },
            date = set.date ?: editing.date,
            time = set.time ?: editing.time,
            place = set.place.ifBlank { editing.place },
            kind = kind,
        ) ?: Schedule(
            title = set.title.ifBlank { return askAgain(transcript, Sentences.ASK_AGAIN) },
            date = set.date ?: dayOf(set, requestTime),
            time = set.time,
            place = set.place,
            kind = kind,
        )
        val text = Sentences.draft(draft, requestTime.toLocalDate())
        return VoiceAnswer.Draft(transcript, draft, text, speaker.speak(text))
    }

    private fun dayOf(set: FunctionCall.SetSchedule, requestTime: LocalDateTime): LocalDate {
        val today = requestTime.toLocalDate()
        val passed = set.time != null && set.time <= requestTime.toLocalTime()
        return if (passed) today.plusDays(1) else today // "8시에 알려줘" means the next 8 o'clock
    }

    private suspend fun askAgain(transcript: String, text: String) =
        VoiceAnswer.AskAgain(transcript, text, speaker.speak(text))

    private companion object {
        val OPEN_END: LocalDate = LocalDate.of(9999, 12, 31) // LocalDate.MAX breaks text order
    }
}

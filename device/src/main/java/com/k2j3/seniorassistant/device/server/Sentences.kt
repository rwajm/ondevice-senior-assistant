package com.k2j3.seniorassistant.device.server

import com.k2j3.seniorassistant.device.ai.document.DocumentFields
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

internal object Sentences { // spoken and shown as is
    const val ASK_AGAIN = "잘 알아듣지 못했어요. 다시 한 번 말씀해 주세요."
    const val UNSUPPORTED = "그건 도와드리기 어려워요. \"내일 일정 알려줘\"처럼 물어봐 주세요."
    const val NOT_FOUND = "찾는 일정이 없어요."
    const val NOT_READY = "준비 중이에요. 잠시 뒤에 다시 말씀해 주세요."
    const val DOCUMENT_NOT_DETECTED = "글자를 읽지 못했어요. 글자가 다 보이게 다시 찍어 주세요."
    const val DOCUMENT_UNAVAILABLE = "지금은 문서를 읽을 수 없어요. 직접 적어 주세요." // FR-04
    const val SPEECH_PREVIEW = "이 빠르기로 말씀드릴게요."

    fun found(schedules: List<Schedule>, today: LocalDate): String =
        if (schedules.isEmpty()) NOT_FOUND else schedules.joinToString(" ") { describe(it, today) }

    fun draft(schedule: Schedule, today: LocalDate): String =
        "이렇게 적을까요? ${describe(schedule, today)}"

    fun dailySummary(schedules: List<Schedule>, today: LocalDate): String =
        "오늘 일정은 ${schedules.size}개예요. " + found(schedules, today)

    fun document(fields: DocumentFields, today: LocalDate): String {
        val time = listOfNotNull(fields.date?.let { day(it, today) }, fields.time?.let(::clock))
        val what = listOf(fields.place, fields.title).filter { it.isNotBlank() }
        val items = items(fields.items)
        return "이런 내용이 있어요. " + (time + what).joinToString(" ") + "." + items
    }

    private fun describe(schedule: Schedule, today: LocalDate): String {
        val time = listOfNotNull(day(schedule.date, today), schedule.time?.let(::clock))
        val what = listOf(schedule.place, schedule.title).filter { it.isNotBlank() }
        return time.joinToString(" ") + ", " + what.joinToString(" ") + " 일정이 있어요." +
            items(schedule.items)
    }

    private fun items(items: List<String>): String =
        if (items.isEmpty()) "" else " 챙길 것은 ${items.joinToString(", ")}."

    private fun day(date: LocalDate, today: LocalDate): String =
        when (ChronoUnit.DAYS.between(today, date)) {
            0L -> "오늘"
            1L -> "내일"
            2L -> "모레"
            else -> "${date.monthValue}월 ${date.dayOfMonth}일"
        }

    private fun clock(time: LocalTime): String {
        val noon = if (time.hour < 12) "오전" else "오후"
        val hour = (time.hour % 12).takeIf { it != 0 } ?: 12
        val minute = if (time.minute == 0) "" else " ${time.minute}분"
        return "$noon ${hour}시$minute"
    }
}

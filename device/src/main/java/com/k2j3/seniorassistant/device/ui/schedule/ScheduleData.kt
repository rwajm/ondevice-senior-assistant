package com.k2j3.seniorassistant.device.ui.schedule

import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.data.schedule.ScheduleKind
import java.time.LocalDate

/** 날짜 하나의 할 일을 시간 순서로 (시간 없는 할 일은 맨 뒤) */
fun List<Schedule>.on(date: LocalDate): List<Schedule> =
    filter { it.date == date }.sortedWith(compareBy(nullsLast()) { it.time })

/** 할 일 보기·달력에 나오는 것: 알람은 알람 화면에만 나온다 */
fun List<Schedule>.calendarOnly(): List<Schedule> = filter { it.kind == ScheduleKind.CALENDAR }

/** 챙길 것 목록 ↔ 입력 칸 글자 ("신분증, 먹는 약") */
fun List<String>.joinItems(): String = joinToString(", ")
fun String.splitItems(): List<String> = split(",").map { it.trim() }.filter { it.isNotEmpty() }

package com.k2j3.seniorassistant.device.ui.schedule

import com.k2j3.seniorassistant.device.data.schedule.Schedule
import java.time.LocalDate

/** 날짜 하나의 할 일을 시간 순서로 (시간 없는 할 일은 맨 뒤) */
fun List<Schedule>.on(date: LocalDate): List<Schedule> =
    filter { it.date == date }.sortedWith(compareBy(nullsLast()) { it.time })

/** 챙길 것 목록 ↔ 입력 칸 글자 ("신분증, 먹는 약") */
fun List<String>.joinItems(): String = joinToString(", ")
fun String.splitItems(): List<String> = split(",").map { it.trim() }.filter { it.isNotEmpty() }

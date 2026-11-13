package com.k2j3.seniorassistant.device.ui.common

import java.time.LocalDate
import java.time.LocalTime

private val WEEKDAYS = listOf("월", "화", "수", "목", "금", "토", "일")

/** 11월 13일 (금) */
fun LocalDate.koreanDate(): String = "${monthValue}월 ${dayOfMonth}일 (${WEEKDAYS[dayOfWeek.value - 1]})"

/** 오전·오후 */
fun LocalTime.amPm(): String = if (hour < 12) "오전" else "오후"

/** 2:06 (12시간제) */
fun LocalTime.clock12(): String {
    val h = hour % 12
    return "${if (h == 0) 12 else h}:%02d".format(minute)
}

/** 오후 2시, 오전 10시 30분 */
fun LocalTime.koreanTime(): String {
    val h = hour % 12
    val base = "${amPm()} ${if (h == 0) 12 else h}시"
    return if (minute == 0) base else "$base ${minute}분"
}

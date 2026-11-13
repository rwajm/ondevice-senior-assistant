package com.k2j3.seniorassistant.device.ui.schedule

import com.k2j3.seniorassistant.device.data.schedule.Schedule
import java.time.LocalDate
import java.time.LocalTime

/** 미리보기(@Preview)에서만 쓰는 예시 할 일 */
internal fun sampleSchedules(today: LocalDate) = listOf(
    Schedule(1, "병원 진료", today, LocalTime.of(14, 0), "○○내과", listOf("신분증", "먹는 약")),
    Schedule(2, "관리비 내는 날", today, LocalTime.of(18, 0), "관리사무소", listOf("고지서")),
)

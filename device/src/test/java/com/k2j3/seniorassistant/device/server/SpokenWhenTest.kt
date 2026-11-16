package com.k2j3.seniorassistant.device.server

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

// expressions come from the model's training and evaluation labels
class SpokenWhenTest {
    private val now = LocalDateTime.of(2026, 11, 18, 14, 0) // a Wednesday

    private fun date(expression: String, future: Boolean = true) =
        resolveSpokenWhen(expression, "", now, future).date

    private fun time(expression: String, at: LocalDateTime = now) =
        resolveSpokenWhen("", expression, at, true).time

    private fun day(month: Int, day: Int, year: Int = 2026) = Spoken.Value(LocalDate.of(year, month, day))

    private fun exact(hour: Int, minute: Int = 0) = Spoken.Value(ClockTime.Exact(LocalTime.of(hour, minute)))

    private fun amOrPm(hour: Int, minute: Int = 0) = Spoken.Value(ClockTime.AmOrPm(LocalTime.of(hour, minute)))

    private fun assertEach(expected: Any, vararg expressions: String, resolve: (String) -> Any) =
        expressions.forEach { assertEquals(it, expected, resolve(it)) }

    @Test
    fun nothingSaidIsNone() {
        assertEquals(SpokenWhen(Spoken.None, Spoken.None), resolveSpokenWhen("", "", now, true))
        assertEquals(SpokenWhen(Spoken.None, Spoken.None), resolveSpokenWhen(" ", "  ", now, false))
    }

    @Test
    fun daysCountedFromToday() {
        assertEach(day(11, 18), "오늘", "오 늘") { date(it) }
        assertEach(day(11, 19), "내일", "낼", "내일에") { date(it) }
        assertEach(day(11, 20), "모레", "내일모레", "낼 모레", "이틀 뒤에") { date(it) }
        assertEach(day(11, 21), "글피", "사흘 후", "사흘 뒤", "사흘 뒤의", "삼 일 뒤에", "삼 일 후") { date(it) }
        assertEquals(day(11, 17), date("어제"))
        assertEquals(day(11, 25), date("일주일 뒤"))
        assertEquals(day(12, 18), date("한 달 후"))
    }

    @Test
    fun weekdaysWithoutAWeekAreTheNearestIncludingToday() {
        assertEquals(day(11, 18), date("수요일")) // today
        assertEquals(day(11, 20), date("금요일"))
        assertEquals(day(11, 23), date("월요일"))
    }

    @Test
    fun weekdaysInThisNextAndLaterWeeks() {
        assertEach(day(11, 20), "이번 주 금요일", "이번 금요일") { date(it) }
        assertEach(day(11, 21), "이번 토요일", "이번 주 토요일") { date(it) }
        assertEach(day(11, 22), "이번 주 일요일", "이번 일요일") { date(it) } // weeks start on Monday
        assertEach(day(11, 23), "다음 주 월요일", "다음 월요일", "담주 월요일") { date(it) }
        assertEquals(day(11, 24), date("다음 주 화요일"))
        assertEquals(day(11, 29), date("다음 주 일요일"))
        assertEquals(day(12, 2), date("다다음 주 수요일"))
        assertEquals(day(11, 9), date("지난주 월요일"))
    }

    @Test
    fun aPassedDayOfThisWeekMovesOnOnlyForASet() {
        assertEquals(day(11, 23), date("이번 주 월요일", future = true))
        assertEquals(day(11, 16), date("이번 주 월요일", future = false))
    }

    @Test
    fun calendarDates() {
        assertEach(day(3, 1, 2027), "삼월 일일", "3월 1일", "삼월 첫날", "3월 첫날") { date(it) }
        assertEquals(day(3, 13, 2027), date("삼월 십삼일"))
        assertEquals(day(4, 20, 2027), date("4월 20일"))
        assertEquals(day(12, 25), date("12월 25일"))
        assertEach(day(1, 1, 2027), "내년 1월 1일", "새해 첫날", "1월 1일") { date(it) }
        assertEquals(day(4, 7, 2029), date("2029년 4월 7일"))
        assertEquals(day(8, 6), date("올해 8월 6일"))
        assertEquals(day(8, 1, 2027), date("8월 첫날"))
    }

    @Test
    fun datesInThisOrTheNextMonth() {
        assertEach(day(11, 22), "이번 달 22일", "이십이일", "22일") { date(it) }
        assertEach(day(12, 25), "다음 달 25일", "담달 25일") { date(it) }
        assertEquals(day(12, 1), date("다음 달 첫날"))
        assertEquals(day(12, 24), date("다음 달 이십사일"))
        assertEquals(day(12, 8), date("다음 달 팔일"))
        assertEquals(day(11, 30), date("이번 달 말일"))
    }

    @Test
    fun aPassedDateMovesOnForASetButStaysNearForAQuery() {
        assertEquals(day(12, 10), date("10일", future = true))
        assertEquals(day(11, 10), date("10일", future = false))
        assertEquals(day(11, 10, 2027), date("11월 10일", future = true))
        assertEquals(day(11, 10), date("11월 10일", future = false))
        assertEquals(day(1, 5, 2027), date("1월 5일", future = false)) // nearer than last January
    }

    @Test
    fun datesWithoutASingleDayAreUnknown() {
        assertEach(Spoken.Unknown, "다음 주", "이번 주말", "3월", "다음 달", "이번 달", "하루 전", "2월 30일", "내년 5일") {
            date(it)
        }
    }

    @Test
    fun hoursWithAPartOfTheDay() {
        assertEquals(exact(14), time("오후 두 시"))
        assertEquals(exact(9, 30), time("오전 아홉 시 반"))
        assertEquals(exact(9, 40), time("아침 아홉 시 사십 분"))
        assertEquals(exact(12), time("낮 열두 시"))
        assertEquals(exact(14), time("낮 두 시"))
        assertEquals(exact(19), time("저녁 일곱 시"))
        assertEquals(exact(17, 50), time("저녁 오 시 오십 분"))
        assertEquals(exact(21, 25), time("밤 아홉 시 이십오 분"))
        assertEquals(exact(6, 40), time("새벽 여섯 시 40분"))
        assertEquals(exact(1, 30), time("새벽 일 시 반"))
        assertEquals(exact(9), time("오전 구 시"))
        assertEquals(exact(11, 45), time("오전 십일 시 사십오 분"))
        assertEquals(exact(12, 35), time("낮 십이 시 삼십오 분"))
        assertEquals(exact(22, 30), time("오후 10시 반"))
    }

    @Test
    fun spacingAndDigitsDoNotMatter() {
        assertEach(exact(9, 30), "아침 아홉 시반", "아침 아홉시 반", "아침 9시 30분", "오전 9시 반", "오전 아홉 시 30분") {
            time(it)
        }
        assertEquals(exact(20, 40), time("오후 여덟 시사십 분"))
        assertEquals(exact(12, 55), time("낮 12시55분"))
        assertEquals(exact(13, 40), time("오후 1:40"))
        assertEquals(exact(11), time("오전 11:00"))
        assertEquals(exact(8, 45), time("오전 08:45"))
        assertEquals(exact(18, 25), time("18:25"))
        assertEquals(exact(0, 15), time("00:15"))
    }

    @Test
    fun twelveOClockAndTwentyFourHourTimes() {
        assertEquals(exact(0, 30), time("오전 12:30"))
        assertEquals(exact(0, 30), time("오전 열두 시 반"))
        assertEquals(exact(12, 30), time("오후 12시 반"))
        assertEquals(exact(12), time("열두 시")) // without 밤 it is noon
        assertEquals(exact(16, 30), time("십육 시 반"))
        assertEquals(exact(23, 40), time("이십삼 시 사십 분"))
        assertEquals(exact(17), time("17시 00분"))
        assertEach(exact(0), "0시", "영 시") { time(it) }
        assertEquals(exact(0, 15), time("00시 15분"))
    }

    @Test
    fun onTheHourAndAboutTheHour() {
        assertEach(exact(11), "오전 열한 시 정각", "오전 열한 시 영 분", "오전 열한 시쯤", "오전 열한 시에") { time(it) }
        assertEquals(exact(18), time("저녁 여섯 시 0분"))
        assertEquals(exact(8, 20), time("아침 여덟 시 이십 분 경"))
        assertEquals(exact(19), time("저녁 일곱 시쯤"))
    }

    @Test
    fun noonAndMidnight() {
        assertEach(exact(12), "정오", "정오에") { time(it) }
        assertEquals(exact(12, 30), time("정오 열두 시 반"))
        listOf("자정", "자정 0시", "밤 열두 시", "24시").forEach {
            assertEquals(it, SpokenWhen(day(11, 19), exact(0)), resolveSpokenWhen("", it, now, true))
        }
        assertEquals(SpokenWhen(day(11, 20), exact(0)), resolveSpokenWhen("내일", "자정", now, true))
        assertEquals(SpokenWhen(day(11, 19), exact(0, 30)), resolveSpokenWhen("오늘", "밤 열두 시 반", now, true))
    }

    @Test
    fun hoursWithoutMorningOrAfternoonAreAskedBack() {
        assertEquals(amOrPm(3), time("세 시"))
        assertEquals(amOrPm(9), time("아홉 시"))
        assertEquals(amOrPm(10, 20), time("10시 20분"))
        assertEquals(amOrPm(8, 15), time("8시15분"))
        assertEquals(amOrPm(2, 45), time("2시 사십오 분"))
        assertEquals(LocalTime.of(15, 0), ClockTime.AmOrPm(LocalTime.of(3, 0)).pm)
    }

    @Test
    fun elapsedTimesStartFromNow() {
        assertEach(SpokenWhen(day(11, 18), exact(14, 30)),
            "삼십 분 있다가", "반 시간 뒤에", "반 시간 있다", "지금부터 서른 분 뒤", "지금 기준 반 시간 뒤",
            "지금에서 삼십 분 지나면", "30분 후", "삼십 분 후에") {
            resolveSpokenWhen("", it, now, true)
        }
        assertEquals(exact(15, 15), time("1시간 15분 뒤에"))
        assertEquals(exact(15, 30), time("한 시간 반 뒤"))
        assertEquals(exact(15, 30), time("지금에서 한 시간 삼십 분 지나면"))
        assertEquals(exact(16, 35), time("두 시간 삼십오 분 후"))
        assertEquals(exact(15, 30), time("지금부터 90분 후에"))
        assertEquals(exact(18), time("네 시간 뒤에"))
        assertEquals(exact(14, 45), time("지금에서 사십오 분 지나면"))
    }

    @Test
    fun elapsedTimesCanCrossMidnight() {
        val late = LocalDateTime.of(2026, 11, 18, 23, 30, 40)
        assertEquals(SpokenWhen(day(11, 19), exact(0, 30)), resolveSpokenWhen("", "한 시간 뒤", late, true))
        assertEquals(SpokenWhen(day(11, 19), exact(0, 30)), resolveSpokenWhen("오늘", "한 시간 뒤", late, true))
        assertEquals(Spoken.Unknown, resolveSpokenWhen("내일", "삼십 분 뒤", now, true).time) // contradicts
    }

    @Test
    fun aPartOfTheDayAloneIsNoTime() {
        assertEach(Spoken.None, "오후", "오전", "아침", "낮", "오후에") { time(it) }
    }

    @Test
    fun rangesAndWrongTimesAreUnknown() {
        assertEach(Spoken.Unknown,
            "16시 십 분부터", "저녁 여섯 시 이후", "오후 세 시에서 네 시 사이", "오전 열 시 십오 분 전",
            "오전 열 시 70분", "오후 두 시 75분", "오전 3시 60분", "오전 22시", "밤 한 시", "820") {
            time(it)
        }
    }

    @Test
    fun aKnownDateSurvivesAnUnknownTime() {
        assertEquals(SpokenWhen(day(11, 19), Spoken.Unknown), resolveSpokenWhen("내일", "16시 십 분부터", now, false))
    }

    @Test
    fun koreanNumbers() {
        mapOf(
            "12" to 12, "08" to 8, "한" to 1, "두" to 2, "세" to 3, "네" to 4, "다섯" to 5, "일곱" to 7, "여덟" to 8,
            "열" to 10, "열한" to 11, "열두" to 12, "서른" to 30, "마흔다섯" to 45, "일" to 1, "구" to 9, "십" to 10,
            "십일" to 11, "십오" to 15, "이십" to 20, "이십삼" to 23, "사십오" to 45, "오십오" to 55, "영" to 0,
        ).forEach { (text, n) -> assertEquals(text, n, koreanNumber(text)) }
        listOf("", "십십", "일곱여덟", "시").forEach { assertEquals(it, null, koreanNumber(it)) }
    }
}

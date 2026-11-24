package com.k2j3.seniorassistant.device.server

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** 날짜나 시각 하나: 말하지 않았다 / 말했지만 계산할 수 없다 / 계산한 값 */
sealed interface Spoken<out T> {
    data object None : Spoken<Nothing>

    data object Unknown : Spoken<Nothing>

    data class Value<out T>(val value: T) : Spoken<T>
}

sealed interface ClockTime {
    data class Exact(val time: LocalTime) : ClockTime

    /** 오전·오후 없이 말한 1~11시 ("세 시"). 등록은 다시 묻고, 조회는 둘 다 찾는다 */
    data class AmOrPm(val am: LocalTime) : ClockTime {
        val pm: LocalTime get() = am.plusHours(12)
    }
}

data class SpokenWhen(val date: Spoken<LocalDate>, val time: Spoken<ClockTime>)

/**
 * 모델이 발화에서 옮긴 날짜·시간 표현("다음 주 월요일", "오후 세 시 반", "삼십 분 뒤")을
 * 요청 시각 [now] 기준의 날짜와 시각으로 바꾼다. 띄어쓰기는 보지 않는다.
 *
 * [future]: 등록처럼 앞으로의 날을 말할 때 true. 연도 없는 "3월 1일"이나 "22일", "이번 월요일"이
 * 이미 지났으면 다음 해·다음 달·다음 주로 본다. 조회(false)는 가장 가까운 날로 본다.
 * "자정"은 그날 밤 12시(다음 날 0시), 요일만 말하면 오늘을 포함해 가장 가까운 그 요일이다.
 */
fun resolveSpokenWhen(
    dateExpression: String,
    timeExpression: String,
    now: LocalDateTime,
    future: Boolean,
): SpokenWhen {
    val today = now.toLocalDate()
    val date = parseDate(compact(dateExpression), today, future)
    return when (val time = parseTime(compact(timeExpression))) {
        ParsedTime.None -> SpokenWhen(date, Spoken.None)
        ParsedTime.Unknown -> SpokenWhen(date, Spoken.Unknown)
        is ParsedTime.After -> {
            val at = now.plus(time.duration).truncatedTo(ChronoUnit.MINUTES)
            // "오늘 한 시간 뒤"는 자정을 넘겨도 된다. "내일 삼십 분 뒤"는 서로 맞지 않는다
            if (date is Spoken.Value && date.value != at.toLocalDate() && date.value != today) {
                SpokenWhen(date, Spoken.Unknown)
            } else {
                SpokenWhen(Spoken.Value(at.toLocalDate()), Spoken.Value(ClockTime.Exact(at.toLocalTime())))
            }
        }
        is ParsedTime.Clock -> {
            val day = if (!time.nextDay) {
                date
            } else {
                when (date) {
                    Spoken.None -> Spoken.Value(today.plusDays(1))
                    Spoken.Unknown -> Spoken.Unknown
                    is Spoken.Value -> Spoken.Value(date.value.plusDays(1))
                }
            }
            SpokenWhen(day, Spoken.Value(time.time))
        }
    }
}

/** 숫자, 고유어("열두", "서른"), 한자어("이십오", "십일")로 쓴 수 */
internal fun koreanNumber(s: String): Int? {
    if (s.isEmpty()) return null
    if (s.all(Char::isDigit)) return s.toIntOrNull()
    return nativeNumber(s) ?: sinoNumber(s)
}

// ---------------------------------------------------------------- 시각

private sealed interface ParsedTime {
    data object None : ParsedTime

    data object Unknown : ParsedTime

    data class After(val duration: Duration) : ParsedTime

    data class Clock(val time: ClockTime, val nextDay: Boolean = false) : ParsedTime
}

private fun parseTime(raw: String): ParsedTime {
    val t = raw.trimSuffixes(TIME_SUFFIXES)
    if (t.isEmpty() || t in PARTS_OF_DAY) return ParsedTime.None // "오후에": 시각은 말하지 않았다
    ELAPSED.matchEntire(t)?.let { return duration(it.groupValues[1])?.let(ParsedTime::After) ?: ParsedTime.Unknown }
    if (RANGES.any { it in t } || t.endsWith("분전") || t.endsWith("시전")) return ParsedTime.Unknown
    if (t.startsWith("자정")) {
        return if (t.removePrefix("자정") in MIDNIGHT_REST) midnight(0) else ParsedTime.Unknown
    }
    val noon = t.startsWith("정오")
    val marker = if (noon) "낮" else MARKERS.firstOrNull { t.startsWith(it) }
    val body = when {
        noon -> t.removePrefix("정오").ifEmpty { "12시" }
        marker != null -> t.removePrefix(marker)
        else -> t
    }
    val clock = clock(body) ?: return ParsedTime.Unknown
    return withMarker(marker, clock)
}

/** twentyFourHour: "09:00", "02시 반"처럼 콜론이나 앞의 0으로 24시간제임을 보인 시각 */
private data class Clock(val hour: Int, val minute: Int, val twentyFourHour: Boolean)

private fun clock(body: String): Clock? {
    val clock = COLON.matchEntire(body)?.let { Clock(it.groupValues[1].toInt(), it.groupValues[2].toInt(), true) }
        ?: HOUR.matchEntire(body)?.let { m ->
            val hourText = m.groupValues[1]
            val hour = koreanNumber(hourText) ?: return null
            val minute = when (val rest = m.groupValues[2]) {
                "", "정각", "영분", "공분" -> 0
                "반" -> 30
                else -> rest.takeIf { it.endsWith("분") }?.dropLast(1)?.let(::koreanNumber) ?: return null
            }
            Clock(hour, minute, hourText.length == 2 && hourText[0] == '0')
        }
        ?: return null
    return clock.takeIf { it.minute in 0..59 } // "열 시 70분"은 고치지 않는다
}

private fun withMarker(marker: String?, clock: Clock): ParsedTime {
    val (h, m) = clock
    fun at(hour: Int) = ParsedTime.Clock(ClockTime.Exact(LocalTime.of(hour, m)))
    return when (marker) {
        null -> when (h) {
            0 -> at(0)
            in 1..11 -> if (clock.twentyFourHour) at(h) else ParsedTime.Clock(ClockTime.AmOrPm(LocalTime.of(h, m)))
            in 12..23 -> at(h) // 열두 시는 낮 열두 시
            24 -> if (m == 0) midnight(0) else ParsedTime.Unknown
            else -> ParsedTime.Unknown
        }
        "오전" -> when (h) {
            in 0..11 -> at(h)
            12 -> at(0) // 오전 12:30은 0시 30분
            else -> ParsedTime.Unknown // 오전 22시
        }
        "아침" -> if (h in 4..11) at(h) else ParsedTime.Unknown
        "새벽" -> if (h in 0..7) at(h) else ParsedTime.Unknown
        "오후" -> when (h) {
            in 1..11 -> at(h + 12)
            in 12..23 -> at(h)
            else -> ParsedTime.Unknown
        }
        "낮", "점심" -> when (h) {
            in 1..6 -> at(h + 12)
            in 11..18 -> at(h)
            else -> ParsedTime.Unknown
        }
        "저녁" -> when (h) {
            in 4..11 -> at(h + 12)
            in 16..23 -> at(h)
            else -> ParsedTime.Unknown
        }
        "밤" -> when (h) {
            in 6..11 -> at(h + 12)
            12, 24 -> midnight(m) // 밤 열두 시는 다음 날 0시
            in 18..23 -> at(h)
            else -> ParsedTime.Unknown // 밤 한 시: 어느 날 밤인지 모른다
        }
        else -> ParsedTime.Unknown
    }
}

private fun midnight(minute: Int) = ParsedTime.Clock(ClockTime.Exact(LocalTime.of(0, minute)), nextDay = true)

private fun duration(amount: String): Duration? {
    if (amount == "반시간") return Duration.ofMinutes(30)
    HOURS.matchEntire(amount)?.let { m ->
        val hours = koreanNumber(m.groupValues[1]) ?: return null
        val minutes = when (val rest = m.groupValues[2]) {
            "" -> 0
            "반" -> 30
            else -> rest.takeIf { it.endsWith("분") }?.dropLast(1)?.let(::koreanNumber) ?: return null
        }
        if (hours !in 0..48 || minutes !in 0..59 || hours + minutes == 0) return null
        return Duration.ofMinutes(hours * 60L + minutes)
    }
    MINUTES.matchEntire(amount)?.let { m ->
        val minutes = koreanNumber(m.groupValues[1]) ?: return null
        return if (minutes in 1..48 * 60) Duration.ofMinutes(minutes.toLong()) else null
    }
    return null
}

// ---------------------------------------------------------------- 날짜

private fun parseDate(raw: String, today: LocalDate, future: Boolean): Spoken<LocalDate> {
    val d = raw.trimSuffixes(DATE_SUFFIXES)
    if (d.isEmpty()) return Spoken.None
    val day = relativeDay(d, today) ?: weekday(d, today, future) ?: calendarDay(d, today, future)
    return day?.let { Spoken.Value(it) } ?: Spoken.Unknown // "다음 주", "이번 주말", "3월", "하루 전"
}

private fun relativeDay(d: String, today: LocalDate): LocalDate? {
    DAY_WORDS[d]?.let { return today.plusDays(it) }
    val amount = LATER.matchEntire(d)?.groupValues?.get(1) ?: return null
    COUNTED_DAYS[amount]?.let { return today.plusDays(it) }
    val unit = UNIT.matchEntire(amount) ?: return null
    val n = koreanNumber(unit.groupValues[1])?.toLong() ?: return null
    return when (unit.groupValues[2]) {
        "일" -> today.plusDays(n)
        "주", "주일" -> today.plusWeeks(n)
        else -> today.plusMonths(n)
    }
}

private fun weekday(d: String, today: LocalDate, future: Boolean): LocalDate? {
    val m = WEEKDAY.matchEntire(d) ?: return null
    val day = DayOfWeek.of(WEEKDAYS.indexOf(m.groupValues[2]) + 1)
    val thisWeek = today.with(DayOfWeek.MONDAY).plusDays(day.value - 1L)
    return when (m.groupValues[1]) {
        "" -> today.plusDays(((day.value - today.dayOfWeek.value + 7) % 7).toLong())
        "이번주", "이번", "금주" -> if (future && thisWeek < today) thisWeek.plusWeeks(1) else thisWeek
        "다음주", "담주", "다음" -> thisWeek.plusWeeks(1)
        "다다음주", "다담주" -> thisWeek.plusWeeks(2)
        else -> thisWeek.minusWeeks(1) // 지난주, 저번주
    }
}

private fun calendarDay(d: String, today: LocalDate, future: Boolean): LocalDate? {
    val m = CALENDAR.matchEntire(d) ?: return null
    val (yearDigits, yearWord, relativeMonth, monthText, dayWord, dayText) = m.destructured
    val year = when {
        yearDigits.isNotEmpty() -> yearDigits.toInt()
        yearWord == "새해" -> if (today.dayOfYear == 1) today.year else today.year + 1
        yearWord == "내년" -> today.year + 1
        yearWord == "후년" -> today.year + 2
        yearWord.isNotEmpty() -> today.year // 올해, 금년
        else -> null
    }
    val month = when {
        monthText.isNotEmpty() -> koreanNumber(monthText)?.takeIf { it in 1..12 } ?: return null
        yearWord == "새해" && dayWord == "첫날" -> 1
        else -> null
    }
    fun dayIn(ym: YearMonth): LocalDate? = when (dayWord) {
        "첫날" -> ym.atDay(1)
        "말일", "마지막날" -> ym.atEndOfMonth()
        else -> koreanNumber(dayText)?.takeIf(ym::isValidDay)?.let(ym::atDay)
    }
    val thisMonth = YearMonth.from(today)
    if (relativeMonth.isNotEmpty()) {
        if (year != null) return null
        val offset = when (relativeMonth) {
            "이번달" -> 0L
            "다음달", "담달" -> 1L
            "다다음달" -> 2L
            else -> -1L // 지난달, 저번달
        }
        return dayIn(thisMonth.plusMonths(offset))
    }
    if (month != null) {
        if (year != null) return dayIn(YearMonth.of(year, month))
        val candidates = (-1..1).mapNotNull { dayIn(YearMonth.of(today.year + it, month)) }
        return pick(candidates, today, future)
    }
    if (year != null) return null // "내년 5일"
    val candidates = (-1L..1L).mapNotNull { dayIn(thisMonth.plusMonths(it)) }
    return pick(candidates, today, future)
}

/** 등록은 오늘 이후의 첫 날, 조회는 오늘에서 가장 가까운 날 */
private fun pick(candidates: List<LocalDate>, today: LocalDate, future: Boolean): LocalDate? =
    if (future) {
        candidates.filter { it >= today }.minOrNull()
    } else {
        candidates.minByOrNull { abs(ChronoUnit.DAYS.between(today, it)) }
    }

// ---------------------------------------------------------------- 수

private fun nativeNumber(s: String): Int? {
    NATIVE_UNITS[s]?.let { return it }
    for ((word, tens) in NATIVE_TENS) {
        if (!s.startsWith(word)) continue
        val rest = s.removePrefix(word)
        if (rest.isEmpty()) return tens
        NATIVE_UNITS[rest]?.let { return tens + it }
    }
    return null
}

private fun sinoDigit(s: String): Int? = when {
    s.length == 1 && s[0] in SINO_DIGITS -> SINO_DIGITS.indexOf(s[0])
    s == "공" -> 0
    s == "륙" -> 6
    else -> null
}

private fun sinoNumber(s: String): Int? {
    val ten = s.indexOf('십')
    if (ten < 0) return sinoDigit(s)
    if (s.indexOf('십', ten + 1) >= 0) return null
    val tens = s.substring(0, ten).let { if (it.isEmpty()) 1 else sinoDigit(it) ?: return null }
    val ones = s.substring(ten + 1).let { if (it.isEmpty()) 0 else sinoDigit(it) ?: return null }
    return tens * 10 + ones
}

// ---------------------------------------------------------------- 공통

private fun compact(s: String) = s.filterNot(Char::isWhitespace)

private fun String.trimSuffixes(suffixes: List<String>): String {
    var s = this
    while (true) {
        val hit = suffixes.firstOrNull { s.length > it.length && s.endsWith(it) } ?: return s
        s = s.dropLast(hit.length)
    }
}

private val TIME_SUFFIXES = listOf("즈음", "정도", "쯤", "경", "께", "에")
private val DATE_SUFFIXES = listOf("쯤", "께", "에", "의")
private val PARTS_OF_DAY = setOf("오전", "오후", "아침", "점심", "낮", "저녁", "밤", "새벽")
private val MARKERS = listOf("오전", "오후", "아침", "점심", "저녁", "새벽", "낮", "밤")
private val RANGES = listOf("부터", "까지", "이후", "이전", "사이", "에서", "~", "넘어")
private val MIDNIGHT_REST = setOf("", "0시", "영시", "열두시", "12시", "24시")

private val ELAPSED = Regex("""(?:지금부터|지금에서|지금기준|지금)?(.+?)(?:뒤|후|있다가|있다|지나면|지나서|이따가|이따)""")
private val COLON = Regex("""(\d{1,2}):(\d{2})""")
private val HOUR = Regex("""(.+?)시(.*)""")
private val HOURS = Regex("""(.+?)시간(.*)""")
private val MINUTES = Regex("""(.+?)분""")

private val DAY_WORDS = mapOf(
    "오늘" to 0L, "금일" to 0L, "내일" to 1L, "낼" to 1L, "모레" to 2L, "내일모레" to 2L, "낼모레" to 2L,
    "글피" to 3L, "어제" to -1L, "그제" to -2L, "그저께" to -2L,
)
private val COUNTED_DAYS = mapOf(
    "하루" to 1L, "이틀" to 2L, "사흘" to 3L, "나흘" to 4L, "닷새" to 5L, "엿새" to 6L, "이레" to 7L, "열흘" to 10L,
)
private val LATER = Regex("""(.+?)(?:뒤|후)""")
private val UNIT = Regex("""(.+?)(일|주일|주|달|개월)""")
private const val WEEKDAYS = "월화수목금토일"
private val WEEKDAY =
    Regex("""(이번주|이번|금주|다다음주|다담주|다음주|담주|다음|저번주|지난주|저번|지난)?([월화수목금토일])요일""")
private val CALENDAR = Regex(
    """(?:(\d{4})년|(내년|올해|금년|새해|후년))?(?:(이번달|다다음달|다음달|담달|지난달|저번달)|(.+?)월)?(?:(첫날|말일|마지막날)|(.+?)일)""",
)

private val NATIVE_UNITS = mapOf(
    "하나" to 1, "한" to 1, "둘" to 2, "두" to 2, "셋" to 3, "세" to 3, "넷" to 4, "네" to 4,
    "다섯" to 5, "여섯" to 6, "일곱" to 7, "여덟" to 8, "아홉" to 9,
)
private val NATIVE_TENS = listOf("스물" to 20, "스무" to 20, "서른" to 30, "마흔" to 40, "쉰" to 50, "열" to 10)
private const val SINO_DIGITS = "영일이삼사오육칠팔구"

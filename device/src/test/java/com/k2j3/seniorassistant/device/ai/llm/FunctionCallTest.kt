package com.k2j3.seniorassistant.device.ai.llm

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FunctionCallTest {
    @Test
    fun queryWithConditionsIsParsed() {
        val output =
            """<maum_0>{"date": "2026-10-28", "time": "17:00", "place": "", "type": 1}<maum_end>"""
        val expected = FunctionCall.QuerySchedule(
            date = LocalDate.of(2026, 10, 28),
            time = LocalTime.of(17, 0),
            place = "",
            type = QueryType.CONDITION,
        )
        assertEquals(expected, parseFunctionCall(output))
    }

    @Test
    fun setWithPlaceAndTitleIsParsed() {
        val output = """
            <maum_1>{"date": "2026-10-13", "time": "11:00", "place": "치과",
            "title": "치과 가기", "type": 1}<maum_end>
        """.trimIndent()
        val expected = FunctionCall.SetSchedule(
            date = LocalDate.of(2026, 10, 13),
            time = LocalTime.of(11, 0),
            place = "치과",
            title = "치과 가기",
            type = SetType.CALENDAR,
        )
        assertEquals(expected, parseFunctionCall(output))
    }

    @Test
    fun unclearTypeKeepsTheExtractedValues() {
        val output = """
            <maum_1>{"date": "", "time": "20:00", "place": "", "title": "약", "type": -1}<maum_end>
        """.trimIndent()
        val call = parseFunctionCall(output) as FunctionCall.SetSchedule
        assertEquals(SetType.UNCLEAR, call.type)
        assertEquals(LocalTime.of(20, 0), call.time)
        assertNull(call.date)
    }

    @Test
    fun outputsOutsideTheFormatAreRejected() {
        val rejected = listOf(
            "다음 일정은 내일입니다", // no function token
            """<maum_0>{"date": "", "time": "", "place": "", "type": 3}""", // no end token
            """<maum_2>{"date": "", "time": "", "place": "", "type": 1}<maum_end>""", // unknown
            """<maum_0>{"date": "", "time": "", "type": 3}<maum_end>""", // missing field
            """<maum_0>{"date": "", "time": "", "place": "", "type": 4}<maum_end>""", // bad type
            """<maum_0>{"date": "10월 28일", "time": "", "place": "", "type": 1}<maum_end>""",
            """<maum_1>{"date": "", "time": "", "place": "", "type": 1}<maum_end>""", // no title
        )
        rejected.forEach { assertNull(it, parseFunctionCall(it)) }
    }
}

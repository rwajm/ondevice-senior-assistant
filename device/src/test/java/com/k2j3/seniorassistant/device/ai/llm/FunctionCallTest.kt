package com.k2j3.seniorassistant.device.ai.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// outputs are the v3 model's answers on the QCS6490 board (Genie leaves out <maum_end>)
class FunctionCallTest {
    @Test
    fun queryWithConditionsIsParsed() {
        val output =
            """<maum_0>{"date_expression":"다음 주 일요일","time_expression":"18:25","place":"","type":1}"""
        val expected = FunctionCall.QuerySchedule(
            dateExpression = "다음 주 일요일",
            timeExpression = "18:25",
            place = "",
            type = QueryType.CONDITION,
        )
        assertEquals(expected, parseFunctionCall(output))
    }

    @Test
    fun queryByPlaceIsParsed() {
        val output =
            """<maum_0>{"date_expression":"","time_expression":"","place":"성당","type":3}"""
        val call = parseFunctionCall(output) as FunctionCall.QuerySchedule
        assertEquals("성당", call.place)
        assertEquals(QueryType.NEXT, call.type)
    }

    @Test
    fun setKeepsThePlaceInTheTitle() {
        val output = """<maum_1>{"date_expression":"내일","time_expression":"오후 세 시",""" +
            """"title":"복지관 노래 교실","type":1}"""
        val expected = FunctionCall.SetSchedule(
            dateExpression = "내일",
            timeExpression = "오후 세 시",
            title = "복지관 노래 교실",
            type = SetType.CALENDAR,
        )
        assertEquals(expected, parseFunctionCall(output))
    }

    @Test
    fun alarmWithElapsedTimeIsParsed() {
        val output = """<maum_1>{"date_expression":"","time_expression":"삼십 분 있다가",""" +
            """"title":"가스불 살피기","type":2}"""
        val call = parseFunctionCall(output) as FunctionCall.SetSchedule
        assertEquals(SetType.ALARM, call.type)
        assertEquals("", call.dateExpression)
        assertEquals("삼십 분 있다가", call.timeExpression)
    }

    @Test
    fun theEndTokenIsOptional() {
        val bare = """<maum_0>{"date_expression":"","time_expression":"","place":"","type":3}"""
        assertEquals(parseFunctionCall(bare), parseFunctionCall("$bare<maum_end>"))
        assertEquals(parseFunctionCall(bare), parseFunctionCall("  $bare <maum_end>\n"))
    }

    @Test
    fun unclearTypeKeepsTheExtractedValues() {
        val output = """<maum_1>{"date_expression":"내일","time_expression":"오후 열 시",""" +
            """"title":"","type":-1}"""
        val call = parseFunctionCall(output) as FunctionCall.SetSchedule
        assertEquals(SetType.UNCLEAR, call.type)
        assertEquals("내일", call.dateExpression)
        assertEquals("오후 열 시", call.timeExpression)
    }

    @Test
    fun outputsOutsideTheFormatAreRejected() {
        val rejected = listOf(
            "", // the broken v2 model stopped before any token
            "다음 일정은 내일입니다", // no function token
            """<maum_2>{"date_expression":"","time_expression":"","place":"","type":1}""", // unknown
            """<maum_0>{"date_expression":"","time_expression":"","type":3}""", // missing field
            """<maum_0>{"date_expression":"","time_expression":"","place":"","type":4}""", // bad type
            """<maum_0>{"date_expression":"","time_expression":"","place":"","type":"삼"}""", // not a number
            """<maum_1>{"date_expression":"","time_expression":"","place":"","type":1}""", // place, no title
            """<maum_0>{"date": "", "time": "", "place": "", "type": 3}<maum_end>""", // the old format
            """<maum_end>-<maum_end>-22T15:50:00-09:00""", // what the broken v2 model said
            """<maum_0>{"date_expression":"","time_expression":"","place":"","type":3""", // cut off
        )
        rejected.forEach { assertNull(it, parseFunctionCall(it)) }
    }
}

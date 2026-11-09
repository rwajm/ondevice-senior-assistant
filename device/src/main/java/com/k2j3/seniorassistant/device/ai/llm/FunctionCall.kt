package com.k2j3.seniorassistant.device.ai.llm

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface FunctionCall { // model design doc 3.2.2
    data class QuerySchedule( // <maum_0>; null or blank means no condition
        val date: LocalDate?,
        val time: LocalTime?,
        val place: String,
        val type: QueryType,
    ) : FunctionCall

    data class SetSchedule( // <maum_1>
        val date: LocalDate?,
        val time: LocalTime?,
        val place: String,
        val title: String,
        val type: SetType,
    ) : FunctionCall
}

enum class QueryType(val code: Int) {
    CONDITION(1),
    RECENT(2),
    NEXT(3),
    UNCLEAR(-1),
}

enum class SetType(val code: Int) {
    CALENDAR(1),
    ALARM(2),
    UNCLEAR(-1), // never saved automatically
}

fun parseFunctionCall(output: String): FunctionCall? { // null unless the allowed format
    val match = outputPattern.matchEntire(output.trim()) ?: return null
    val (token, body) = match.destructured
    return try {
        when (token) {
            "0" -> Json.decodeFromString<QueryArgs>(body).let { args ->
                FunctionCall.QuerySchedule(
                    date = args.date.takeIf(String::isNotBlank)?.let(LocalDate::parse),
                    time = args.time.takeIf(String::isNotBlank)?.let(LocalTime::parse),
                    place = args.place,
                    type = QueryType.entries.find { it.code == args.type } ?: return null,
                )
            }
            else -> Json.decodeFromString<SetArgs>(body).let { args ->
                FunctionCall.SetSchedule(
                    date = args.date.takeIf(String::isNotBlank)?.let(LocalDate::parse),
                    time = args.time.takeIf(String::isNotBlank)?.let(LocalTime::parse),
                    place = args.place,
                    title = args.title,
                    type = SetType.entries.find { it.code == args.type } ?: return null,
                )
            }
        }
    } catch (e: SerializationException) {
        null
    } catch (e: DateTimeParseException) {
        null
    }
}

private val outputPattern =
    Regex("""<maum_([01])>\s*(\{.*\})\s*<maum_end>""", RegexOption.DOT_MATCHES_ALL)

@Serializable
private class QueryArgs(val date: String, val time: String, val place: String, val type: Int)

@Serializable
private class SetArgs(
    val date: String,
    val time: String,
    val place: String,
    val title: String,
    val type: Int,
)

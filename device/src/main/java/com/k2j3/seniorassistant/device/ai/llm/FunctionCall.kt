package com.k2j3.seniorassistant.device.ai.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * 모델이 고른 기능과 인자 (학습 데이터와 common_prompt.txt 형식).
 *
 * 날짜·시간은 발화에서 가져온 표현 그대로다 ("내일", "오후 세 시", "삼십 분 뒤").
 * 달력 날짜와 시각은 앱이 요청 시각을 기준으로 계산한다. 빈 문자열은 발화에 없었다는 뜻이다.
 */
sealed interface FunctionCall {
    val dateExpression: String
    val timeExpression: String

    /** <maum_0> 조회 */
    data class QuerySchedule(
        override val dateExpression: String,
        override val timeExpression: String,
        val place: String,
        val type: QueryType,
    ) : FunctionCall

    /** <maum_1> 설정. 장소는 따로 받지 않고 title에 들어 있다 ("복지관 노래 교실") */
    data class SetSchedule(
        override val dateExpression: String,
        override val timeExpression: String,
        val title: String,
        val type: SetType,
    ) : FunctionCall
}

enum class QueryType(val code: Int) {
    CONDITION(1), // 날짜·시각·장소 조건
    RECENT(2), // 마지막으로 등록한 일정
    NEXT(3), // 앞으로 가장 가까운 일정
    UNCLEAR(-1), // 지원하지 않는 조회
}

enum class SetType(val code: Int) {
    CALENDAR(1), // 새 일정
    ALARM(2), // 지정 시각의 알림·깨우기
    UNCLEAR(-1), // 실행하지 않는다
}

/**
 * 허용한 형식이 아니면 null.
 * Genie는 종료 토큰에서 멈추면서 <maum_end>를 출력에 붙이지 않으므로 있어도 없어도 받는다.
 */
fun parseFunctionCall(output: String): FunctionCall? {
    val match = outputPattern.matchEntire(output.trim()) ?: return null
    val (token, body) = match.destructured
    return try {
        when (token) {
            "0" -> json.decodeFromString<QueryArgs>(body).let { args ->
                FunctionCall.QuerySchedule(
                    dateExpression = args.date_expression.trim(),
                    timeExpression = args.time_expression.trim(),
                    place = args.place.trim(),
                    type = QueryType.entries.find { it.code == args.type } ?: return null,
                )
            }
            else -> json.decodeFromString<SetArgs>(body).let { args ->
                FunctionCall.SetSchedule(
                    dateExpression = args.date_expression.trim(),
                    timeExpression = args.time_expression.trim(),
                    title = args.title.trim(),
                    type = SetType.entries.find { it.code == args.type } ?: return null,
                )
            }
        }
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) { // a field of the wrong kind
        null
    }
}

private val outputPattern =
    Regex("""<maum_([01])>\s*(\{.*\})\s*(?:<maum_end>)?""", RegexOption.DOT_MATCHES_ALL)

private val json = Json // strict: every field required, no extra fields (a set with place is rejected)

@Suppress("PropertyName") // the model's field names
@Serializable
private class QueryArgs(
    val date_expression: String,
    val time_expression: String,
    val place: String,
    val type: Int,
)

@Suppress("PropertyName")
@Serializable
private class SetArgs(
    val date_expression: String,
    val time_expression: String,
    val title: String,
    val type: Int,
)

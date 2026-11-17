package com.k2j3.seniorassistant.device.ai.document

import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * EXT-01: OpenAI Responses API로 문서 사진에서 일정 항목을 뽑는다.
 * 요청 모양은 실제 API로 확인한 tools/document_eval/eval_document.py와 같다
 * (instructions + input_image(data URL), text.format = json_schema strict, store false).
 *
 * 인터넷이 안 되면 IOException(재시도 없음), 응답이 늦거나 API가 실패하면 ExternalAiException(한 번 더 시도).
 * 시간 제한은 [DocumentAnalyzer]가 시도마다 20초로 건다. 여기 읽기 제한도 같게 둬서 연결이 남지 않게 한다.
 */
class OpenAiClient(
    private val apiKey: String,
    private val endpoint: String = "https://api.openai.com/v1/responses",
) : ExternalAiClient {

    override suspend fun extract(request: ExternalAiRequest): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw ExternalAiException("no OpenAI key (openai.api.key in local.properties)")
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body(request).toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw ExternalAiException("HTTP $status: ${errorMessage(text)}")
            outputText(text)
        } catch (e: SocketTimeoutException) {
            throw ExternalAiException("timed out: ${e.message}") // slow, not offline: worth one more try
        } finally {
            connection.disconnect()
        }
    }

    internal fun body(request: ExternalAiRequest): String = buildJsonObject {
        put("model", request.model)
        put("instructions", request.instructions)
        put("input", buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "input_image")
                        put("image_url", "data:${request.mimeType};base64,${request.imageBase64}")
                    })
                })
            })
        })
        put("text", buildJsonObject {
            put("format", buildJsonObject {
                put("type", "json_schema")
                put("name", "document_extraction")
                put("schema", Json.parseToJsonElement(request.jsonSchema))
                put("strict", true)
            })
        })
        put("store", request.store)
    }.toString()

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 20_000
    }
}

/** 응답의 output 중 message의 output_text를 이어 붙인다 (SDK의 response.output_text와 같음) */
internal fun outputText(response: String): String {
    val root = try {
        Json.parseToJsonElement(response).jsonObject
    } catch (e: IllegalArgumentException) {
        throw ExternalAiException("not JSON: ${response.take(200)}")
    }
    val status = (root["status"] as? JsonPrimitive)?.contentOrNull
    if (status != null && status != "completed") throw ExternalAiException("status $status")
    val output = root["output"] as? JsonArray ?: throw ExternalAiException("no output")
    val parts = output.mapNotNull { it as? JsonObject }
        .filter { (it["type"] as? JsonPrimitive)?.contentOrNull == "message" }
        .flatMap { (it["content"] as? JsonArray).orEmpty() }
        .mapNotNull { it as? JsonObject }
    parts.firstOrNull { (it["type"] as? JsonPrimitive)?.contentOrNull == "refusal" }?.let {
        throw ExternalAiException("refused: ${it["refusal"]}")
    }
    val text = parts.filter { (it["type"] as? JsonPrimitive)?.contentOrNull == "output_text" }
        .joinToString("") { it["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
    if (text.isBlank()) throw ExternalAiException("empty output")
    return text
}

private fun errorMessage(body: String): String = try {
    Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull ?: body.take(200)
} catch (e: IllegalArgumentException) {
    body.take(200)
}

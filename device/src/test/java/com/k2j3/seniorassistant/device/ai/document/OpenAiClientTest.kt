package com.k2j3.seniorassistant.device.ai.document

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiClientTest {
    private val request = ExternalAiRequest(
        model = "gpt-6-luna",
        instructions = "사진에서 일정을 추출해.",
        imageBase64 = "aW1hZ2U=",
        mimeType = "image/png",
        jsonSchema = """{"type":"object","additionalProperties":false}""",
        store = false,
    )

    @Test
    fun requestUsesResponsesJsonSchemaAndDoesNotStoreData() {
        val body = Json.parseToJsonElement(OpenAiClient("test-key").body(request)).jsonObject

        assertEquals("gpt-6-luna", body["model"]!!.jsonPrimitive.content)
        assertEquals("사진에서 일정을 추출해.", body["instructions"]!!.jsonPrimitive.content)
        assertFalse(body["store"]!!.jsonPrimitive.boolean)
        val image = body["input"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray[0].jsonObject
        assertEquals("input_image", image["type"]!!.jsonPrimitive.content)
        assertEquals("data:image/png;base64,aW1hZ2U=", image["image_url"]!!.jsonPrimitive.content)
        val format = body["text"]!!.jsonObject["format"]!!.jsonObject
        assertEquals("json_schema", format["type"]!!.jsonPrimitive.content)
        assertEquals("document_extraction", format["name"]!!.jsonPrimitive.content)
        assertTrue(format["strict"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun extractsTheTextFromACompletedResponse() {
        val response = """
            {"status":"completed","output":[
              {"type":"message","content":[
                {"type":"output_text","text":"{\"document_detected\":true}"}
              ]}
            ]}
        """.trimIndent()

        assertEquals("{\"document_detected\":true}", outputText(response))
    }

    @Test(expected = ExternalAiException::class)
    fun refusesIncompleteResponses() {
        outputText("""{"status":"in_progress","output":[]}""")
    }
}

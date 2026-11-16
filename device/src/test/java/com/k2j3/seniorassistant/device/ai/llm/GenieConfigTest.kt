package com.k2j3.seniorassistant.device.ai.llm

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class GenieConfigTest {
    // the parts of the bundle's genie_config_npu.json that hold paths, plus neighbours that must stay
    private val config = """
        {"dialog": {"version": 1, "max-num-tokens": 128,
          "context": {"size": 2048, "eos-token": [128009, 128258]},
          "sampler": {"temp": 0.0, "top-k": 1},
          "tokenizer": {"version": 1, "path": "tokenizer.json"},
          "engine": {"backend": {"type": "QnnHtp", "QnnHtp": {"cpu-mask": "0xe0", "use-mmap": false},
                                 "extensions": "htp_backend_ext_config.json"},
                     "model": {"type": "binary", "binary": {"ctx-bins": ["llama32_hf_v68.bin"]}}}}}
    """.trimIndent()

    @Test
    fun relativePathsPointIntoTheModelFolder() {
        val fixed = Json.parseToJsonElement(withAbsolutePaths(config, File("/data/models/llm"))).jsonObject
        val dialog = fixed["dialog"]!!.jsonObject
        val engine = dialog["engine"]!!.jsonObject
        assertEquals("/data/models/llm/tokenizer.json", dialog["tokenizer"]!!.jsonObject["path"]!!.jsonPrimitive.content)
        assertEquals(
            "/data/models/llm/htp_backend_ext_config.json",
            engine["backend"]!!.jsonObject["extensions"]!!.jsonPrimitive.content,
        )
        val bins = engine["model"]!!.jsonObject["binary"]!!.jsonObject["ctx-bins"]!!.jsonArray
        assertEquals("/data/models/llm/llama32_hf_v68.bin", bins.single().jsonPrimitive.content)
    }

    @Test
    fun everythingElseStaysAsItWas() {
        val original = Json.parseToJsonElement(config).jsonObject["dialog"]!!.jsonObject
        val fixed = Json.parseToJsonElement(withAbsolutePaths(config, File("/m"))).jsonObject["dialog"]!!.jsonObject
        listOf("version", "max-num-tokens", "context", "sampler").forEach { assertEquals(it, original[it], fixed[it]) }
        val backend = { d: kotlinx.serialization.json.JsonObject -> d["engine"]!!.jsonObject["backend"]!!.jsonObject }
        assertEquals(backend(original)["QnnHtp"], backend(fixed)["QnnHtp"])
        assertEquals("0.0", fixed["sampler"]!!.jsonObject["temp"]!!.jsonPrimitive.content)
    }

    @Test
    fun absolutePathsAreKept() {
        val json = """{"tokenizer": {"path": "/sdcard/tokenizer.json"}}"""
        assertEquals(json.replace(" ", ""), withAbsolutePaths(json, File("/m")))
    }
}

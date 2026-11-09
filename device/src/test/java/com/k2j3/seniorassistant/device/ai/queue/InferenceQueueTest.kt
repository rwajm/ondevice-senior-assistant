package com.k2j3.seniorassistant.device.ai.queue

import com.k2j3.seniorassistant.device.ai.llm.FakeLlmModule
import com.k2j3.seniorassistant.device.ai.llm.FunctionCall
import com.k2j3.seniorassistant.device.ai.llm.InvalidOutputException
import com.k2j3.seniorassistant.device.ai.llm.QueryType
import com.k2j3.seniorassistant.device.ai.stt.FakeSttModule
import com.k2j3.seniorassistant.device.ai.stt.SttModule
import com.k2j3.seniorassistant.device.ai.tts.FakeTtsModule
import com.k2j3.seniorassistant.device.ai.tts.TtsModule
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceQueueTest {
    private val now = LocalDateTime.of(2026, 10, 11, 9, 0)

    @Test
    fun requestsBeforeLoadingAreNotReady() = runTest {
        val queue = InferenceQueue(FakeSttModule(), FakeLlmModule(), FakeTtsModule())
        assertEquals(InferenceResult.NotReady, queue.transcribe(ByteArray(0)))

        queue.load()
        assertEquals(ModelState.READY, queue.state.value)
        assertEquals(InferenceResult.Success("다음 일정 알려줘"), queue.transcribe(ByteArray(0)))
    }

    @Test
    fun failedLoadingKeepsTheQueueClosed() = runTest {
        val broken = object : SttModule {
            override suspend fun load() = throw IllegalStateException("model file missing")

            override suspend fun transcribe(audio: ByteArray) = ""
        }
        val queue = InferenceQueue(broken, FakeLlmModule(), FakeTtsModule())
        queue.load()
        assertEquals(ModelState.FAILED, queue.state.value)
        assertEquals(InferenceResult.NotReady, queue.transcribe(ByteArray(0)))
    }

    @Test
    fun inferencesRunOneAtATimeInArrivalOrder() = runTest {
        val log = mutableListOf<String>()
        val stt = object : SttModule {
            override suspend fun load() = Unit

            override suspend fun transcribe(audio: ByteArray): String {
                log += "stt start"
                delay(1_000)
                log += "stt end"
                return ""
            }
        }
        val tts = object : TtsModule {
            override suspend fun load() = Unit

            override suspend fun synthesize(text: String, speed: Float): ByteArray {
                log += "tts start"
                delay(10)
                log += "tts end"
                return ByteArray(0)
            }
        }
        val queue = InferenceQueue(stt, FakeLlmModule(), tts)
        queue.load()

        listOf(
            launch { queue.transcribe(ByteArray(0)) },
            launch { queue.synthesize("안녕하세요", 1f) },
        ).joinAll()
        assertEquals(listOf("stt start", "stt end", "tts start", "tts end"), log)
    }

    @Test
    fun reloadWaitsForTheRunningInference() = runTest {
        val log = mutableListOf<String>()
        val stt = object : SttModule {
            override suspend fun load() {
                log += "load"
            }

            override suspend fun transcribe(audio: ByteArray): String {
                log += "stt start"
                delay(1_000)
                log += "stt end"
                return ""
            }
        }
        val queue = InferenceQueue(stt, FakeLlmModule(), FakeTtsModule())
        queue.load()
        log.clear()

        listOf(launch { queue.transcribe(ByteArray(0)) }, launch { queue.load() }).joinAll()
        assertEquals(listOf("stt start", "stt end", "load"), log)
    }

    @Test
    fun functionCallIsParsedOrRejected() = runTest {
        val queue = InferenceQueue(FakeSttModule(), FakeLlmModule(), FakeTtsModule())
        queue.load()
        val next = FunctionCall.QuerySchedule(null, null, "", QueryType.NEXT)
        assertEquals(InferenceResult.Success(next), queue.inferFunctionCall("다음 일정", now))

        val chatty = InferenceQueue(FakeSttModule(), FakeLlmModule("내일 2시예요"), FakeTtsModule())
        chatty.load()
        val result = chatty.inferFunctionCall("내일 병원", now)
        assertTrue((result as InferenceResult.Failed).cause is InvalidOutputException)
    }

    @Test
    fun fakeSpeechIsAPlayableWav() = runTest {
        val queue = InferenceQueue(FakeSttModule(), FakeLlmModule(), FakeTtsModule())
        queue.load()
        val wav = (queue.synthesize("안녕하세요", 1f) as InferenceResult.Success).value
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals(44 + 3_200, wav.size)
    }
}

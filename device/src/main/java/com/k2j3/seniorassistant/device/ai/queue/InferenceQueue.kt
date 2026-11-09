package com.k2j3.seniorassistant.device.ai.queue

import com.k2j3.seniorassistant.device.ai.llm.FunctionCall
import com.k2j3.seniorassistant.device.ai.llm.InvalidOutputException
import com.k2j3.seniorassistant.device.ai.llm.LlmModule
import com.k2j3.seniorassistant.device.ai.llm.parseFunctionCall
import com.k2j3.seniorassistant.device.ai.stt.SttModule
import com.k2j3.seniorassistant.device.ai.tts.TtsModule
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InferenceQueue(
    private val stt: SttModule,
    private val llm: LlmModule,
    private val tts: TtsModule,
) {
    private val mutex = Mutex() // fair: requests run one at a time in arrival order

    private val _state = MutableStateFlow(ModelState.LOADING)

    val state: StateFlow<ModelState> = _state.asStateFlow()

    suspend fun load() = mutex.withLock { // waits for running inferences
        _state.value = ModelState.LOADING
        _state.value = try {
            stt.load()
            llm.load()
            tts.load()
            ModelState.READY
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ModelState.FAILED
        }
    }

    suspend fun transcribe(audio: ByteArray): InferenceResult<String> =
        enqueue { stt.transcribe(audio) }

    suspend fun inferFunctionCall(
        text: String,
        requestTime: LocalDateTime,
    ): InferenceResult<FunctionCall> = enqueue {
        val output = llm.generate(text, requestTime)
        parseFunctionCall(output) ?: throw InvalidOutputException(output)
    }

    suspend fun synthesize(text: String, speed: Float): InferenceResult<ByteArray> =
        enqueue { tts.synthesize(text, speed) }

    private suspend fun <T> enqueue(inference: suspend () -> T): InferenceResult<T> {
        if (state.value != ModelState.READY) return InferenceResult.NotReady
        return mutex.withLock {
            if (state.value != ModelState.READY) { // reloaded while waiting
                return@withLock InferenceResult.NotReady
            }
            try {
                InferenceResult.Success(inference())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                InferenceResult.Failed(e)
            }
        }
    }
}

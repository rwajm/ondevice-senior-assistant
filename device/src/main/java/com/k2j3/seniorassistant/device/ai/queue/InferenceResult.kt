package com.k2j3.seniorassistant.device.ai.queue

sealed interface InferenceResult<out T> {
    data class Success<T>(val value: T) : InferenceResult<T>

    data object NotReady : InferenceResult<Nothing> // models still loading or failed to load

    data class Failed(val cause: Throwable) : InferenceResult<Nothing>
}

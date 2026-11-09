package com.k2j3.seniorassistant.device.ai.stt

class FakeSttModule(private val transcript: String = "다음 일정 알려줘") : SttModule {
    override suspend fun load() = Unit

    override suspend fun transcribe(audio: ByteArray): String = transcript
}

package com.k2j3.seniorassistant.device.audio

import kotlinx.coroutines.delay

/** 어르신의 말 한 번을 녹음한다 (보드 프론트엔드 담당: 녹음 + 발화 끝 감지) */
interface VoiceRecorder {
    /** 16 kHz mono 16-bit WAV. 아무 말도 없었으면 빈 배열 */
    suspend fun record(): ByteArray
}

/** 마이크나 VAD 모델이 없을 때: 듣는 것처럼 잠깐 기다렸다가 빈 녹음을 돌려준다 */
class SilentVoiceRecorder(private val listenMillis: Long = 2_500) : VoiceRecorder {
    override suspend fun record(): ByteArray {
        delay(listenMillis)
        return ByteArray(0)
    }
}

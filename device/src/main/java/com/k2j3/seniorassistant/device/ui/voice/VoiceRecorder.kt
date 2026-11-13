package com.k2j3.seniorassistant.device.ui.voice

import kotlinx.coroutines.delay

/**
 * 마이크 녹음 (보드 프론트엔드 담당: 녹음 + 발화 끝 감지).
 * 아직 마이크 입력이 없어서, 정해진 시간 동안 듣는 것처럼 기다렸다가 빈 녹음을 돌려준다.
 * TODO: AudioRecord(16 kHz mono) + Silero VAD로 바꾼다.
 */
class VoiceRecorder(private val listenMillis: Long = 2_500) {
    suspend fun record(): ByteArray {
        delay(listenMillis)
        return ByteArray(0)
    }
}

package com.k2j3.seniorassistant.device.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2j3.seniorassistant.device.ai.vad.SpeechDetector
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 마이크(AudioRecord, 16 kHz mono) + VAD로 말 한 번을 녹음한다.
 * 말이 끝나면 바로 돌려주고, 아무 말이 없으면 [UtteranceCollector]의 대기 시간 뒤에 빈 녹음을 돌려준다.
 *
 * 마지막 녹음은 확인용으로 cache/last_utterance.wav에 남긴다 (run-as로 꺼내 들어 볼 수 있다).
 *
 * @param detector VAD. 처음 녹음할 때 만든다 (모델 로드)
 */
class MicVoiceRecorder(
    private val context: Context,
    detector: () -> SpeechDetector,
) : VoiceRecorder {
    private val detector by lazy(detector)
    private val mutex = Mutex() // 마이크는 한 번에 한 곳에서만
    private val fallback = SilentVoiceRecorder()

    override suspend fun record(): ByteArray {
        if (!hasPermission()) {
            Log.w(TAG, "RECORD_AUDIO not granted, not listening")
            return fallback.record()
        }
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val speech = listen() ?: return@withContext fallback.record()
                Log.i(TAG, "heard ${speech.size * 1000L / SAMPLE_RATE} ms of speech")
                if (speech.isEmpty()) ByteArray(0)
                else Wav.encode(speech.toPcm16(), SAMPLE_RATE).also(::keepForChecking)
            }
        }
    }

    /** 말한 소리 (없으면 빈 배열). 마이크를 열 수 없으면 null */
    @SuppressLint("MissingPermission") // checked in record()
    private suspend fun listen(): FloatArray? {
        val vad = detector
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) return null
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, // 음성 인식용: 자동 음량 조절 등 가공을 줄인 입력
            SAMPLE_RATE, CHANNEL, ENCODING,
            maxOf(minBuffer, SAMPLE_RATE * 2), // 1초: 추론이 잠깐 밀려도 소리를 놓치지 않게
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "microphone could not be opened")
            record.release()
            return null
        }
        val collector = UtteranceCollector(vad).also { it.start() }
        val window = ShortArray(vad.windowSize)
        try {
            record.startRecording()
            while (true) {
                currentCoroutineContext().ensureActive() // 화면을 나가면 32 ms 안에 마이크를 닫는다
                if (!readFully(record, window)) return null
                if (collector.accept(window.toFloats())) break
            }
        } finally {
            record.stop()
            record.release()
        }
        return collector.speech()
    }

    private fun readFully(record: AudioRecord, buffer: ShortArray): Boolean {
        var read = 0
        while (read < buffer.size) {
            val n = record.read(buffer, read, buffer.size - read)
            if (n < 0) {
                Log.w(TAG, "microphone read failed: $n")
                return false
            }
            read += n
        }
        return true
    }

    private fun hasPermission() =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun keepForChecking(wav: ByteArray) {
        runCatching { File(context.cacheDir, "last_utterance.wav").writeBytes(wav) }
    }

    private companion object {
        const val TAG = "MicVoiceRecorder"
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}

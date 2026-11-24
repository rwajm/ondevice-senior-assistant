package com.k2j3.seniorassistant.device.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 스피커로 도우미의 말(TTS WAV)을 읽어 준다. 앱에 하나만 두어 두 소리가 겹치지 않게 한다
 * (나중에 온 소리는 앞의 소리가 끝날 때까지 기다린다).
 */
class AudioPlayer(private val context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mutex = Mutex()

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /**
     * 끝까지 읽을 때까지 기다린다. 읽을 수 없는 소리(형식이 다르거나 비었거나 스피커 오류)면 false.
     * 부른 코루틴을 취소하면(화면을 나가면) 바로 멈춘다.
     */
    suspend fun play(wav: ByteArray): Boolean {
        val pcm = Wav.decode(wav)?.takeIf { it.frames > 0 } ?: run {
            Log.w(TAG, "not a 16-bit PCM WAV (${wav.size} bytes)")
            return false
        }
        return mutex.withLock { withContext(Dispatchers.IO) { playLocked(pcm) } }
    }

    /**
     * 앱에 넣은 소리 파일(res/raw, 알람음)을 [millis] 동안 되풀이해 울린다. 말과 같은 스피커·음량.
     * 부른 코루틴을 취소하면(확인했어요) 바로 멈춘다. 울리지 못하면 false.
     */
    suspend fun playLooped(rawRes: Int, millis: Long): Boolean = mutex.withLock {
        val player = MediaPlayer()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes).build()
        try {
            withContext(Dispatchers.IO) {
                context.resources.openRawResourceFd(rawRes).use { player.setDataSource(it) }
                player.setAudioAttributes(attributes)
                player.isLooping = true
                player.prepare()
            }
            audioManager.requestAudioFocus(focus)
            player.start()
            delay(millis)
            true
        } catch (e: java.io.IOException) {
            Log.w(TAG, "cannot play sound $rawRes", e)
            false
        } catch (e: IllegalStateException) {
            Log.w(TAG, "cannot play sound $rawRes", e)
            false
        } finally {
            runCatching { player.stop() }
            player.release()
            audioManager.abandonAudioFocusRequest(focus)
        }
    }

    private suspend fun playLocked(pcm: Pcm16): Boolean {
        val track = createTrack(pcm) ?: return false
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .build()
        audioManager.requestAudioFocus(focus)
        try {
            val deadline = SystemClock.elapsedRealtime() + pcm.durationMillis + END_SLACK_MILLIS
            track.play()
            if (!write(track, pcm.data)) return false
            // 버퍼보다 짧은 소리는 버퍼가 찰 때까지 시작하지 않는 기기가 있어서 뒤에 무음을 채운다
            write(track, ByteArray(track.bufferSizeInFrames * pcm.channels * 2))
            while (track.playbackHeadPosition < pcm.frames && SystemClock.elapsedRealtime() < deadline) {
                delay(POLL_MILLIS)
            }
            return true
        } finally {
            runCatching { track.pause(); track.flush() } // 취소됐으면 남은 소리를 바로 끊는다
            track.release()
            audioManager.abandonAudioFocusRequest(focus)
        }
    }

    /** 다 쓰면 true. 조금씩 써서 그사이 취소를 확인한다 */
    private suspend fun write(track: AudioTrack, data: ByteArray): Boolean {
        val chunk = (track.bufferSizeInFrames * track.channelCount * 2).coerceAtLeast(MIN_CHUNK_BYTES)
        var offset = 0
        while (offset < data.size) {
            currentCoroutineContext().ensureActive()
            val written = track.write(data, offset, minOf(chunk, data.size - offset))
            if (written <= 0) {
                Log.w(TAG, "AudioTrack.write failed: $written")
                return false
            }
            offset += written
        }
        return true
    }

    private fun createTrack(pcm: Pcm16): AudioTrack? {
        val channelMask = if (pcm.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minBuffer = AudioTrack.getMinBufferSize(pcm.sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) {
            Log.w(TAG, "unsupported format: ${pcm.sampleRate} Hz, ${pcm.channels} ch")
            return null
        }
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(pcm.sampleRate)
                        .setChannelMask(channelMask)
                        .build(),
                )
                .setBufferSizeInBytes(minBuffer * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) { // 잘못된 형식이거나 스피커를 열 수 없을 때
            Log.w(TAG, "cannot open AudioTrack", e)
            return null
        }
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return null
        }
        return track
    }

    private companion object {
        const val TAG = "AudioPlayer"
        const val POLL_MILLIS = 20L
        const val END_SLACK_MILLIS = 1_000L // 끝 위치를 못 받아도 소리 길이 + 1초 뒤에는 돌아온다
        const val MIN_CHUNK_BYTES = 1_024
    }
}

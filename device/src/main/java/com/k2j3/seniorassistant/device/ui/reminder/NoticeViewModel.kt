package com.k2j3.seniorassistant.device.ui.reminder

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2j3.seniorassistant.device.R
import com.k2j3.seniorassistant.device.audio.AudioPlayer
import com.k2j3.seniorassistant.device.audio.Chime
import com.k2j3.seniorassistant.device.data.schedule.ScheduleKind
import com.k2j3.seniorassistant.device.server.BoardApiServer
import com.k2j3.seniorassistant.device.server.Notice
import com.k2j3.seniorassistant.device.server.NoticeKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NoticeViewModel(
    private val server: BoardApiServer,
    private val player: AudioPlayer,
) : ViewModel() {
    val notices: StateFlow<List<Notice>> = server.notices.notices

    private var playing: Job? = null

    init {
        // 창이 뜰 때 소리로 알린다: 알람은 알람음, 일정·아침 요약은 "딩동". '소리로 알림'이 켜져 있으면 이어서 읽어 준다
        viewModelScope.launch {
            notices.map { it.firstOrNull() }
                .distinctUntilChangedBy { it?.id }
                .collect { notice ->
                    playing?.cancel()
                    playing = notice?.let { viewModelScope.launch { alert(it) } }
                }
        }
    }

    /**
     * 알람: 알람음 5초 + 안내를 '확인했어요'를 누를 때까지 30초마다 되풀이한다 (5분까지).
     * 일정 선제 알림·아침 요약: "딩동" 한 번 + 안내.
     */
    private suspend fun alert(notice: Notice) {
        val alarm = notice.kind == NoticeKind.REMINDER && notice.schedules.any { it.kind == ScheduleKind.ALARM }
        if (!alarm) {
            player.play(Chime.wav)
            notice.audio?.let { player.play(it) }
            return
        }
        repeat(ALARM_REPEATS) { i ->
            Log.i(TAG, "alarm ${notice.id}: ring ${i + 1}/$ALARM_REPEATS")
            player.playLooped(R.raw.alarm_classic, ALARM_TONE_MILLIS)
            notice.audio?.let { player.play(it) }
            delay(ALARM_GAP_MILLIS)
        }
    }

    /** '소리로 듣기'. 알람음 되풀이는 멈추고 안내만 읽는다. 소리로 알림을 꺼 둬서 소리가 없으면 지금 만든다 */
    fun play() {
        val notice = notices.value.firstOrNull() ?: return
        playing?.cancel() // 여러 번 눌러도 처음부터 한 번만
        playing = viewModelScope.launch {
            val audio = notice.audio ?: server.voice.speak(notice.text) ?: return@launch
            player.play(audio)
        }
    }

    fun dismissFirst() {
        playing?.cancel() // 확인을 누르면 울리던 것·읽던 것도 멈춘다
        notices.value.firstOrNull()?.let { server.notices.dismiss(it.id) }
    }

    private companion object {
        const val TAG = "Notice"
        const val ALARM_TONE_MILLIS = 5_000L
        const val ALARM_GAP_MILLIS = 30_000L
        const val ALARM_REPEATS = 10 // 5분 남짓
    }
}

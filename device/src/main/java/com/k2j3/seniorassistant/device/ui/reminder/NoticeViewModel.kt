package com.k2j3.seniorassistant.device.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2j3.seniorassistant.device.audio.AudioPlayer
import com.k2j3.seniorassistant.device.server.BoardApiServer
import com.k2j3.seniorassistant.device.server.Notice
import kotlinx.coroutines.Job
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
        // '소리로 알림'이 켜져 있으면 알림마다 소리가 만들어져 있다: 창이 뜰 때 바로 읽어 준다
        viewModelScope.launch {
            notices.map { it.firstOrNull() }
                .distinctUntilChangedBy { it?.id }
                .collect { notice -> if (notice?.audio != null) play() }
        }
    }

    /** '소리로 듣기'. 소리로 알림을 꺼 둬서 소리가 없으면 지금 만든다 */
    fun play() {
        val notice = notices.value.firstOrNull() ?: return
        playing?.cancel() // 여러 번 눌러도 처음부터 한 번만
        playing = viewModelScope.launch {
            val audio = notice.audio ?: server.voice.speak(notice.text) ?: return@launch
            player.play(audio)
        }
    }

    fun dismissFirst() {
        playing?.cancel() // 확인을 누르면 읽던 것도 멈춘다
        notices.value.firstOrNull()?.let { server.notices.dismiss(it.id) }
    }
}

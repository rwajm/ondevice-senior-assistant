package com.k2j3.seniorassistant.device.ui.voice

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.server.BoardApiServer
import com.k2j3.seniorassistant.device.server.VoiceAnswer
import kotlinx.coroutines.launch

class VoiceViewModel(private val server: BoardApiServer) : ViewModel() {
    private var lastAnswer: VoiceAnswer? = null

    fun ask(editingId: Long? = null) {
        viewModelScope.launch {
            val editing = editingId?.let { server.schedules.getSchedule(it)?.schedule }
            val answer = server.voice.ask(SAMPLE_AUDIO, editing)
            lastAnswer = answer
            val draft = (answer as? VoiceAnswer.Draft)?.draft
            Log.d(TAG, "${answer::class.simpleName}: ${answer.text} draft=$draft")
        }
    }

    fun takeDraft(): Schedule? {
        val draft = (lastAnswer as? VoiceAnswer.Draft)?.draft
        if (draft != null) lastAnswer = null
        return draft
    }

    fun play() {
        Log.d(TAG, "play ${lastAnswer?.audio?.size ?: 0} bytes")
    }

    private companion object {
        const val TAG = "VoiceViewModel"
        val SAMPLE_AUDIO = ByteArray(0) // no microphone input yet; the fake STT ignores it
    }
}

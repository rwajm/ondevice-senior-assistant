package com.k2j3.seniorassistant.device.ui.reminder

import android.util.Log
import androidx.lifecycle.ViewModel
import com.k2j3.seniorassistant.device.server.BoardApiServer
import com.k2j3.seniorassistant.device.server.Notice
import kotlinx.coroutines.flow.StateFlow

class NoticeViewModel(private val server: BoardApiServer) : ViewModel() {
    val notices: StateFlow<List<Notice>> = server.notices.notices

    fun play() {
        val notice = notices.value.firstOrNull()
        Log.d(TAG, "play ${notice?.audio?.size ?: 0} bytes: ${notice?.text}")
    }

    fun dismissFirst() {
        notices.value.firstOrNull()?.let { server.notices.dismiss(it.id) }
    }

    private companion object {
        const val TAG = "NoticeViewModel"
    }
}

package com.k2j3.seniorassistant.device.server

import com.k2j3.seniorassistant.device.data.schedule.Schedule
import com.k2j3.seniorassistant.device.data.settings.SettingsRepository
import com.k2j3.seniorassistant.device.scheduler.ReminderEvents
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Notice(
    val id: Long,
    val kind: NoticeKind,
    val schedules: List<Schedule>,
    val text: String,
    val audio: ByteArray?, // null when voice notification is off
)

enum class NoticeKind {
    REMINDER,
    DAILY_SUMMARY,
}

class NoticeApi internal constructor(
    private val settings: SettingsRepository,
    private val speaker: Speaker,
    private val now: () -> LocalDateTime,
) {
    private val _notices = MutableStateFlow<List<Notice>>(emptyList())

    val notices: StateFlow<List<Notice>> = _notices.asStateFlow() // open ones, oldest first

    private val nextId = AtomicLong()

    fun dismiss(noticeId: Long) {
        _notices.update { open -> open.filterNot { it.id == noticeId } }
    }

    internal fun start(scope: CoroutineScope) { // subscribes before returning
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            ReminderEvents.due.collect { due ->
                val text = Sentences.found(listOf(due.schedule), now().toLocalDate())
                open(NoticeKind.REMINDER, listOf(due.schedule), text)
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            ReminderEvents.dailySummary.collect { todays ->
                val text = Sentences.dailySummary(todays, now().toLocalDate())
                open(NoticeKind.DAILY_SUMMARY, todays, text)
            }
        }
    }

    private suspend fun open(kind: NoticeKind, schedules: List<Schedule>, text: String) {
        val voice = settings.getSettings().voiceNotificationEnabled
        val audio = if (voice) speaker.speak(text) else null
        val notice = Notice(nextId.incrementAndGet(), kind, schedules, text, audio)
        _notices.update { it + notice }
    }
}

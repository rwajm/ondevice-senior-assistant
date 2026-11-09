package com.k2j3.seniorassistant.device.server

import android.content.Context
import android.util.Log
import com.k2j3.seniorassistant.device.ai.document.DocumentAnalyzer
import com.k2j3.seniorassistant.device.ai.queue.InferenceQueue
import com.k2j3.seniorassistant.device.data.AppDatabase
import com.k2j3.seniorassistant.device.data.schedule.ScheduleRepository
import com.k2j3.seniorassistant.device.data.settings.SettingsRepository
import com.k2j3.seniorassistant.device.scheduler.ReminderScheduler
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope

class BoardApiServer internal constructor( // other databases only in tests
    context: Context,
    db: AppDatabase,
    private val queue: InferenceQueue,
    analyzer: DocumentAnalyzer,
    now: () -> LocalDateTime = LocalDateTime::now,
    log: (String) -> Unit = { Log.w(TAG, it) },
) {
    private val scheduleRepository = ScheduleRepository(db)
    private val settingsRepository = SettingsRepository(db)
    private val scheduler = ReminderScheduler(context, scheduleRepository, settingsRepository, now)
    private val speaker = Speaker(queue, settingsRepository)

    val schedules = ScheduleApi(scheduleRepository, settingsRepository, scheduler)
    val settings = SettingsApi(settingsRepository, scheduler, speaker)
    val voice = VoiceApi(queue, scheduleRepository, speaker, now)
    val documents = DocumentApi(analyzer, settingsRepository, speaker, now, log)
    val notices = NoticeApi(settingsRepository, speaker, now)

    suspend fun start(scope: CoroutineScope) { // on app start
        notices.start(scope)
        queue.load()
        scheduler.rescheduleAll() // missed reminders come out as notices
    }

    companion object {
        private const val TAG = "BoardApiServer"

        // the same database the alarm and boot receivers use
        fun create(context: Context, queue: InferenceQueue, analyzer: DocumentAnalyzer) =
            BoardApiServer(context, AppDatabase.getInstance(context), queue, analyzer)
    }
}

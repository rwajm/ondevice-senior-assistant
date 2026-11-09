package com.k2j3.seniorassistant.device.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1)
        if (reminderId < 0) return
        launchAsync { ReminderScheduler.from(context).onAlarm(reminderId) }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}

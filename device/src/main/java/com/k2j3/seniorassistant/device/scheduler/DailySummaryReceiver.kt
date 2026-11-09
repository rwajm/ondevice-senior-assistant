package com.k2j3.seniorassistant.device.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DailySummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        launchAsync { ReminderScheduler.from(context).onDailySummary() }
    }
}

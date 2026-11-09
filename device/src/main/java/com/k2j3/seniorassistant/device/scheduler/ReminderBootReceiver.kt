package com.k2j3.seniorassistant.device.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderBootReceiver : BroadcastReceiver() { // alarms are cleared on reboot
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        launchAsync { ReminderScheduler.from(context).rescheduleAll() }
    }
}

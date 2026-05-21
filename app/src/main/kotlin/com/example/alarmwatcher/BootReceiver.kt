package com.example.alarmwatcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Device booted — rescheduling next pre-warn")
            AlarmMonitor.scanNextAlarmAndSchedule(context)
        }
    }
}

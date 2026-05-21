package com.example.alarmwatcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives generic system intents indicating alarms/time changed and forwards to service to re-scan.
 */
class AlarmChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmChangeReceiver", "Received ${intent.action}")
        AlarmMonitor.scanNextAlarmAndSchedule(context)
    }
}

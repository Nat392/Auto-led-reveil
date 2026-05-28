package com.example.alarmwatcher

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives generic system intents indicating alarms/time changed and forwards to service to re-scan.
 */
class AlarmChangeReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action
        if (action == AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            AlarmMonitor.scanNextAlarmAndSchedule(context)
            SunsetAutomationScheduler.requestRefreshAndSchedule(context)
        }
    }
}

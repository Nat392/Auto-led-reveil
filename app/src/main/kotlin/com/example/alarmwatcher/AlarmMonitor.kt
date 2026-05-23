package com.example.alarmwatcher

import android.app.AlarmManager
import android.content.Context
import android.util.Log

object AlarmMonitor {
    private const val TAG = "AlarmMonitor"

    fun scanNextAlarmAndSchedule(context: Context) {
        try {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            if (alarmManager == null) {
                Log.w(TAG, "AlarmManager unavailable")
                return
            }

            val next = alarmManager.nextAlarmClock
            if (next != null) {
                val trigger = next.triggerTime

                val preWarnAt = trigger - AlarmScheduler.PREWARN_MS

                if (preWarnAt > System.currentTimeMillis()) {
                    AlarmScheduler.schedulePreWarn(context, preWarnAt, trigger)
                }
            } else {
                AlarmScheduler.cancelPreWarn(context)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing permission to read alarms: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected alarm scan failure", e)
            DiscordCrashReporter.reportNonFatal(
                context = context,
                throwable = e,
                source = "AlarmMonitor.scanNextAlarmAndSchedule"
            )
        }
    }
}
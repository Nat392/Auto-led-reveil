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
                DiscordCrashReporter.reportDebugBlocking(
                    context = context,
                    source = "AlarmMonitor.scanNextAlarmAndSchedule",
                    details = "AlarmManager unavailable"
                )
                return
            }

            val canScheduleExact = runCatching {
                alarmManager.canScheduleExactAlarms()
            }.getOrDefault(false)

            val next = alarmManager.nextAlarmClock
            if (next != null) {
                val trigger = next.triggerTime
                Log.d(TAG, "Next alarm at $trigger")

                val preWarnAt = trigger - AlarmScheduler.PREWARN_MS
                val details = buildString {
                    appendLine("AlarmMonitor.scanNextAlarmAndSchedule()")
                    appendLine("canScheduleExactAlarms=$canScheduleExact")
                    appendLine("nextAlarmTrigger=$trigger")
                    appendLine("preWarnAt=$preWarnAt")
                    appendLine("now=${System.currentTimeMillis()}")
                    appendLine("delayMs=${preWarnAt - System.currentTimeMillis()}")
                }
                DiscordCrashReporter.reportDebugBlocking(
                    context = context,
                    source = "AlarmMonitor.scanNextAlarmAndSchedule",
                    details = details
                )

                if (preWarnAt > System.currentTimeMillis()) {
                    AlarmScheduler.schedulePreWarn(context, preWarnAt, trigger)
                } else {
                    Log.d(TAG, "Pre-warn time already passed")
                }
            } else {
                Log.d(TAG, "No next alarm available")
                DiscordCrashReporter.reportDebugBlocking(
                    context = context,
                    source = "AlarmMonitor.scanNextAlarmAndSchedule",
                    details = buildString {
                        appendLine("AlarmMonitor.scanNextAlarmAndSchedule()")
                        appendLine("canScheduleExactAlarms=$canScheduleExact")
                        appendLine("nextAlarm=none")
                        appendLine("now=${System.currentTimeMillis()}")
                    }
                )
                AlarmScheduler.cancelPreWarn(context)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing permission to read alarms: ${e.message}")
            DiscordCrashReporter.reportDebugBlocking(
                context = context,
                source = "AlarmMonitor.scanNextAlarmAndSchedule",
                details = "SecurityException while reading alarms: ${e.message}"
            )
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
package com.example.alarmwatcher

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import kotlin.math.max

internal class AlarmMonitorRunner(
    private val alarmScheduler: AlarmSchedulerApi,
    private val crashReporter: CrashReporterApi
) {
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
                val now = System.currentTimeMillis()

                if (trigger <= now) {
                    Log.i(TAG, "Skipping expired nextAlarmClock at $trigger (now=$now)")
                    alarmScheduler.cancelPreWarn(context)
                    return
                }

                val preWarnAt = trigger - AlarmScheduler.PREWARN_MS
                val scheduleAt = max(preWarnAt, now)
                val durationMs = max(trigger - scheduleAt, 1L)

                alarmScheduler.schedulePreWarn(context, scheduleAt, trigger, durationMs)
            } else {
                alarmScheduler.cancelPreWarn(context)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing permission to read alarms: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected alarm scan failure", e)
            crashReporter.reportNonFatal(
                context = context,
                throwable = e,
                source = "AlarmMonitor.scanNextAlarmAndSchedule"
            )
        }
    }

    private companion object {
        const val TAG = "AlarmMonitor"
    }
}
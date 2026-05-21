package com.example.alarmwatcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"
    private const val REQ_CODE = 5401
    private const val ACTION_PREWARN = "com.example.alarmwatcher.ACTION_PREWARN"
    const val PREWARN_MS = 30 * 60 * 1000L

    fun schedulePreWarn(context: Context, whenMs: Long, originalAlarmMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, AlarmTriggerReceiver::class.java).apply {
            action = ACTION_PREWARN
            putExtra("original_alarm_ms", originalAlarmMs)
        }
        val pi = PendingIntent.getBroadcast(context, REQ_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        try {
            val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
            if (!canScheduleExact) {
                Log.w(TAG, "App cannot schedule exact alarms: request user permission")
            }
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
            Log.i(TAG, "Scheduled pre-warn at $whenMs for alarm $originalAlarmMs")
            DiscordCrashReporter.reportDebugBlocking(
                context = context,
                source = "AlarmScheduler.schedulePreWarn",
                details = buildString {
                    appendLine("AlarmScheduler.schedulePreWarn()")
                    appendLine("canScheduleExactAlarms=$canScheduleExact")
                    appendLine("requestedAt=$whenMs")
                    appendLine("originalAlarmMs=$originalAlarmMs")
                    appendLine("delayMs=${whenMs - System.currentTimeMillis()}")
                    appendLine("sdk=${Build.VERSION.SDK_INT}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact alarm: ${e.message}")
            DiscordCrashReporter.reportDebugBlocking(
                context = context,
                source = "AlarmScheduler.schedulePreWarn",
                details = buildString {
                    appendLine("AlarmScheduler.schedulePreWarn() failed")
                    appendLine("requestedAt=$whenMs")
                    appendLine("originalAlarmMs=$originalAlarmMs")
                    appendLine("error=${e::class.java.name}")
                    appendLine("message=${e.message}")
                    appendLine("sdk=${Build.VERSION.SDK_INT}")
                }
            )
        }
    }

    fun cancelPreWarn(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, AlarmTriggerReceiver::class.java).apply { action = ACTION_PREWARN }
        val pi = PendingIntent.getBroadcast(context, REQ_CODE, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
            Log.i(TAG, "Cancelled pre-warn")
            DiscordCrashReporter.reportDebugBlocking(
                context = context,
                source = "AlarmScheduler.cancelPreWarn",
                details = "Cancelled pending pre-warn alarm"
            )
        }
    }
}

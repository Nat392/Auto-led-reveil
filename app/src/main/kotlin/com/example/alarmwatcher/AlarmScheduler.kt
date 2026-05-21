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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                Log.w(TAG, "App cannot schedule exact alarms: request user permission")
            }
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
            Log.i(TAG, "Scheduled pre-warn at $whenMs for alarm $originalAlarmMs")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact alarm: ${e.message}")
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
        }
    }
}

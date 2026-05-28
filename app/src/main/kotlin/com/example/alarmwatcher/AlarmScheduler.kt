package com.example.alarmwatcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object AlarmScheduler : AlarmSchedulerApi {
    private const val TAG = "AlarmScheduler"
    private const val REQ_CODE = 5401
    private const val ACTION_PREWARN = "com.example.alarmwatcher.ACTION_PREWARN"
    const val PREWARN_MS = DEFAULT_PREWARN_MS
    internal var intentFactory: (Context, Class<*>) -> Intent = { context, targetClass ->
        Intent(context, targetClass)
    }

    override fun schedulePreWarn(
        context: Context,
        whenMs: Long,
        originalAlarmMs: Long,
        durationMs: Long,
    ) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val intent =
            intentFactory(context, AlarmTriggerReceiver::class.java).apply {
                action = ACTION_PREWARN
                putExtra("original_alarm_ms", originalAlarmMs)
                putExtra(SunriseService.EXTRA_DURATION_MS, durationMs)
            }
        val pi =
            PendingIntent.getBroadcast(
                context,
                REQ_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        try {
            val canScheduleExact =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.canScheduleExactAlarms()
                } else {
                    true
                }
            if (!canScheduleExact) {
                Log.w(TAG, "App cannot schedule exact alarms: request user permission")
                return
            }
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact alarm: ${e.message}")
        }
    }

    override fun cancelPreWarn(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val intent =
            intentFactory(context, AlarmTriggerReceiver::class.java).apply {
                action = ACTION_PREWARN
            }
        val pi =
            PendingIntent.getBroadcast(
                context,
                REQ_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
        }
    }

    override fun stopSunriseService(context: Context) {
        val serviceIntent = Intent(context, SunriseService::class.java)
        context.stopService(serviceIntent)
    }
}

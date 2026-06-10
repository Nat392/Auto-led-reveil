package com.example.alarmwatcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object AlarmScheduler : AlarmSchedulerApi {
    private const val TAG = "AlarmScheduler"

    // --- Identifiants pour le Sunrise ---
    private const val REQ_CODE = 5401
    private const val ACTION_PREWARN = "com.example.alarmwatcher.ACTION_PREWARN"

    // --- Identifiants pour le Night Fade (un par zone) ---
    private const val REQ_CODE_NIGHT_FADE_BUREAU = 5402
    private const val REQ_CODE_NIGHT_FADE_CHAMBRE = 5403
    const val ACTION_START_NIGHT_FADE = "com.example.alarmwatcher.ACTION_START_NIGHT_FADE"
    const val EXTRA_START_TIME_MS = "extra_start_time_ms"
    const val EXTRA_END_TIME_MS = "extra_end_time_ms"
    const val EXTRA_ZONE_KEY = "extra_zone_key"

    const val PREWARN_MS = DEFAULT_PREWARN_MS

    internal var intentFactory: (Context, Class<*>) -> Intent = { context, targetClass ->
        Intent(context, targetClass)
    }

    // ==========================================
    // NOUVELLES FONCTIONS NIGHT FADE
    // ==========================================

    override fun scheduleNightFade(
        context: Context,
        zoneKey: String,
        triggerAtMs: Long,
        startTimeMs: Long,
        endTimeMs: Long,
    ) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = intentFactory(context, AlarmTriggerReceiver::class.java).apply {
            action = ACTION_START_NIGHT_FADE
            putExtra(EXTRA_ZONE_KEY, zoneKey)
            putExtra(EXTRA_START_TIME_MS, startTimeMs)
            putExtra(EXTRA_END_TIME_MS, endTimeMs)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            nightFadeRequestCode(zoneKey),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        try {
            val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.canScheduleExactAlarms()
            } else {
                true
            }
            if (!canScheduleExact) {
                Log.w(TAG, "App cannot schedule exact alarms: request user permission")
                return
            }
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            Log.i(
                TAG,
                "Night Fade ($zoneKey) programmé pour déclenchement à $triggerAtMs (Début absolu: $startTimeMs)",
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact alarm for Night Fade ($zoneKey): ${e.message}")
        }
    }

    override fun cancelNightFade(context: Context, zoneKey: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = intentFactory(context, AlarmTriggerReceiver::class.java).apply {
            action = ACTION_START_NIGHT_FADE
        }
        val pi = PendingIntent.getBroadcast(
            context,
            nightFadeRequestCode(zoneKey),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
            Log.i(TAG, "Night Fade ($zoneKey) annulé")
        }
    }

    private fun nightFadeRequestCode(zoneKey: String): Int =
        when (zoneKey) {
            SunsetAutomationScheduler.ZONE_BUREAU -> REQ_CODE_NIGHT_FADE_BUREAU
            SunsetAutomationScheduler.ZONE_CHAMBRE -> REQ_CODE_NIGHT_FADE_CHAMBRE
            else -> error("Unknown zone key: $zoneKey")
        }

    // ==========================================
    // FONCTIONS EXISTANTES (SUNRISE)
    // ==========================================

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

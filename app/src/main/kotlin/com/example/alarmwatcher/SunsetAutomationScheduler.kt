package com.example.alarmwatcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal object SunsetAutomationScheduler {
    private const val TAG = "SunsetAutomation"
    private const val SUNSET_API_URL = "https://api.sunrise-sunset.org/json?lat=46.6644&lng=5.5619&formatted=0"
    private const val REFRESH_RETRY_MS = 60 * 60 * 1000L
    private const val SUNSET_OFFSET_BUREAU_MS = 60 * 60 * 1000L
    private const val SUNSET_OFFSET_CHAMBRE_MS = 30 * 60 * 1000L
    private const val REFRESH_LOCAL_HOUR = 0
    private const val REFRESH_LOCAL_MINUTE = 5

    const val ACTION_REFRESH_SCHEDULE = "com.example.alarmwatcher.ACTION_REFRESH_SUNSET_SCHEDULE"
    const val ACTION_APPLY_SCENE = "com.example.alarmwatcher.ACTION_APPLY_SUNSET_SCENE"
    const val EXTRA_TARGET_ZONE = "extra_target_zone"

    const val ZONE_BUREAU = "bureau"
    const val ZONE_CHAMBRE = "chambre"

    private const val REQ_REFRESH = 7101
    private const val REQ_BUREAU = 7102
    private const val REQ_CHAMBRE = 7103

    private val schedulerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun requestRefreshAndSchedule(context: Context) {
        schedulerScope.launch {
            try {
                refreshAndSchedule(context.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Sunset schedule refresh failed", e)
                DiscordCrashReporter.reportNonFatal(
                    context = context.applicationContext,
                    throwable = e,
                    source = "SunsetAutomationScheduler.requestRefreshAndSchedule"
                )
            }
        }
    }

    suspend fun refreshAndSchedule(context: Context) {
        val now = System.currentTimeMillis()
        val sunsetInstant = fetchSunsetInstant() ?: run {
            scheduleRefreshRetry(context, now + REFRESH_RETRY_MS)
            return
        }

        val sunsetMs = sunsetInstant.toEpochMilli()
        val bureauMs = sunsetMs - SUNSET_OFFSET_BUREAU_MS
        val chambreMs = sunsetMs - SUNSET_OFFSET_CHAMBRE_MS

        cancelSceneAlarm(context, ZONE_BUREAU)
        cancelSceneAlarm(context, ZONE_CHAMBRE)
        cancelRefreshAlarm(context)

        scheduleSceneAlarmIfNeeded(context, ZONE_BUREAU, bureauMs)
        scheduleSceneAlarmIfNeeded(context, ZONE_CHAMBRE, chambreMs)
        scheduleRefreshAlarm(context, computeNextRefreshAtMillis())

        Log.i(TAG, "Scheduled sunset scenes: bureau=$bureauMs chambre=$chambreMs sunset=$sunsetMs")
    }

    private suspend fun fetchSunsetInstant(): Instant? {
        return try {
            val connection = (URL(SUNSET_API_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
            }

            connection.use { httpConnection ->
                val responseCode = httpConnection.responseCode
                if (responseCode !in 200..299) {
                    Log.w(TAG, "Sunset API returned HTTP $responseCode")
                    return null
                }

                val body = BufferedReader(InputStreamReader(httpConnection.inputStream)).use { reader ->
                    reader.readText()
                }
                val sunsetIso = JSONObject(body)
                    .getJSONObject("results")
                    .getString("sunset")
                Instant.parse(sunsetIso)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch sunset time", e)
            null
        }
    }

    private fun scheduleSceneAlarmIfNeeded(context: Context, zoneKey: String, whenMs: Long) {
        if (whenMs <= System.currentTimeMillis()) {
            Log.i(TAG, "Skipping past sunset scene for $zoneKey at $whenMs")
            return
        }
        scheduleExactAlarm(context, zoneKeyAlarmRequestCode(zoneKey), buildSceneIntent(context, zoneKey), whenMs)
    }

    private fun scheduleRefreshAlarm(context: Context, whenMs: Long) {
        scheduleExactAlarm(context, REQ_REFRESH, buildRefreshIntent(context), whenMs)
    }

    private fun scheduleRefreshRetry(context: Context, whenMs: Long) {
        Log.w(TAG, "Scheduling sunset refresh retry at $whenMs")
        scheduleExactAlarm(context, REQ_REFRESH, buildRefreshIntent(context), whenMs)
    }

    private fun cancelSceneAlarm(context: Context, zoneKey: String) {
        cancelExactAlarm(context, zoneKeyAlarmRequestCode(zoneKey), buildSceneIntent(context, zoneKey))
    }

    private fun cancelRefreshAlarm(context: Context) {
        cancelExactAlarm(context, REQ_REFRESH, buildRefreshIntent(context))
    }

    private fun buildSceneIntent(context: Context, zoneKey: String): Intent {
        return Intent(context, SunsetAutomationReceiver::class.java).apply {
            action = ACTION_APPLY_SCENE
            putExtra(EXTRA_TARGET_ZONE, zoneKey)
        }
    }

    private fun buildRefreshIntent(context: Context): Intent {
        return Intent(context, SunsetAutomationReceiver::class.java).apply {
            action = ACTION_REFRESH_SCHEDULE
        }
    }

    private fun scheduleExactAlarm(context: Context, requestCode: Int, intent: Intent, whenMs: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return

        try {
            val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (!canScheduleExact) {
                Log.w(TAG, "Exact alarm permission is missing; not scheduling requestCode=$requestCode")
                return
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact sunset alarm", e)
        }
    }

    private fun cancelExactAlarm(context: Context, requestCode: Int, intent: Intent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun computeNextRefreshAtMillis(zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val nextRefresh = LocalDate.now(zoneId)
            .plusDays(1)
            .atTime(LocalTime.of(REFRESH_LOCAL_HOUR, REFRESH_LOCAL_MINUTE))
            .atZone(zoneId)
        return nextRefresh.toInstant().toEpochMilli()
    }

    private fun zoneKeyAlarmRequestCode(zoneKey: String): Int {
        return when (zoneKey) {
            ZONE_BUREAU -> REQ_BUREAU
            ZONE_CHAMBRE -> REQ_CHAMBRE
            else -> error("Unknown zone key: $zoneKey")
        }
    }
}
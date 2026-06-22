package com.example.alarmwatcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.alarmwatcher.settings.AppSettings
import com.example.alarmwatcher.settings.AppSettingsCache
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
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

internal object SunsetAutomationScheduler {
    private const val TAG = "SunsetAutomation"
    private const val REFRESH_RETRY_MS = 60 * 60 * 1000L
    private const val CATCH_UP_RETRY_MS = 5 * 60 * 1000L
    private const val MILLIS_PER_MINUTE = 60_000L

    const val ACTION_REFRESH_SCHEDULE = "com.example.alarmwatcher.ACTION_REFRESH_SUNSET_SCHEDULE"
    const val ACTION_APPLY_SCENE = "com.example.alarmwatcher.ACTION_APPLY_SUNSET_SCENE"
    const val EXTRA_TARGET_ZONE = "extra_target_zone"

    const val ZONE_BUREAU = "bureau"
    const val ZONE_CHAMBRE = "chambre"
    const val ZONE_CUISINE = "cuisine"

    private const val REQ_REFRESH = 7101
    private const val REQ_BUREAU = 7102
    private const val REQ_CHAMBRE = 7103
    private const val REQ_CUISINE = 7104

    private val schedulerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal var sunsetConnectionFactory: (String) -> HttpURLConnection = { urlString ->
        URL(urlString).openConnection() as HttpURLConnection
    }
    internal var intentFactory: (Context, Class<*>) -> Intent = { context, targetClass ->
        Intent(context, targetClass)
    }

    fun requestRefreshAndSchedule(context: Context) {
        schedulerScope.launch {
            try {
                refreshAndSchedule(context.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Sunset schedule refresh failed", e)
                DiscordCrashReporter.reportNonFatal(
                    context = context.applicationContext,
                    throwable = e,
                    source = "SunsetAutomationScheduler.requestRefreshAndSchedule",
                )
            }
        }
    }

    suspend fun refreshAndSchedule(context: Context) {
        val settings = AppSettingsCache.current
        val now = System.currentTimeMillis()
        val sunsetInstant =
            fetchSunsetInstant(context, settings) ?: run {
                scheduleRefreshRetry(context, now + REFRESH_RETRY_MS)
                return
            }

        val sunsetMs = sunsetInstant.toEpochMilli()
        val bureauMs = sunsetMs - settings.bureauSunsetOffsetMinutes * MILLIS_PER_MINUTE
        val chambreMs = sunsetMs - settings.chambreSunsetOffsetMinutes * MILLIS_PER_MINUTE
        val cuisineMs = sunsetMs - settings.cuisineSunsetOffsetMinutes * MILLIS_PER_MINUTE

        SunsetTimesStore.save(context, sunsetMs, bureauMs, chambreMs, cuisineMs)

        // Nouveau cycle quotidien : la fenêtre du Daylight Harvesting de chaque zone se rouvrira
        // à la fin de la prochaine rampe d'aube de la Chambre/Cuisine.
        DaylightHarvestingStateStore.deactivate(context, ZONE_CHAMBRE)
        DaylightHarvestingStateStore.deactivate(context, ZONE_CUISINE)

        cancelSceneAlarm(context, ZONE_BUREAU)
        cancelSceneAlarm(context, ZONE_CHAMBRE)
        cancelSceneAlarm(context, ZONE_CUISINE)
        cancelRefreshAlarm(context)

        scheduleSceneAlarmIfNeeded(context, ZONE_BUREAU, bureauMs)
        scheduleSceneAlarmIfNeeded(context, ZONE_CHAMBRE, chambreMs)
        scheduleSceneAlarmIfNeeded(context, ZONE_CUISINE, cuisineMs)
        scheduleRefreshAlarm(context, computeNextRefreshAtMillis())

        // Les horaires "mode soirée" viennent de changer : recalcule la planification du mode nuit par zone
        AlarmMonitor.scanNextAlarmAndSchedule(context)

        Log.i(TAG, "Scheduled sunset scenes: bureau=$bureauMs chambre=$chambreMs cuisine=$cuisineMs sunset=$sunsetMs")
    }

    internal suspend fun fetchSunsetInstant(
        context: Context,
        settings: AppSettings = AppSettingsCache.current,
    ): Instant? {
        val todayDate = LocalDate.now(ZoneId.systemDefault()).toString()
        return try {
            val url =
                "https://api.sunrise-sunset.org/json?lat=${settings.latitude}&lng=${settings.longitude}" +
                    "&formatted=0&date=$todayDate"
            val connection =
                sunsetConnectionFactory(url).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "application/json")
                }

            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    Log.w(TAG, "Sunset API returned HTTP $responseCode")
                    DiscordCrashReporter.reportNonFatal(
                        context = context,
                        throwable = IllegalStateException("Sunset API HTTP $responseCode (date=$todayDate)"),
                        source = "SunsetAutomationScheduler.fetchSunsetInstant",
                    )
                    return null
                }

                val body =
                    BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                        reader.readText()
                    }
                val sunsetIso =
                    JSONObject(body)
                        .getJSONObject("results")
                        .getString("sunset")
                OffsetDateTime.parse(sunsetIso).toInstant()
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch sunset time", e)
            DiscordCrashReporter.reportNonFatal(
                context = context,
                throwable = e,
                source = "SunsetAutomationScheduler.fetchSunsetInstant",
            )
            null
        }
    }

    private suspend fun scheduleSceneAlarmIfNeeded(
        context: Context,
        zoneKey: String,
        whenMs: Long,
    ) {
        val now = System.currentTimeMillis()
        if (whenMs <= now) {
            val missedByMs = now - whenMs
            val maxCatchUpDelay = 1 * 60 * 60 * 1000L
            if (missedByMs <= maxCatchUpDelay) {
                Log.w(TAG, "Sunset scene for $zoneKey is already past at $whenMs; running catch-up now")
                triggerSceneCatchUp(context, zoneKey)
            } else {
                Log.w(TAG, "Sunset scene for $zoneKey passed $missedByMs ms ago, skipping catch-up as it is too late.")
            }
            return
        }
        scheduleExactAlarm(context, zoneKeyAlarmRequestCode(zoneKey), buildSceneIntent(context, zoneKey), whenMs)
    }

    private fun scheduleRefreshAlarm(
        context: Context,
        whenMs: Long,
    ) {
        scheduleExactAlarm(context, REQ_REFRESH, buildRefreshIntent(context), whenMs)
    }

    private fun scheduleRefreshRetry(
        context: Context,
        whenMs: Long,
    ) {
        Log.w(TAG, "Scheduling sunset refresh retry at $whenMs")
        scheduleExactAlarm(context, REQ_REFRESH, buildRefreshIntent(context), whenMs)
    }

    private fun cancelSceneAlarm(
        context: Context,
        zoneKey: String,
    ) {
        cancelExactAlarm(context, zoneKeyAlarmRequestCode(zoneKey), buildSceneIntent(context, zoneKey))
    }

    private fun cancelRefreshAlarm(context: Context) {
        cancelExactAlarm(context, REQ_REFRESH, buildRefreshIntent(context))
    }

    private fun buildSceneIntent(
        context: Context,
        zoneKey: String,
    ): Intent {
        return intentFactory(context, SunsetAutomationReceiver::class.java).apply {
            action = ACTION_APPLY_SCENE
            putExtra(EXTRA_TARGET_ZONE, zoneKey)
        }
    }

    private suspend fun triggerSceneCatchUp(
        context: Context,
        zoneKey: String,
    ) {
        val zone = SunsetSceneService.resolveZone(zoneKey)
        if (zone == null || !zone.isConfigured) {
            Log.w(TAG, "Cannot run sunset catch-up for $zoneKey: zone is missing or not configured")
            return
        }

        if (!BlePermissionSupport.hasBluetoothConnectPermission(context)) {
            Log.w(TAG, "Cannot run sunset catch-up for $zoneKey: BLUETOOTH_CONNECT permission missing")
            val permissionError =
                SecurityException("BLUETOOTH_CONNECT permission missing, sunset catch-up skipped (zoneKey=$zoneKey)")
            DiscordCrashReporter.reportNonFatal(
                context = context,
                throwable = permissionError,
                source = "SunsetAutomationScheduler.triggerSceneCatchUp.permission",
            )
            return
        }

        val applied = SunsetSceneService.applySunsetScene(context.applicationContext, zoneKey)
        if (!applied) {
            scheduleCatchUpRetry(context, zoneKey)
        }
    }

    private fun scheduleCatchUpRetry(
        context: Context,
        zoneKey: String,
    ) {
        val workRequest =
            OneTimeWorkRequestBuilder<SunsetCatchUpWorker>()
                .setInitialDelay(CATCH_UP_RETRY_MS, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(SunsetCatchUpWorker.KEY_ZONE_KEY to zoneKey))
                .build()

        Log.w(TAG, "Sunset catch-up for $zoneKey did not apply; retrying in $CATCH_UP_RETRY_MS ms")
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            SunsetCatchUpWorker.uniqueWorkName(zoneKey),
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
    }

    private fun buildRefreshIntent(context: Context): Intent {
        return intentFactory(context, SunsetAutomationReceiver::class.java).apply {
            action = ACTION_REFRESH_SCHEDULE
        }
    }

    private fun scheduleExactAlarm(
        context: Context,
        requestCode: Int,
        intent: Intent,
        whenMs: Long,
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return

        try {
            val canScheduleExact =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager.canScheduleExactAlarms()
                } else {
                    true
                }

            if (!canScheduleExact) {
                Log.w(TAG, "Exact alarm permission is missing; not scheduling requestCode=$requestCode")
                return
            }

            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact sunset alarm", e)
            DiscordCrashReporter.reportNonFatal(
                context = context,
                throwable = e,
                source = "SunsetAutomationScheduler.scheduleExactAlarm(requestCode=$requestCode, whenMs=$whenMs)",
            )
        }
    }

    private fun cancelExactAlarm(
        context: Context,
        requestCode: Int,
        intent: Intent,
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    internal fun computeNextRefreshAtMillis(
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        settings: AppSettings = AppSettingsCache.current,
    ): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val today = now.toLocalDate()
        val refreshTime = LocalTime.of(settings.sunsetRefreshHour, settings.sunsetRefreshMinute)
        val todayRefresh = today.atTime(refreshTime).atZone(zoneId)
        val nextRefresh =
            if (todayRefresh.isAfter(now)) {
                todayRefresh
            } else {
                today.plusDays(1).atTime(refreshTime).atZone(zoneId)
            }
        return nextRefresh.toInstant().toEpochMilli()
    }

    private fun zoneKeyAlarmRequestCode(zoneKey: String): Int {
        return when (zoneKey) {
            ZONE_BUREAU -> REQ_BUREAU
            ZONE_CHAMBRE -> REQ_CHAMBRE
            ZONE_CUISINE -> REQ_CUISINE
            else -> error("Unknown zone key: $zoneKey")
        }
    }
}

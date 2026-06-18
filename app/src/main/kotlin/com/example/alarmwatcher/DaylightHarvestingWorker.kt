package com.example.alarmwatcher

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.alarmwatcher.settings.AppSettingsCache

private const val MILLIS_PER_MINUTE = 60_000L
private const val STEP_DELAY_MS = 30_000L

/**
 * Worker périodique du Daylight Harvesting : compense en continu le déficit de lumière naturelle
 * de la Cuisine en fonction de la radiation solaire actuelle (Open-Meteo). La fréquence est pilotée
 * par [com.example.alarmwatcher.settings.AppSettings.daylightIntervalMinutes].
 */
class DaylightHarvestingWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val zone = SunriseZoneConfig.cuisine
        if (!zone.isConfigured) {
            AppErrorLogStore.record(
                context = applicationContext,
                level = AppErrorLogStore.Level.WARNING,
                title = "Zone non configurée",
                source = "DaylightHarvestingWorker.doWork",
                message = "zoneKey=${SunsetAutomationScheduler.ZONE_CUISINE}",
            )
            return Result.success()
        }

        val state = DaylightHarvestingStateStore.getState(applicationContext)
        if (!state.active) {
            return Result.success()
        }

        val eveningStartMs =
            SunsetTimesStore.getEveningStartMs(applicationContext, SunsetAutomationScheduler.ZONE_CUISINE)
                ?: Long.MAX_VALUE
        if (System.currentTimeMillis() >= eveningStartMs) {
            return Result.success()
        }

        if (!BlePermissionSupport.hasBluetoothConnectPermission(applicationContext)) {
            DiscordCrashReporter.reportNonFatal(
                context = applicationContext,
                throwable =
                    SecurityException(
                        "BLUETOOTH_CONNECT permission missing, daylight harvesting skipped " +
                            "(zoneKey=${SunsetAutomationScheduler.ZONE_CUISINE}, macAddress=${zone.macAddress})",
                    ),
                source = "DaylightHarvestingWorker.doWork",
            )
            return Result.success()
        }

        val conditions = OpenMeteoClient.fetchCurrentConditions(applicationContext) ?: return Result.retry()

        val targetRgb =
            DaylightHarvestingEstimator.calculateTargetRgb(
                conditions.shortwaveRadiation,
                Triple(zone.sunriseR, zone.sunriseG, zone.sunriseB),
            )

        if (targetRgb == state.currentRgb) {
            return Result.success()
        }

        val durationMs = AppSettingsCache.current.daylightFadeDurationMinutes * MILLIS_PER_MINUTE
        val steps = (durationMs / STEP_DELAY_MS).toInt().coerceAtLeast(1)

        DaylightFadeRunner(ZenggeBulbController, DiscordCrashReporter).fade(
            context = applicationContext,
            zone = zone,
            from = state.currentRgb,
            to = targetRgb,
            durationMs = durationMs,
            steps = steps,
        )

        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "daylight_harvesting_periodic"
    }
}

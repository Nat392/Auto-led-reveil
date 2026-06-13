package com.example.alarmwatcher

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Worker périodique (toutes les 15 min) du Daylight Harvesting : compense en continu le déficit
 * de lumière naturelle de la Cuisine en fonction de la radiation solaire actuelle (Open-Meteo).
 */
class DaylightHarvestingWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val zone = SunriseZoneConfig.cuisine
        if (!zone.isConfigured) {
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
            return Result.success()
        }

        val conditions = OpenMeteoClient.fetchCurrentConditions() ?: return Result.retry()

        val targetRgb =
            DaylightHarvestingEstimator.calculateTargetRgb(
                conditions.shortwaveRadiation,
                Triple(zone.sunriseR, zone.sunriseG, zone.sunriseB),
            )

        if (targetRgb == state.currentRgb) {
            return Result.success()
        }

        DaylightFadeRunner(ZenggeBulbController, DiscordCrashReporter).fade(
            context = applicationContext,
            zone = zone,
            from = state.currentRgb,
            to = targetRgb,
        )

        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "daylight_harvesting_periodic"
    }
}

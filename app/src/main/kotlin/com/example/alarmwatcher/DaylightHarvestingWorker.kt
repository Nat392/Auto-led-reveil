package com.example.alarmwatcher

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.alarmwatcher.settings.AppSettingsCache
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Worker périodique (toutes les 15 min) du Daylight Harvesting : compense en continu le déficit
 * de lumière naturelle de la Chambre et de la Cuisine en fonction de la radiation solaire actuelle
 * (Open-Meteo). Chaque zone a son propre seuil de saturation, réglable indépendamment dans
 * Réglages ([com.example.alarmwatcher.settings.AppSettings.chambreSolarThresholdWm2] /
 * [com.example.alarmwatcher.settings.AppSettings.cuisineSolarThresholdWm2]) : la Cuisine (baie
 * vitrée large) sature à une radiation bien plus faible que la Chambre (fenêtre plus petite), qui
 * laisse entrer beaucoup moins de lumière naturelle.
 */
class DaylightHarvestingWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        if (!BlePermissionSupport.hasBluetoothConnectPermission(applicationContext)) {
            return Result.success()
        }

        val dueZoneKeys = HARVEST_ZONE_KEYS.filter { isDue(it) }
        if (dueZoneKeys.isEmpty()) {
            return Result.success()
        }

        val conditions = OpenMeteoClient.fetchCurrentConditions(applicationContext) ?: return Result.retry()
        SolarConditionsStore.save(applicationContext, conditions.shortwaveRadiation, System.currentTimeMillis())

        coroutineScope {
            dueZoneKeys.forEach { zoneKey ->
                launch { applyHarvesting(zoneKey, conditions.shortwaveRadiation) }
            }
        }

        return Result.success()
    }

    private fun isDue(zoneKey: String): Boolean {
        val zone = SunsetSceneService.resolveZone(zoneKey)
        val eveningStartMs =
            SunsetTimesStore.getEveningStartMs(applicationContext, zoneKey) ?: Long.MAX_VALUE
        return zone != null &&
            zone.isConfigured &&
            DaylightHarvestingStateStore.getState(applicationContext, zoneKey).active &&
            System.currentTimeMillis() < eveningStartMs
    }

    private suspend fun applyHarvesting(
        zoneKey: String,
        shortwaveRadiation: Double,
    ) {
        val zone = SunsetSceneService.resolveZone(zoneKey) ?: return
        val state = DaylightHarvestingStateStore.getState(applicationContext, zoneKey)
        val saturationRadiationWm2 = saturationThresholdWm2(zoneKey)

        val targetRgb =
            DaylightHarvestingEstimator.calculateTargetRgb(
                shortwaveRadiation,
                Triple(zone.sunriseR, zone.sunriseG, zone.sunriseB),
                saturationRadiationWm2,
            )

        if (targetRgb == state.currentRgb) {
            return
        }

        DaylightFadeRunner(ZenggeBulbController, DiscordCrashReporter).fade(
            context = applicationContext,
            fadeZone = FadeZone(key = zoneKey, bulb = zone),
            transition = ColorTransition(from = state.currentRgb, to = targetRgb),
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "daylight_harvesting_periodic"

        private val HARVEST_ZONE_KEYS =
            listOf(SunsetAutomationScheduler.ZONE_CHAMBRE, SunsetAutomationScheduler.ZONE_CUISINE)

        internal fun saturationThresholdWm2(zoneKey: String): Double {
            val settings = AppSettingsCache.current
            return when (zoneKey) {
                SunsetAutomationScheduler.ZONE_CHAMBRE -> settings.chambreSolarThresholdWm2
                SunsetAutomationScheduler.ZONE_CUISINE -> settings.cuisineSolarThresholdWm2
                else -> error("Unknown harvest zone key: $zoneKey")
            }
        }
    }
}

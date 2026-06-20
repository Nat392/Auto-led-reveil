package com.example.alarmwatcher

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.alarmwatcher.settings.AppSettings
import com.example.alarmwatcher.settings.AppSettingsCache
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Worker périodique (toutes les 15 min) du Daylight Harvesting : compense en continu le déficit
 * de lumière naturelle de la Chambre et de la Cuisine en fonction de la radiation solaire actuelle
 * (Open-Meteo). Chaque zone a son propre seuil de saturation, calibré sur la taille de sa fenêtre
 * ([HARVEST_ZONES]) : la Cuisine (baie vitrée ~200-225 cm) sature à une radiation bien plus faible
 * que la Chambre (fenêtre ~74 cm), qui laisse entrer beaucoup moins de lumière naturelle. Ce
 * calibrage par zone est exprimé en proportion du réglage utilisateur [AppSettings.solarSaturationThresholdWm2]
 * (par défaut 600 W/m²), qui reste donc le levier réel pour ajuster les deux zones à la fois.
 */
class DaylightHarvestingWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        if (!BlePermissionSupport.hasBluetoothConnectPermission(applicationContext)) {
            return Result.success()
        }

        val dueZones = HARVEST_ZONES.filter { isDue(it) }
        if (dueZones.isEmpty()) {
            return Result.success()
        }

        val conditions = OpenMeteoClient.fetchCurrentConditions(applicationContext) ?: return Result.retry()

        coroutineScope {
            dueZones.forEach { harvestZone ->
                launch { applyHarvesting(harvestZone, conditions.shortwaveRadiation) }
            }
        }

        return Result.success()
    }

    private fun isDue(harvestZone: HarvestZone): Boolean {
        val zone = SunsetSceneService.resolveZone(harvestZone.zoneKey)
        val eveningStartMs =
            SunsetTimesStore.getEveningStartMs(applicationContext, harvestZone.zoneKey) ?: Long.MAX_VALUE
        return zone != null &&
            zone.isConfigured &&
            DaylightHarvestingStateStore.getState(applicationContext, harvestZone.zoneKey).active &&
            System.currentTimeMillis() < eveningStartMs
    }

    private suspend fun applyHarvesting(
        harvestZone: HarvestZone,
        shortwaveRadiation: Double,
    ) {
        val zone = SunsetSceneService.resolveZone(harvestZone.zoneKey) ?: return
        val state = DaylightHarvestingStateStore.getState(applicationContext, harvestZone.zoneKey)
        val saturationRadiationWm2 =
            harvestZone.calibrationRatio * AppSettingsCache.current.solarSaturationThresholdWm2

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
            fadeZone = FadeZone(key = harvestZone.zoneKey, bulb = zone),
            transition = ColorTransition(from = state.currentRgb, to = targetRgb),
        )
    }

    private data class HarvestZone(
        val zoneKey: String,
        val calibrationRatio: Double,
    )

    companion object {
        const val UNIQUE_WORK_NAME = "daylight_harvesting_periodic"

        // Ratios calibrés proportionnellement à la largeur de vitrage de chaque zone (largeur
        // de référence 100 cm ↔ 300 W/m²), exprimés par rapport au réglage utilisateur par défaut
        // (600 W/m²) : Chambre ~74 cm → ~400 W/m², Cuisine ~212 cm → ~150 W/m².
        private val HARVEST_ZONES =
            listOf(
                HarvestZone(
                    SunsetAutomationScheduler.ZONE_CHAMBRE,
                    calibrationRatio = 400.0 / AppSettings.DEFAULT_SOLAR_SATURATION_THRESHOLD_WM2,
                ),
                HarvestZone(
                    SunsetAutomationScheduler.ZONE_CUISINE,
                    calibrationRatio = 150.0 / AppSettings.DEFAULT_SOLAR_SATURATION_THRESHOLD_WM2,
                ),
            )
    }
}

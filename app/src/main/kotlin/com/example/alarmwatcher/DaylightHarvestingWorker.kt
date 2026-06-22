package com.example.alarmwatcher

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.alarmwatcher.settings.AppSettingsCache

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
        // La mesure de radiation est rafraîchie à chaque tick, indépendamment de l'état "active"
        // des zones, pour que le Dashboard affiche toujours une valeur récente — seule
        // l'application du harvesting reste conditionnée aux zones actives/équipées en BLE.
        val conditions = OpenMeteoClient.fetchCurrentConditions(applicationContext) ?: return Result.retry()
        SolarConditionsStore.save(applicationContext, conditions.shortwaveRadiation, System.currentTimeMillis())

        if (!BlePermissionSupport.hasBluetoothConnectPermission(applicationContext)) {
            return Result.success()
        }

        val dueZoneKeys = HARVEST_ZONE_KEYS.filter { isDue(it) }
        if (dueZoneKeys.isEmpty()) {
            return Result.success()
        }

        // Le fondu (jusqu'à 14 min, voir DaylightFadeRunner) tourne dans un foreground service
        // plutôt qu'ici : un Worker WorkManager est tué par le système après ~10 min d'exécution
        // en arrière-plan, ce qui interromprait le fondu et décalerait le cycle suivant.
        DaylightHarvestingFadeService.start(applicationContext, dueZoneKeys, conditions.shortwaveRadiation)

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

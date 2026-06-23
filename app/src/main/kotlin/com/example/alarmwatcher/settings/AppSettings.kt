@file:Suppress("MagicNumber")

package com.example.alarmwatcher.settings

/**
 * Couleur cible RGB (0-255) appliquée aux ampoules pour une scène donnée (aube ou soirée).
 */
data class RgbColor(
    val red: Int,
    val green: Int,
    val blue: Int,
)

/**
 * Réglages utilisateur persistés (Catégorie A de l'audit). Les valeurs par défaut reproduisent
 * le comportement codé en dur de l'application avant l'introduction de cet écran de réglages.
 */
data class AppSettings(
    val preWarnMinutes: Int = DEFAULT_PREWARN_MINUTES,
    val bureauSunriseColor: RgbColor = DEFAULT_BUREAU_SUNRISE_COLOR,
    val bureauSunsetColor: RgbColor = DEFAULT_BUREAU_SUNSET_COLOR,
    val chambreSunriseColor: RgbColor = DEFAULT_CHAMBRE_SUNRISE_COLOR,
    val chambreSunsetColor: RgbColor = DEFAULT_CHAMBRE_SUNSET_COLOR,
    val cuisineSunriseColor: RgbColor = DEFAULT_CUISINE_SUNRISE_COLOR,
    val cuisineSunsetColor: RgbColor = DEFAULT_CUISINE_SUNSET_COLOR,
    val bureauSunsetOffsetMinutes: Int = DEFAULT_BUREAU_SUNSET_OFFSET_MINUTES,
    val chambreSunsetOffsetMinutes: Int = DEFAULT_CHAMBRE_SUNSET_OFFSET_MINUTES,
    val cuisineSunsetOffsetMinutes: Int = DEFAULT_CUISINE_SUNSET_OFFSET_MINUTES,
    val snoozeToleranceMinutes: Int = DEFAULT_SNOOZE_TOLERANCE_MINUTES,
    val nightFadeLeadTimeMinutes: Int = DEFAULT_NIGHT_FADE_LEAD_TIME_MINUTES,
    val nightFadeMorningStartMinutes: Int = DEFAULT_NIGHT_FADE_MORNING_START_MINUTES,
    val nightFadeMorningEndMinutes: Int = DEFAULT_NIGHT_FADE_MORNING_END_MINUTES,
    val daylightIntervalMinutes: Int = DEFAULT_DAYLIGHT_INTERVAL_MINUTES,
    val daylightFadeDurationMinutes: Int = DEFAULT_DAYLIGHT_FADE_DURATION_MINUTES,
    val chambreSolarThresholdWm2: Double = DEFAULT_CHAMBRE_SOLAR_THRESHOLD_WM2,
    val cuisineSolarThresholdWm2: Double = DEFAULT_CUISINE_SOLAR_THRESHOLD_WM2,
    val latitude: Double = DEFAULT_LATITUDE,
    val longitude: Double = DEFAULT_LONGITUDE,
    val sunsetRefreshHour: Int = DEFAULT_SUNSET_REFRESH_HOUR,
    val sunsetRefreshMinute: Int = DEFAULT_SUNSET_REFRESH_MINUTE,
    val bureauMacAddress: String = "",
    val chambreMacAddress: String = "",
    val cuisineMacAddress: String = "",
    val sunsetSceneRetryAttempts: Int = DEFAULT_SUNSET_SCENE_RETRY_ATTEMPTS,
    val sunsetSceneRetryDelaySeconds: Double = DEFAULT_SUNSET_SCENE_RETRY_DELAY_SECONDS,
    val bleConnectTimeoutSeconds: Int = DEFAULT_BLE_CONNECT_TIMEOUT_SECONDS,
    val bleOperationTimeoutSeconds: Int = DEFAULT_BLE_OPERATION_TIMEOUT_SECONDS,
) {
    companion object {
        const val DEFAULT_PREWARN_MINUTES = 30

        val DEFAULT_BUREAU_SUNRISE_COLOR = RgbColor(220, 240, 255)
        val DEFAULT_BUREAU_SUNSET_COLOR = RgbColor(255, 140, 0)
        val DEFAULT_CHAMBRE_SUNRISE_COLOR = RgbColor(255, 230, 210)
        val DEFAULT_CHAMBRE_SUNSET_COLOR = RgbColor(255, 71, 0)
        val DEFAULT_CUISINE_SUNRISE_COLOR = RgbColor(220, 240, 255)
        val DEFAULT_CUISINE_SUNSET_COLOR = RgbColor(255, 140, 0)

        const val DEFAULT_BUREAU_SUNSET_OFFSET_MINUTES = 60
        const val DEFAULT_CHAMBRE_SUNSET_OFFSET_MINUTES = 30
        const val DEFAULT_CUISINE_SUNSET_OFFSET_MINUTES = 60

        const val DEFAULT_SNOOZE_TOLERANCE_MINUTES = 30

        const val DEFAULT_NIGHT_FADE_LEAD_TIME_MINUTES = 8 * 60 + 35
        const val DEFAULT_NIGHT_FADE_MORNING_START_MINUTES = 7 * 60
        const val DEFAULT_NIGHT_FADE_MORNING_END_MINUTES = 9 * 60 + 30

        const val DEFAULT_DAYLIGHT_INTERVAL_MINUTES = 15
        const val DEFAULT_DAYLIGHT_FADE_DURATION_MINUTES = 14

        // Seuils par zone, calibrés sur la largeur de vitrage de chacune (Chambre ~74 cm,
        // Cuisine ~212 cm) : reproduisent le comportement précédent (ratio caché appliqué au
        // réglage global) mais sont désormais réglables indépendamment.
        const val DEFAULT_CHAMBRE_SOLAR_THRESHOLD_WM2 = 400.0
        const val DEFAULT_CUISINE_SOLAR_THRESHOLD_WM2 = 150.0

        const val DEFAULT_LATITUDE = 46.6644
        const val DEFAULT_LONGITUDE = 5.5619

        const val DEFAULT_SUNSET_REFRESH_HOUR = 0
        const val DEFAULT_SUNSET_REFRESH_MINUTE = 5

        const val DEFAULT_SUNSET_SCENE_RETRY_ATTEMPTS = 3
        const val DEFAULT_SUNSET_SCENE_RETRY_DELAY_SECONDS = 1.5
        const val DEFAULT_BLE_CONNECT_TIMEOUT_SECONDS = 12
        const val DEFAULT_BLE_OPERATION_TIMEOUT_SECONDS = 5
    }
}

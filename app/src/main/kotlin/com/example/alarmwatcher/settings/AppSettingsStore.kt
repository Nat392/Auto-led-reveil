package com.example.alarmwatcher.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persiste les réglages utilisateur (Catégorie A de l'audit) via DataStore Preferences.
 */
internal class AppSettingsStore(
    private val dataStore: DataStore<Preferences>,
) {
    val settingsFlow: Flow<AppSettings> = dataStore.data.map { it.toAppSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { preferences ->
            val updated = transform(preferences.toAppSettings())
            preferences.writeAppSettings(updated)
        }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            preWarnMinutes = this[Keys.preWarnMinutes, d.preWarnMinutes],
            bureauSunriseColor = readColor(Keys.bureauSunrise, d.bureauSunriseColor),
            bureauSunsetColor = readColor(Keys.bureauSunset, d.bureauSunsetColor),
            chambreSunriseColor = readColor(Keys.chambreSunrise, d.chambreSunriseColor),
            chambreSunsetColor = readColor(Keys.chambreSunset, d.chambreSunsetColor),
            cuisineSunriseColor = readColor(Keys.cuisineSunrise, d.cuisineSunriseColor),
            cuisineSunsetColor = readColor(Keys.cuisineSunset, d.cuisineSunsetColor),
            bureauSunsetOffsetMinutes = this[Keys.bureauSunsetOffsetMinutes, d.bureauSunsetOffsetMinutes],
            chambreSunsetOffsetMinutes = this[Keys.chambreSunsetOffsetMinutes, d.chambreSunsetOffsetMinutes],
            cuisineSunsetOffsetMinutes = this[Keys.cuisineSunsetOffsetMinutes, d.cuisineSunsetOffsetMinutes],
            snoozeToleranceMinutes = this[Keys.snoozeToleranceMinutes, d.snoozeToleranceMinutes],
            nightFadeLeadTimeMinutes = this[Keys.nightFadeLeadTimeMinutes, d.nightFadeLeadTimeMinutes],
            nightFadeMorningStartMinutes = this[Keys.nightFadeMorningStartMinutes, d.nightFadeMorningStartMinutes],
            nightFadeMorningEndMinutes = this[Keys.nightFadeMorningEndMinutes, d.nightFadeMorningEndMinutes],
            daylightIntervalMinutes = this[Keys.daylightIntervalMinutes, d.daylightIntervalMinutes],
            daylightFadeDurationMinutes = this[Keys.daylightFadeDurationMinutes, d.daylightFadeDurationMinutes],
            chambreSolarThresholdWm2 = this[Keys.chambreSolarThresholdWm2, d.chambreSolarThresholdWm2],
            cuisineSolarThresholdWm2 = this[Keys.cuisineSolarThresholdWm2, d.cuisineSolarThresholdWm2],
            latitude = this[Keys.latitude, d.latitude],
            longitude = this[Keys.longitude, d.longitude],
            sunsetRefreshHour = this[Keys.sunsetRefreshHour, d.sunsetRefreshHour],
            sunsetRefreshMinute = this[Keys.sunsetRefreshMinute, d.sunsetRefreshMinute],
            bureauMacAddress = this[Keys.bureauMacAddress, d.bureauMacAddress],
            chambreMacAddress = this[Keys.chambreMacAddress, d.chambreMacAddress],
            cuisineMacAddress = this[Keys.cuisineMacAddress, d.cuisineMacAddress],
            sunsetSceneRetryAttempts = this[Keys.sunsetSceneRetryAttempts, d.sunsetSceneRetryAttempts],
            sunsetSceneRetryDelaySeconds = this[Keys.sunsetSceneRetryDelaySeconds, d.sunsetSceneRetryDelaySeconds],
            bleConnectTimeoutSeconds = this[Keys.bleConnectTimeoutSeconds, d.bleConnectTimeoutSeconds],
            bleOperationTimeoutSeconds = this[Keys.bleOperationTimeoutSeconds, d.bleOperationTimeoutSeconds],
        )
    }

    private operator fun <T> Preferences.get(
        key: Preferences.Key<T>,
        default: T,
    ): T = this[key] ?: default

    private fun Preferences.readColor(
        keys: ColorKeys,
        default: RgbColor,
    ): RgbColor =
        RgbColor(
            red = this[keys.red, default.red],
            green = this[keys.green, default.green],
            blue = this[keys.blue, default.blue],
        )

    private fun MutablePreferences.writeAppSettings(settings: AppSettings) {
        this[Keys.preWarnMinutes] = settings.preWarnMinutes
        writeColor(Keys.bureauSunrise, settings.bureauSunriseColor)
        writeColor(Keys.bureauSunset, settings.bureauSunsetColor)
        writeColor(Keys.chambreSunrise, settings.chambreSunriseColor)
        writeColor(Keys.chambreSunset, settings.chambreSunsetColor)
        writeColor(Keys.cuisineSunrise, settings.cuisineSunriseColor)
        writeColor(Keys.cuisineSunset, settings.cuisineSunsetColor)
        this[Keys.bureauSunsetOffsetMinutes] = settings.bureauSunsetOffsetMinutes
        this[Keys.chambreSunsetOffsetMinutes] = settings.chambreSunsetOffsetMinutes
        this[Keys.cuisineSunsetOffsetMinutes] = settings.cuisineSunsetOffsetMinutes
        this[Keys.snoozeToleranceMinutes] = settings.snoozeToleranceMinutes
        this[Keys.nightFadeLeadTimeMinutes] = settings.nightFadeLeadTimeMinutes
        this[Keys.nightFadeMorningStartMinutes] = settings.nightFadeMorningStartMinutes
        this[Keys.nightFadeMorningEndMinutes] = settings.nightFadeMorningEndMinutes
        this[Keys.daylightIntervalMinutes] = settings.daylightIntervalMinutes
        this[Keys.daylightFadeDurationMinutes] = settings.daylightFadeDurationMinutes
        this[Keys.chambreSolarThresholdWm2] = settings.chambreSolarThresholdWm2
        this[Keys.cuisineSolarThresholdWm2] = settings.cuisineSolarThresholdWm2
        this[Keys.latitude] = settings.latitude
        this[Keys.longitude] = settings.longitude
        this[Keys.sunsetRefreshHour] = settings.sunsetRefreshHour
        this[Keys.sunsetRefreshMinute] = settings.sunsetRefreshMinute
        this[Keys.bureauMacAddress] = settings.bureauMacAddress
        this[Keys.chambreMacAddress] = settings.chambreMacAddress
        this[Keys.cuisineMacAddress] = settings.cuisineMacAddress
        this[Keys.sunsetSceneRetryAttempts] = settings.sunsetSceneRetryAttempts
        this[Keys.sunsetSceneRetryDelaySeconds] = settings.sunsetSceneRetryDelaySeconds
        this[Keys.bleConnectTimeoutSeconds] = settings.bleConnectTimeoutSeconds
        this[Keys.bleOperationTimeoutSeconds] = settings.bleOperationTimeoutSeconds
    }

    private fun MutablePreferences.writeColor(
        keys: ColorKeys,
        color: RgbColor,
    ) {
        this[keys.red] = color.red
        this[keys.green] = color.green
        this[keys.blue] = color.blue
    }

    private class ColorKeys(prefix: String) {
        val red = intPreferencesKey("${prefix}_r")
        val green = intPreferencesKey("${prefix}_g")
        val blue = intPreferencesKey("${prefix}_b")
    }

    private object Keys {
        val preWarnMinutes = intPreferencesKey("prewarn_minutes")

        val bureauSunrise = ColorKeys("bureau_sunrise")
        val bureauSunset = ColorKeys("bureau_sunset")
        val chambreSunrise = ColorKeys("chambre_sunrise")
        val chambreSunset = ColorKeys("chambre_sunset")
        val cuisineSunrise = ColorKeys("cuisine_sunrise")
        val cuisineSunset = ColorKeys("cuisine_sunset")

        val bureauSunsetOffsetMinutes = intPreferencesKey("bureau_sunset_offset_minutes")
        val chambreSunsetOffsetMinutes = intPreferencesKey("chambre_sunset_offset_minutes")
        val cuisineSunsetOffsetMinutes = intPreferencesKey("cuisine_sunset_offset_minutes")

        val snoozeToleranceMinutes = intPreferencesKey("snooze_tolerance_minutes")

        val nightFadeLeadTimeMinutes = intPreferencesKey("night_fade_lead_time_minutes")
        val nightFadeMorningStartMinutes = intPreferencesKey("night_fade_morning_start_minutes")
        val nightFadeMorningEndMinutes = intPreferencesKey("night_fade_morning_end_minutes")

        val daylightIntervalMinutes = intPreferencesKey("daylight_interval_minutes")
        val daylightFadeDurationMinutes = intPreferencesKey("daylight_fade_duration_minutes")
        val chambreSolarThresholdWm2 = doublePreferencesKey("chambre_solar_threshold_wm2")
        val cuisineSolarThresholdWm2 = doublePreferencesKey("cuisine_solar_threshold_wm2")

        val latitude = doublePreferencesKey("latitude")
        val longitude = doublePreferencesKey("longitude")

        val sunsetRefreshHour = intPreferencesKey("sunset_refresh_hour")
        val sunsetRefreshMinute = intPreferencesKey("sunset_refresh_minute")

        val bureauMacAddress = stringPreferencesKey("bureau_mac_address")
        val chambreMacAddress = stringPreferencesKey("chambre_mac_address")
        val cuisineMacAddress = stringPreferencesKey("cuisine_mac_address")

        val sunsetSceneRetryAttempts = intPreferencesKey("sunset_scene_retry_attempts")
        val sunsetSceneRetryDelaySeconds = doublePreferencesKey("sunset_scene_retry_delay_seconds")
        val bleConnectTimeoutSeconds = intPreferencesKey("ble_connect_timeout_seconds")
        val bleOperationTimeoutSeconds = intPreferencesKey("ble_operation_timeout_seconds")
    }

    companion object {
        private const val DATA_STORE_FILE_NAME = "app_settings.preferences_pb"

        @Volatile
        private var instance: DataStore<Preferences>? = null

        // DataStore lève une exception si plusieurs instances ouvrent le même fichier : on
        // partage donc une unique instance entre AppSettingsCache et SettingsViewModel.
        fun create(context: Context): AppSettingsStore {
            val dataStore =
                instance ?: synchronized(this) {
                    instance ?: PreferenceDataStoreFactory.create(
                        produceFile = { context.applicationContext.preferencesDataStoreFile(DATA_STORE_FILE_NAME) },
                    ).also { instance = it }
                }
            return AppSettingsStore(dataStore)
        }
    }
}

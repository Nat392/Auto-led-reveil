package com.example.alarmwatcher

import androidx.annotation.VisibleForTesting
import com.example.alarmwatcher.settings.AppSettingsCache

internal data class SunriseBulbZone(
    val label: String,
    val macAddress: String,
    val sunriseR: Int,
    val sunriseG: Int,
    val sunriseB: Int,
    val sunsetR: Int,
    val sunsetG: Int,
    val sunsetB: Int,
    val whiteChannel: Int = 0,
    val brightnessPercent: Int = 100,
) {
    val isConfigured: Boolean
        get() = macAddress.isNotBlank()
}

internal object SunriseZoneConfig {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var testZones: List<SunriseBulbZone>? = null

    val bureau: SunriseBulbZone
        get() {
            val settings = AppSettingsCache.current
            return SunriseBulbZone(
                label = "Bureau",
                macAddress = settings.bureauMacAddress.trim().ifBlank { BuildConfig.ZENGGE_BULB_MAC_BUREAU.trim() },
                sunriseR = settings.bureauSunriseColor.red,
                sunriseG = settings.bureauSunriseColor.green,
                sunriseB = settings.bureauSunriseColor.blue,
                sunsetR = settings.bureauSunsetColor.red,
                sunsetG = settings.bureauSunsetColor.green,
                sunsetB = settings.bureauSunsetColor.blue,
                whiteChannel = 0,
                brightnessPercent = 100,
            )
        }

    val chambre: SunriseBulbZone
        get() {
            val settings = AppSettingsCache.current
            return SunriseBulbZone(
                label = "Chambre",
                macAddress = settings.chambreMacAddress.trim().ifBlank { BuildConfig.ZENGGE_BULB_MAC_CHAMBRE.trim() },
                sunriseR = settings.chambreSunriseColor.red,
                sunriseG = settings.chambreSunriseColor.green,
                sunriseB = settings.chambreSunriseColor.blue,
                sunsetR = settings.chambreSunsetColor.red,
                sunsetG = settings.chambreSunsetColor.green,
                sunsetB = settings.chambreSunsetColor.blue,
                whiteChannel = 0,
                brightnessPercent = 100,
            )
        }

    val cuisine: SunriseBulbZone
        get() {
            val settings = AppSettingsCache.current
            return SunriseBulbZone(
                label = "Cuisine",
                macAddress = settings.cuisineMacAddress.trim().ifBlank { BuildConfig.ZENGGE_BULB_MAC_CUISINE.trim() },
                sunriseR = settings.cuisineSunriseColor.red,
                sunriseG = settings.cuisineSunriseColor.green,
                sunriseB = settings.cuisineSunriseColor.blue,
                sunsetR = settings.cuisineSunsetColor.red,
                sunsetG = settings.cuisineSunsetColor.green,
                sunsetB = settings.cuisineSunsetColor.blue,
                whiteChannel = 0,
                brightnessPercent = 100,
            )
        }

    fun all(): List<SunriseBulbZone> = testZones ?: listOf(bureau, chambre, cuisine)

    fun configuredZones(): List<SunriseBulbZone> = all().filter { it.isConfigured }

    fun primaryZone(): SunriseBulbZone = configuredZones().firstOrNull() ?: bureau
}

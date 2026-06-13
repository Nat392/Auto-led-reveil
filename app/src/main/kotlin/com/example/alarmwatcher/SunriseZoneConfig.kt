package com.example.alarmwatcher

import androidx.annotation.VisibleForTesting

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
        get() =
            SunriseBulbZone(
                label = "Bureau",
                macAddress = BuildConfig.ZENGGE_BULB_MAC_BUREAU.trim(),
                sunriseR = 220,
                sunriseG = 240,
                sunriseB = 255,
                sunsetR = 255,
                sunsetG = 140,
                sunsetB = 0,
                whiteChannel = 0,
                brightnessPercent = 100,
            )

    val chambre: SunriseBulbZone
        get() =
            SunriseBulbZone(
                label = "Chambre",
                macAddress = BuildConfig.ZENGGE_BULB_MAC_CHAMBRE.trim(),
                sunriseR = 255,
                sunriseG = 230,
                sunriseB = 210,
                sunsetR = 255,
                sunsetG = 71,
                sunsetB = 0,
                whiteChannel = 0,
                brightnessPercent = 100,
            )

    val cuisine: SunriseBulbZone
        get() =
            SunriseBulbZone(
                label = "Cuisine",
                macAddress = BuildConfig.ZENGGE_BULB_MAC_CUISINE.trim(),
                sunriseR = 220,
                sunriseG = 240,
                sunriseB = 255,
                sunsetR = 255,
                sunsetG = 140,
                sunsetB = 0,
                whiteChannel = 0,
                brightnessPercent = 100,
            )

    fun all(): List<SunriseBulbZone> = testZones ?: listOf(bureau, chambre, cuisine)

    fun configuredZones(): List<SunriseBulbZone> = all().filter { it.isConfigured }

    fun primaryZone(): SunriseBulbZone = configuredZones().firstOrNull() ?: bureau
}

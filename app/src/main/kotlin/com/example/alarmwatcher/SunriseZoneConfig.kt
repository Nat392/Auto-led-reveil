package com.example.alarmwatcher

internal data class SunriseBulbZone(
    val label: String,
    val macAddress: String,
    val targetR: Int,
    val targetG: Int,
    val targetB: Int
) {
    val isConfigured: Boolean
        get() = macAddress.isNotBlank()
}

internal object SunriseZoneConfig {
    val bureau: SunriseBulbZone
        get() = SunriseBulbZone(
            label = "Bureau",
            macAddress = BuildConfig.ZENGGE_BULB_MAC_BUREAU.trim(),
            targetR = 220,
            targetG = 240,
            targetB = 255
        )

    val chambre: SunriseBulbZone
        get() = SunriseBulbZone(
            label = "Chambre",
            macAddress = BuildConfig.ZENGGE_BULB_MAC_CHAMBRE.trim(),
            targetR = 255,
            targetG = 230,
            targetB = 210
        )

    fun all(): List<SunriseBulbZone> = listOf(bureau, chambre)

    fun configuredZones(): List<SunriseBulbZone> = all().filter { it.isConfigured }

    fun primaryZone(): SunriseBulbZone = configuredZones().firstOrNull() ?: bureau
}
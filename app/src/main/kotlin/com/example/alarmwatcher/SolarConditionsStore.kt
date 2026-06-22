package com.example.alarmwatcher

import android.content.Context

/**
 * Persiste la dernière radiation solaire mesurée (Open-Meteo, via [OpenMeteoClient]) afin que
 * l'utilisateur puisse la consulter sur le Dashboard — cette valeur était auparavant calculée par
 * [DaylightHarvestingWorker] puis aussitôt jetée.
 */
internal object SolarConditionsStore {
    private const val PREFS_NAME = "solar_conditions"
    private const val KEY_SHORTWAVE_RADIATION_WM2 = "shortwave_radiation_wm2"
    private const val KEY_READ_AT_MS = "read_at_ms"

    data class Reading(
        val shortwaveRadiationWm2: Double,
        val readAtMs: Long,
    )

    fun save(
        context: Context,
        shortwaveRadiationWm2: Double,
        readAtMs: Long,
    ) {
        prefs(context)
            .edit()
            .putFloat(KEY_SHORTWAVE_RADIATION_WM2, shortwaveRadiationWm2.toFloat())
            .putLong(KEY_READ_AT_MS, readAtMs)
            .apply()
    }

    fun get(context: Context): Reading? {
        val prefs = prefs(context)
        val readAtMs = prefs.getLong(KEY_READ_AT_MS, -1L)
        if (readAtMs <= 0L) return null
        return Reading(
            shortwaveRadiationWm2 = prefs.getFloat(KEY_SHORTWAVE_RADIATION_WM2, 0f).toDouble(),
            readAtMs = readAtMs,
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )
}

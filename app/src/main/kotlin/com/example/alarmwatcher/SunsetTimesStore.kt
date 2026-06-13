package com.example.alarmwatcher

import android.content.Context

/**
 * Persiste les horaires "mode soirée" calculés par [SunsetAutomationScheduler] (coucher du
 * soleil moins l'offset de chaque zone), afin que [NightFadeTimingSupport] puisse aligner le
 * début du mode nuit sur le déclenchement réel du mode soirée de chaque zone.
 */
internal object SunsetTimesStore {
    private const val PREFS_NAME = "sunset_times"
    private const val KEY_BUREAU_EVENING_START_MS = "bureau_evening_start_ms"
    private const val KEY_CHAMBRE_EVENING_START_MS = "chambre_evening_start_ms"
    private const val KEY_CUISINE_EVENING_START_MS = "cuisine_evening_start_ms"

    fun save(
        context: Context,
        bureauEveningStartMs: Long,
        chambreEveningStartMs: Long,
        cuisineEveningStartMs: Long,
    ) {
        prefs(context)
            .edit()
            .putLong(KEY_BUREAU_EVENING_START_MS, bureauEveningStartMs)
            .putLong(KEY_CHAMBRE_EVENING_START_MS, chambreEveningStartMs)
            .putLong(KEY_CUISINE_EVENING_START_MS, cuisineEveningStartMs)
            .apply()
    }

    fun getEveningStartMs(
        context: Context,
        zoneKey: String,
    ): Long? {
        val key =
            when (zoneKey) {
                SunsetAutomationScheduler.ZONE_BUREAU -> KEY_BUREAU_EVENING_START_MS
                SunsetAutomationScheduler.ZONE_CHAMBRE -> KEY_CHAMBRE_EVENING_START_MS
                SunsetAutomationScheduler.ZONE_CUISINE -> KEY_CUISINE_EVENING_START_MS
                else -> return null
            }
        val value = prefs(context).getLong(key, -1L)
        return value.takeIf { it > 0L }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )
}

package com.example.alarmwatcher

import android.content.Context

/**
 * Persiste l'état du Daylight Harvesting pour chaque zone (Chambre, Cuisine) indépendamment : la
 * "fenêtre" pendant laquelle le [DaylightHarvestingWorker] est autorisé à agir ([State.active]),
 * et le vecteur RGB actuellement affiché par ce sous-système ([State.currentRgb]), point de départ
 * du prochain fondu.
 */
internal object DaylightHarvestingStateStore {
    private const val PREFS_NAME_PREFIX = "daylight_harvesting_"
    private const val KEY_ACTIVE = "active"
    private const val KEY_CURRENT_R = "current_r"
    private const val KEY_CURRENT_G = "current_g"
    private const val KEY_CURRENT_B = "current_b"

    private val DEFAULT_RGB = Triple(220, 240, 255)

    data class State(
        val active: Boolean,
        val currentRgb: Triple<Int, Int, Int>,
    )

    fun getState(
        context: Context,
        zoneKey: String,
    ): State {
        val prefs = prefs(context, zoneKey)
        val active = prefs.getBoolean(KEY_ACTIVE, false)
        val currentRgb =
            Triple(
                prefs.getInt(KEY_CURRENT_R, DEFAULT_RGB.first),
                prefs.getInt(KEY_CURRENT_G, DEFAULT_RGB.second),
                prefs.getInt(KEY_CURRENT_B, DEFAULT_RGB.third),
            )
        return State(active = active, currentRgb = currentRgb)
    }

    fun activate(
        context: Context,
        zoneKey: String,
        initialRgb: Triple<Int, Int, Int>,
    ) {
        prefs(context, zoneKey)
            .edit()
            .putBoolean(KEY_ACTIVE, true)
            .putInt(KEY_CURRENT_R, initialRgb.first)
            .putInt(KEY_CURRENT_G, initialRgb.second)
            .putInt(KEY_CURRENT_B, initialRgb.third)
            .apply()
    }

    fun deactivate(
        context: Context,
        zoneKey: String,
    ) {
        prefs(context, zoneKey)
            .edit()
            .putBoolean(KEY_ACTIVE, false)
            .apply()
    }

    fun saveCurrentRgb(
        context: Context,
        zoneKey: String,
        rgb: Triple<Int, Int, Int>,
    ) {
        prefs(context, zoneKey)
            .edit()
            .putInt(KEY_CURRENT_R, rgb.first)
            .putInt(KEY_CURRENT_G, rgb.second)
            .putInt(KEY_CURRENT_B, rgb.third)
            .apply()
    }

    private fun prefs(
        context: Context,
        zoneKey: String,
    ) = context.applicationContext.getSharedPreferences(
        "$PREFS_NAME_PREFIX$zoneKey",
        Context.MODE_PRIVATE,
    )
}

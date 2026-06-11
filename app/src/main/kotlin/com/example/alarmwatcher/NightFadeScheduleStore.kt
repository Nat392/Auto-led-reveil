package com.example.alarmwatcher

import android.content.Context

/**
 * Persiste, par zone, la décision de planification du fondu nocturne prise pour le mode soirée
 * en cours (ancré sur `bureau_evening_start_ms` / `chambre_evening_start_ms` de
 * [SunsetTimesStore]).
 *
 * Une seule décision est prise par mode soirée : tant que `eveningStartMs` ne change pas (nouveau
 * mode soirée le lendemain), [AlarmMonitorRunner.scheduleNightFades] réutilise
 * [Anchor.originalStartTimeMs] et n'envoie plus d'alarme une fois [Anchor.fired], ce qui évite de
 * reprogrammer en boucle une alarme "immédiate" lorsque le mode soirée est déjà passé.
 */
internal object NightFadeScheduleStore {
    private const val PREFS_NAME = "night_fade_schedule"

    private const val KEY_BUREAU_EVENING_START_MS = "bureau_anchor_evening_start_ms"
    private const val KEY_BUREAU_ORIGINAL_START_MS = "bureau_anchor_original_start_ms"
    private const val KEY_BUREAU_TARGET_END_MS = "bureau_anchor_target_end_ms"
    private const val KEY_BUREAU_FIRED = "bureau_anchor_fired"

    private const val KEY_CHAMBRE_EVENING_START_MS = "chambre_anchor_evening_start_ms"
    private const val KEY_CHAMBRE_ORIGINAL_START_MS = "chambre_anchor_original_start_ms"
    private const val KEY_CHAMBRE_TARGET_END_MS = "chambre_anchor_target_end_ms"
    private const val KEY_CHAMBRE_FIRED = "chambre_anchor_fired"

    data class Anchor(
        val eveningStartMs: Long,
        val originalStartTimeMs: Long,
        val targetEndTimeMs: Long,
        val fired: Boolean,
    )

    fun getAnchor(
        context: Context,
        zoneKey: String,
    ): Anchor? {
        val keys = keysFor(zoneKey) ?: return null
        val prefs = prefs(context)
        val eveningStartMs = prefs.getLong(keys.eveningStart, -1L).takeIf { it > 0L }
        val originalStartTimeMs = prefs.getLong(keys.originalStart, -1L).takeIf { it > 0L }
        val targetEndTimeMs = prefs.getLong(keys.targetEnd, -1L).takeIf { it > 0L }
        return if (eveningStartMs != null && originalStartTimeMs != null && targetEndTimeMs != null) {
            Anchor(
                eveningStartMs = eveningStartMs,
                originalStartTimeMs = originalStartTimeMs,
                targetEndTimeMs = targetEndTimeMs,
                fired = prefs.getBoolean(keys.fired, false),
            )
        } else {
            null
        }
    }

    fun saveAnchor(
        context: Context,
        zoneKey: String,
        anchor: Anchor,
    ) {
        val keys = keysFor(zoneKey) ?: return
        prefs(context)
            .edit()
            .putLong(keys.eveningStart, anchor.eveningStartMs)
            .putLong(keys.originalStart, anchor.originalStartTimeMs)
            .putLong(keys.targetEnd, anchor.targetEndTimeMs)
            .putBoolean(keys.fired, anchor.fired)
            .apply()
    }

    fun clearAnchor(
        context: Context,
        zoneKey: String,
    ) {
        val keys = keysFor(zoneKey) ?: return
        prefs(context)
            .edit()
            .remove(keys.eveningStart)
            .remove(keys.originalStart)
            .remove(keys.targetEnd)
            .remove(keys.fired)
            .apply()
    }

    private data class Keys(
        val eveningStart: String,
        val originalStart: String,
        val targetEnd: String,
        val fired: String,
    )

    private fun keysFor(zoneKey: String): Keys? =
        when (zoneKey) {
            SunsetAutomationScheduler.ZONE_BUREAU ->
                Keys(
                    KEY_BUREAU_EVENING_START_MS,
                    KEY_BUREAU_ORIGINAL_START_MS,
                    KEY_BUREAU_TARGET_END_MS,
                    KEY_BUREAU_FIRED,
                )
            SunsetAutomationScheduler.ZONE_CHAMBRE ->
                Keys(
                    KEY_CHAMBRE_EVENING_START_MS,
                    KEY_CHAMBRE_ORIGINAL_START_MS,
                    KEY_CHAMBRE_TARGET_END_MS,
                    KEY_CHAMBRE_FIRED,
                )
            else -> null
        }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )
}

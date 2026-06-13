package com.example.alarmwatcher

import android.content.Context

/**
 * Suit, par zone, si [NightFadeService] est actuellement en train de faire tourner le fondu
 * nocturne. Contrairement à [NightFadeScheduleStore.Anchor.fired] (qui ne fait qu'indiquer
 * qu'une alarme a été déclenchée une fois pour ce mode soirée), ce drapeau permet à
 * [AlarmMonitorRunner] de détecter qu'un fondu déjà "fired" ne tourne plus (processus relancé,
 * service tué) et doit être repris immédiatement.
 *
 * [clearAll] doit être appelé au démarrage du processus (avant tout fondu), puisqu'aucun
 * [NightFadeService] ne peut alors être en cours d'exécution.
 */
internal object NightFadeRunningStore {
    private const val PREFS_NAME = "night_fade_running"
    private const val KEY_BUREAU_RUNNING = "bureau_running"
    private const val KEY_CHAMBRE_RUNNING = "chambre_running"

    fun markRunning(
        context: Context,
        zoneKey: String,
    ) = setRunning(context, zoneKey, true)

    fun clearRunning(
        context: Context,
        zoneKey: String,
    ) = setRunning(context, zoneKey, false)

    fun isRunning(
        context: Context,
        zoneKey: String,
    ): Boolean {
        val key = keyFor(zoneKey) ?: return false
        return prefs(context).getBoolean(key, false)
    }

    fun clearAll(context: Context) {
        prefs(context)
            .edit()
            .remove(KEY_BUREAU_RUNNING)
            .remove(KEY_CHAMBRE_RUNNING)
            .apply()
    }

    private fun setRunning(
        context: Context,
        zoneKey: String,
        running: Boolean,
    ) {
        val key = keyFor(zoneKey) ?: return
        prefs(context).edit().putBoolean(key, running).apply()
    }

    private fun keyFor(zoneKey: String): String? =
        when (zoneKey) {
            SunsetAutomationScheduler.ZONE_BUREAU -> KEY_BUREAU_RUNNING
            SunsetAutomationScheduler.ZONE_CHAMBRE -> KEY_CHAMBRE_RUNNING
            else -> null
        }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )
}

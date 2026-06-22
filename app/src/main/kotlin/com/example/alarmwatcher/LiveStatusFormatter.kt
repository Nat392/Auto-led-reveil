package com.example.alarmwatcher

import android.content.Context
import java.text.DateFormat
import java.util.Date

/**
 * Construit le texte de la carte "État actuel" du Dashboard à partir des valeurs auparavant
 * calculées puis jetées (radiation solaire, heure du coucher de soleil, état du Daylight
 * Harvesting par zone) : elles sont désormais persistées par [SolarConditionsStore] et
 * [SunsetTimesStore] pour pouvoir être affichées ici.
 */
internal object LiveStatusFormatter {
    fun build(context: Context): String {
        val text = StringBuilder()
        appendSolarReading(text, context)
        appendSunsetTimes(text, context)
        appendDaylightHarvesting(text, context)
        return text.toString().trim()
    }

    private fun appendSolarReading(
        text: StringBuilder,
        context: Context,
    ) {
        val solar = SolarConditionsStore.get(context)
        if (solar == null) {
            text.append("☀️ Radiation solaire : aucune mesure récente\n\n")
            return
        }
        val wm2 = solar.shortwaveRadiationWm2.toInt()
        val at = formatTime(solar.readAtMs)
        text.append("☀️ Radiation solaire (dernière mesure) : $wm2 W/m² à $at\n\n")
    }

    private fun appendSunsetTimes(
        text: StringBuilder,
        context: Context,
    ) {
        val sunsetMs = SunsetTimesStore.getSunsetInstantMs(context)
        val sunsetText = sunsetMs?.let { formatTime(it) } ?: "pas encore calculé"
        text.append("🌇 Coucher du soleil (aujourd'hui) : $sunsetText\n")
        listOf(
            SunsetAutomationScheduler.ZONE_BUREAU to "Bureau",
            SunsetAutomationScheduler.ZONE_CHAMBRE to "Chambre",
            SunsetAutomationScheduler.ZONE_CUISINE to "Cuisine",
        ).forEach { (zoneKey, label) ->
            val eveningStartMs = SunsetTimesStore.getEveningStartMs(context, zoneKey)
            val eveningText = eveningStartMs?.let { formatTime(it) } ?: "—"
            text.append("   • $label bascule en mode soirée à $eveningText\n")
        }
        text.append("\n")
    }

    private fun appendDaylightHarvesting(
        text: StringBuilder,
        context: Context,
    ) {
        text.append("🪴 Récupération de la lumière du jour :\n")
        listOf(
            SunsetAutomationScheduler.ZONE_CHAMBRE to "Chambre",
            SunsetAutomationScheduler.ZONE_CUISINE to "Cuisine",
        ).forEach { (zoneKey, label) ->
            val state = DaylightHarvestingStateStore.getState(context, zoneKey)
            val threshold = DaylightHarvestingWorker.saturationThresholdWm2(zoneKey).toInt()
            val statusWord = if (state.active) "actif" else "inactif"
            val rgb = state.currentRgb
            text.append(
                "   • $label : $statusWord, RGB(${rgb.first}, ${rgb.second}, ${rgb.third}), seuil $threshold W/m²\n",
            )
        }
    }

    private fun formatTime(timeMs: Long): String {
        val formatter = DateFormat.getTimeInstance(DateFormat.SHORT)
        return formatter.format(Date(timeMs))
    }
}

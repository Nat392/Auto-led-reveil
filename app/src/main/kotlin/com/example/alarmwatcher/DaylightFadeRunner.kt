package com.example.alarmwatcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Fait glisser progressivement la couleur de la Cuisine d'un vecteur RGB [from] vers un vecteur
 * cible [to], par paliers corrigés en gamma (γ=2.4, même constante que [SunriseRampSupport]) pour
 * une transition perceptuellement lissée (loi de Weber-Fechner/Stevens).
 *
 * Après chaque palier appliqué avec succès, la couleur est immédiatement persistée dans
 * [DaylightHarvestingStateStore] : si Android tue le processus en plein fondu (Doze Mode), le
 * prochain réveil du worker reprend depuis la dernière couleur réellement appliquée, et non
 * depuis une valeur potentiellement obsolète de plusieurs minutes.
 */
internal class DaylightFadeRunner(
    private val bulbController: BulbControllerApi,
    private val crashReporter: CrashReporterApi,
) {
    suspend fun fade(
        context: Context,
        zoneKey: String,
        zone: SunriseBulbZone,
        from: Triple<Int, Int, Int>,
        to: Triple<Int, Int, Int>,
        durationMs: Long = DEFAULT_DURATION_MS,
        steps: Int = DEFAULT_STEPS,
    ) {
        val stepDelayMs = durationMs / steps

        for (step in 1..steps) {
            val progress = step.toDouble() / steps.toDouble()
            val red = perceptualLerp(from.first, to.first, progress)
            val green = perceptualLerp(from.second, to.second, progress)
            val blue = perceptualLerp(from.third, to.third, progress)

            val success =
                bulbController.applyScene(
                    context = context,
                    macAddress = zone.macAddress,
                    red = red,
                    green = green,
                    blue = blue,
                    white = zone.whiteChannel,
                    brightnessPercent = zone.brightnessPercent,
                )

            if (success) {
                DaylightHarvestingStateStore.saveCurrentRgb(context, zoneKey, Triple(red, green, blue))
            } else {
                Log.w(
                    TAG,
                    "Échec BLE pour ${zone.macAddress} au palier $step/$steps du Daylight Harvesting. " +
                        "Reprise automatique au prochain cycle.",
                )
                crashReporter.reportNonFatal(
                    context = context,
                    throwable = Exception("Échec de l'envoi BLE pour ${zone.macAddress} (Daylight Harvesting)"),
                    source = "DaylightFadeRunner.fade",
                )
            }

            if (step < steps) {
                delay(stepDelayMs)
            }
        }
    }

    private fun perceptualLerp(
        from: Int,
        to: Int,
        progress: Double,
    ): Int {
        val gammaFrom = (from / MAX_COLOR_VALUE_D).pow(GAMMA)
        val gammaTo = (to / MAX_COLOR_VALUE_D).pow(GAMMA)
        val interpolated = gammaFrom + (gammaTo - gammaFrom) * progress
        return (interpolated.pow(1.0 / GAMMA) * MAX_COLOR_VALUE_D).roundToInt().coerceIn(0, MAX_COLOR_VALUE)
    }

    private companion object {
        const val TAG = "DaylightFadeRunner"
        const val GAMMA = 2.4
        const val MAX_COLOR_VALUE = 255
        const val MAX_COLOR_VALUE_D = 255.0

        // 14 minutes, 28 paliers de 30 secondes.
        const val DEFAULT_DURATION_MS = 840_000L
        const val DEFAULT_STEPS = 28
    }
}

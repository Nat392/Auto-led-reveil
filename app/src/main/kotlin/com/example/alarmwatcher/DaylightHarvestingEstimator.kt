package com.example.alarmwatcher

import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Calcule la couleur cible du Daylight Harvesting en fonction de la radiation solaire actuelle
 * (`shortwave_radiation`, en W/m²), selon la loi psychophysique de Weber-Fechner : la réponse
 * perceptuelle à l'intensité d'un stimulus lumineux est logarithmique, pas linéaire.
 */
internal object DaylightHarvestingEstimator {
    // I₀ : seuil de référence évitant ln(0), et en deçà duquel la radiation est considérée nulle.
    private const val REFERENCE_RADIATION_WM2 = 1.0

    // Radiation à partir de laquelle l'éclairage artificiel est totalement éteint.
    private const val SATURATION_RADIATION_WM2 = 600.0

    private val DEFAULT_DAY_PROFILE = Triple(220, 240, 255)

    private val LOG_SATURATION_RATIO = ln(1.0 + SATURATION_RADIATION_WM2 / REFERENCE_RADIATION_WM2)

    fun calculateTargetRgb(
        shortwaveRadiation: Double,
        dayProfile: Triple<Int, Int, Int> = DEFAULT_DAY_PROFILE,
    ): Triple<Int, Int, Int> {
        val naturalFraction =
            (ln(1.0 + shortwaveRadiation / REFERENCE_RADIATION_WM2) / LOG_SATURATION_RATIO)
                .coerceIn(0.0, 1.0)
        val artificialFraction = 1.0 - naturalFraction

        return Triple(
            scaleChannel(dayProfile.first, artificialFraction),
            scaleChannel(dayProfile.second, artificialFraction),
            scaleChannel(dayProfile.third, artificialFraction),
        )
    }

    private fun scaleChannel(
        channel: Int,
        fraction: Double,
    ): Int = (channel * fraction).roundToInt().coerceIn(0, 255)
}

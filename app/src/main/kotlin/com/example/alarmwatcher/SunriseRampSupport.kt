package com.example.alarmwatcher

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

internal object SunriseRampSupport {
    const val MAX_RAMP_STEPS = 30
    const val MIN_STEP_DELAY_MS = 250L

    data class Scene(
        val red: Int,
        val green: Int,
        val blue: Int
    )

    fun computeStepCount(durationMs: Long): Int {
        val rawStepCount = max(1L, durationMs / MIN_STEP_DELAY_MS).toInt()
        return min(MAX_RAMP_STEPS, rawStepCount)
    }

    fun computeSceneAtStep(
        step: Int,
        totalSteps: Int,
        targetR: Int,
        targetG: Int,
        targetB: Int
    ): Scene {
        val safeTotalSteps = totalSteps.coerceAtLeast(1)
        val progress = (step.toDouble() / safeTotalSteps.toDouble()).coerceIn(0.0, 1.0)
        val gammaProgress = progress.pow(2.4)

        return Scene(
            red = (targetR.coerceIn(0, 255) * gammaProgress).roundToInt().coerceIn(0, 255),
            green = (targetG.coerceIn(0, 255) * gammaProgress).roundToInt().coerceIn(0, 255),
            blue = (targetB.coerceIn(0, 255) * gammaProgress).roundToInt().coerceIn(0, 255)
        )
    }
}
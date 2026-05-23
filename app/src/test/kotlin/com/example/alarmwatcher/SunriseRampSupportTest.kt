package com.example.alarmwatcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SunriseRampSupportTest {

    @Test
    fun `computeStepCount enforces minimum and maximum bounds`() {
        assertEquals(1, SunriseRampSupport.computeStepCount(0L))
        assertEquals(1, SunriseRampSupport.computeStepCount(SunriseRampSupport.MIN_STEP_DELAY_MS - 1))
        assertEquals(1, SunriseRampSupport.computeStepCount(SunriseRampSupport.MIN_STEP_DELAY_MS))
        assertEquals(
            SunriseRampSupport.MAX_RAMP_STEPS,
            SunriseRampSupport.computeStepCount(
                SunriseRampSupport.MIN_STEP_DELAY_MS * (SunriseRampSupport.MAX_RAMP_STEPS + 2L)
            )
        )
    }

    @Test
    fun `computeSceneAtStep returns darkness at step zero and the full target at the final step`() {
        val targetRed = 100
        val targetGreen = 150
        val targetBlue = 200

        val start = SunriseRampSupport.computeSceneAtStep(
            step = 0,
            totalSteps = 4,
            targetR = targetRed,
            targetG = targetGreen,
            targetB = targetBlue
        )
        val end = SunriseRampSupport.computeSceneAtStep(
            step = 4,
            totalSteps = 4,
            targetR = targetRed,
            targetG = targetGreen,
            targetB = targetBlue
        )

        assertEquals(SunriseRampSupport.Scene(0, 0, 0), start)
        assertEquals(SunriseRampSupport.Scene(targetRed, targetGreen, targetBlue), end)
    }

    @Test
    fun `computeSceneAtStep clamps invalid input values before scaling`() {
        val scene = SunriseRampSupport.computeSceneAtStep(
            step = 5,
            totalSteps = 0,
            targetR = 300,
            targetG = -10,
            targetB = 42
        )

        assertEquals(SunriseRampSupport.Scene(255, 0, 42), scene)
    }
}
package com.example.alarmwatcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `computeSceneAtStep applique le decalage de Rayleigh aux canaux`() {
        val totalSteps = 30 // On simule 30 étapes (1 par minute)
        val targetR = 255
        val targetG = 255
        val targetB = 255

        // Minute 0 : Tout est à 0 (Obscurité totale)
        val scene0 = SunriseRampSupport.computeSceneAtStep(0, totalSteps, targetR, targetG, targetB)
        assertEquals(0, scene0.red, "Le rouge doit être à 0 à T=0")
        assertEquals(0, scene0.green, "Le vert doit être à 0 à T=0")
        assertEquals(0, scene0.blue, "Le bleu doit être à 0 à T=0")

        // Minute 4 : Le Rouge s'allume (dès T=3), le Vert et le Bleu sont encore à 0
        val scene4 = SunriseRampSupport.computeSceneAtStep(4, totalSteps, targetR, targetG, targetB)
        assertTrue(scene4.red > 0, "Le rouge devrait être allumé à T=4")
        assertEquals(0, scene4.green, "Le vert devrait être à 0 à T=4")
        assertEquals(0, scene4.blue, "Le bleu devrait être à 0 à T=4")

        // Minute 8 : Le Rouge et le Vert sont allumés (Vert dès T=6), le Bleu est à 0
        val scene8 = SunriseRampSupport.computeSceneAtStep(8, totalSteps, targetR, targetG, targetB)
        assertTrue(scene8.red > 0, "Le rouge devrait être allumé à T=8")
        assertTrue(scene8.green > 0, "Le vert devrait être allumé à T=8")
        assertEquals(0, scene8.blue, "Le bleu devrait être à 0 à T=8")

        // Minute 12 : Tout le monde est réveillé (Bleu dès T=10)
        val scene12 = SunriseRampSupport.computeSceneAtStep(12, totalSteps, targetR, targetG, targetB)
        assertTrue(scene12.red > 0, "Le rouge devrait être allumé à T=12")
        assertTrue(scene12.green > 0, "Le vert devrait être allumé à T=12")
        assertTrue(scene12.blue > 0, "Le bleu devrait être allumé à T=12")

        // Minute 30 : Les cibles finales doivent être atteintes
        val scene30 = SunriseRampSupport.computeSceneAtStep(30, totalSteps, targetR, targetG, targetB)
        assertEquals(targetR, scene30.red)
        assertEquals(targetG, scene30.green)
        assertEquals(targetB, scene30.blue)
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
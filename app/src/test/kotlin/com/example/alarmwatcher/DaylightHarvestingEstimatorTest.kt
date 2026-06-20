package com.example.alarmwatcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DaylightHarvestingEstimatorTest {
    @Test
    fun `calculateTargetRgb returns the full day profile in complete darkness`() {
        val result = DaylightHarvestingEstimator.calculateTargetRgb(0.0, saturationRadiationWm2 = 600.0)

        assertEquals(Triple(220, 240, 255), result)
    }

    @Test
    fun `calculateTargetRgb turns the artificial light off at the saturation radiation`() {
        val result = DaylightHarvestingEstimator.calculateTargetRgb(600.0, saturationRadiationWm2 = 600.0)

        assertEquals(Triple(0, 0, 0), result)
    }

    @Test
    fun `calculateTargetRgb clamps beyond the saturation radiation`() {
        val result = DaylightHarvestingEstimator.calculateTargetRgb(1_200.0, saturationRadiationWm2 = 600.0)

        assertEquals(Triple(0, 0, 0), result)
    }

    @Test
    fun `calculateTargetRgb decreases monotonically as radiation increases`() {
        val radiations = listOf(0.0, 50.0, 150.0, 300.0, 450.0, 600.0)

        val reds =
            radiations.map {
                DaylightHarvestingEstimator.calculateTargetRgb(it, saturationRadiationWm2 = 600.0).first
            }

        for (i in 0 until reds.size - 1) {
            assertTrue(reds[i] >= reds[i + 1], "Attendu ${reds[i]} >= ${reds[i + 1]} (index $i)")
        }
        assertEquals(220, reds.first())
        assertEquals(0, reds.last())
    }

    @Test
    fun `calculateTargetRgb follows a logarithmic curve rather than a linear ramp`() {
        val at0 = DaylightHarvestingEstimator.calculateTargetRgb(0.0, saturationRadiationWm2 = 600.0).first
        val at50 = DaylightHarvestingEstimator.calculateTargetRgb(50.0, saturationRadiationWm2 = 600.0).first
        val at300 = DaylightHarvestingEstimator.calculateTargetRgb(300.0, saturationRadiationWm2 = 600.0).first
        val at350 = DaylightHarvestingEstimator.calculateTargetRgb(350.0, saturationRadiationWm2 = 600.0).first

        val earlyDrop = at0 - at50
        val lateDrop = at300 - at350

        // La loi de Weber-Fechner produit une chute rapide aux faibles radiations, puis plus
        // progressive : une interpolation linéaire produirait des chutes égales sur ces deux
        // segments de même largeur (50 W/m²).
        assertTrue(earlyDrop > lateDrop, "earlyDrop=$earlyDrop devrait être > lateDrop=$lateDrop")
    }

    @Test
    fun `calculateTargetRgb scales a custom day profile`() {
        val customProfile = Triple(100, 50, 10)

        val atZero = DaylightHarvestingEstimator.calculateTargetRgb(0.0, customProfile, saturationRadiationWm2 = 600.0)
        val atSaturation =
            DaylightHarvestingEstimator.calculateTargetRgb(600.0, customProfile, saturationRadiationWm2 = 600.0)

        assertEquals(customProfile, atZero)
        assertEquals(Triple(0, 0, 0), atSaturation)
    }

    @Test
    fun `calculateTargetRgb saturates earlier for a lower threshold zone`() {
        val cuisine = DaylightHarvestingEstimator.calculateTargetRgb(150.0, saturationRadiationWm2 = 150.0).first
        val chambre = DaylightHarvestingEstimator.calculateTargetRgb(150.0, saturationRadiationWm2 = 400.0).first

        assertEquals(0, cuisine)
        assertTrue(chambre > 0, "Attendu chambre > 0 (sature à 400 W/m²), obtenu $chambre")
    }
}

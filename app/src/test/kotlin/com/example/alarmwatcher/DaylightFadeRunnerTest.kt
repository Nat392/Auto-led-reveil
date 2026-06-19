package com.example.alarmwatcher

import android.content.Context
import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DaylightFadeRunnerTest {
    private val context = mockk<Context>()
    private val bulbController = mockk<BulbControllerApi>()
    private val crashReporter = mockk<CrashReporterApi>(relaxed = true)
    private val runner = DaylightFadeRunner(bulbController, crashReporter)

    private val zone =
        SunriseBulbZone(
            label = "Cuisine",
            macAddress = "22:33:44:55:66:77",
            sunriseR = 220,
            sunriseG = 240,
            sunriseB = 255,
            sunsetR = 255,
            sunsetG = 140,
            sunsetB = 0,
            whiteChannel = 0,
            brightnessPercent = 100,
        )

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        mockkObject(DaylightHarvestingStateStore)
        every { Log.w(any(), any<String>()) } returns 0
        every { DaylightHarvestingStateStore.saveCurrentRgb(any(), any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `fade applies every step and persists each successful color`() =
        runTest {
            coEvery {
                bulbController.applyScene(context, zone.macAddress, any(), any(), any(), 0, 100)
            } returns true

            runner.fade(
                context = context,
                fadeZone = FadeZone(key = "cuisine", bulb = zone),
                transition = ColorTransition(from = Triple(0, 0, 0), to = Triple(220, 240, 255)),
                durationMs = 4_000L,
                steps = 4,
            )

            coVerify(exactly = 4) {
                bulbController.applyScene(context, zone.macAddress, any(), any(), any(), 0, 100)
            }
            verify(exactly = 4) { DaylightHarvestingStateStore.saveCurrentRgb(context, "cuisine", any()) }
        }

    @Test
    fun `fade reaches exactly the target color on the final step`() =
        runTest {
            coEvery {
                bulbController.applyScene(context, zone.macAddress, any(), any(), any(), 0, 100)
            } returns true

            runner.fade(
                context = context,
                fadeZone = FadeZone(key = "cuisine", bulb = zone),
                transition = ColorTransition(from = Triple(0, 0, 0), to = Triple(220, 240, 255)),
                durationMs = 4_000L,
                steps = 4,
            )

            coVerify(exactly = 1) {
                bulbController.applyScene(context, zone.macAddress, 220, 240, 255, 0, 100)
            }
            verify(exactly = 1) {
                DaylightHarvestingStateStore.saveCurrentRgb(context, "cuisine", Triple(220, 240, 255))
            }
        }

    @Test
    fun `fade applies a gamma-corrected curve rather than a linear interpolation`() =
        runTest {
            val appliedReds = mutableListOf<Int>()
            coEvery {
                bulbController.applyScene(context, zone.macAddress, any(), any(), any(), 0, 100)
            } answers {
                appliedReds.add(thirdArg())
                true
            }

            runner.fade(
                context = context,
                fadeZone = FadeZone(key = "cuisine", bulb = zone),
                transition = ColorTransition(from = Triple(0, 0, 0), to = Triple(255, 0, 0)),
                durationMs = 2_000L,
                steps = 2,
            )

            assertEquals(2, appliedReds.size)
            // Avec une interpolation linéaire, le palier à mi-parcours donnerait ~128. La
            // correction gamma (γ=2.4) pousse la valeur perçue à mi-parcours nettement plus haut.
            assertTrue(appliedReds[0] > 150, "Palier à mi-parcours = ${appliedReds[0]}, attendu > 150")
            assertEquals(255, appliedReds[1])
        }

    @Test
    fun `fade reports a non fatal error and skips persistence when the bulb write fails`() =
        runTest {
            coEvery {
                bulbController.applyScene(context, zone.macAddress, any(), any(), any(), 0, 100)
            } returns false

            runner.fade(
                context = context,
                fadeZone = FadeZone(key = "cuisine", bulb = zone),
                transition = ColorTransition(from = Triple(0, 0, 0), to = Triple(220, 240, 255)),
                durationMs = 2_000L,
                steps = 2,
            )

            verify(exactly = 0) { DaylightHarvestingStateStore.saveCurrentRgb(any(), any(), any()) }
            verify(exactly = 2) {
                crashReporter.reportNonFatal(
                    context = context,
                    throwable = any(),
                    source = "DaylightFadeRunner.fade",
                )
            }
        }
}

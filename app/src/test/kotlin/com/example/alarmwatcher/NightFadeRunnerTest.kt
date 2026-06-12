package com.example.alarmwatcher

import android.content.Context
import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NightFadeRunnerTest {
    private val context = mockk<Context>()
    private val bulbController = mockk<BulbControllerApi>()
    private val crashReporter = mockk<CrashReporterApi>(relaxed = true)

    private val zone =
        SunriseBulbZone(
            label = "Chambre",
            macAddress = "AA:BB:CC:DD:EE:FF",
            sunriseR = 255,
            sunriseG = 210,
            sunriseB = 160,
            sunsetR = 255,
            sunsetG = 140,
            sunsetB = 0,
        )

    private class MutableClock(var nowMs: Long)

    private fun createRunner(clock: MutableClock) =
        NightFadeRunner(
            bulbController = bulbController,
            crashReporter = crashReporter,
            nowMs = { clock.nowMs },
        )

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0
    }

    @Test
    fun `applies a fade step from the sunset colors then sends the final scene`() =
        runTest {
            val startTimeMs = 1_000L
            val endTimeMs = 2_000L
            val clock = MutableClock(startTimeMs)
            val runner = createRunner(clock)

            coEvery {
                bulbController.applyScene(context, zone.macAddress, any(), any(), any(), 0, 100)
            } answers {
                clock.nowMs = endTimeMs
                true
            }

            runner.run(context, zone, startTimeMs, endTimeMs)

            coVerify(exactly = 1) {
                bulbController.applyScene(context, zone.macAddress, zone.sunsetR, zone.sunsetG, zone.sunsetB, 0, 100)
            }
            coVerify(exactly = 1) {
                bulbController.applyScene(context, zone.macAddress, 3, 0, 0, 0, 100)
            }
        }

    @Test
    fun `applies successive fade steps and waits between each cycle`() =
        runTest {
            val startTimeMs = 0L
            val endTimeMs = 120_000L
            val clock = MutableClock(startTimeMs)
            val runner = createRunner(clock)

            coEvery {
                bulbController.applyScene(context, zone.macAddress, any(), any(), any(), 0, 100)
            } answers {
                clock.nowMs += 60_000L
                true
            }

            runner.run(context, zone, startTimeMs, endTimeMs)

            coVerify(exactly = 3) {
                bulbController.applyScene(context, zone.macAddress, any(), any(), any(), 0, 100)
            }
            coVerify(exactly = 1) {
                bulbController.applyScene(context, zone.macAddress, 3, 0, 0, 0, 100)
            }
        }

    @Test
    fun `uses the system clock by default`() =
        runTest {
            val runner = NightFadeRunner(bulbController, crashReporter)

            coEvery {
                bulbController.applyScene(context, zone.macAddress, any(), any(), any(), 0, 100)
            } returns true

            runner.run(context, zone, 0L, 0L)

            coVerify(exactly = 1) {
                bulbController.applyScene(context, zone.macAddress, 3, 0, 0, 0, 100)
            }
        }

    @Test
    fun `sends only the final scene when the start time has already passed`() =
        runTest {
            val startTimeMs = 1_000L
            val endTimeMs = 2_000L
            val clock = MutableClock(endTimeMs)
            val runner = createRunner(clock)

            coEvery {
                bulbController.applyScene(context, zone.macAddress, any(), any(), any(), 0, 100)
            } returns true

            runner.run(context, zone, startTimeMs, endTimeMs)

            coVerify(exactly = 1) {
                bulbController.applyScene(context, zone.macAddress, any(), any(), any(), 0, 100)
            }
            coVerify(exactly = 1) {
                bulbController.applyScene(context, zone.macAddress, 3, 0, 0, 0, 100)
            }
        }

    @Test
    fun `reports a non-fatal error and keeps going when a BLE write fails softly`() =
        runTest {
            val startTimeMs = 1_000L
            val endTimeMs = 2_000L
            val clock = MutableClock(startTimeMs)
            val runner = createRunner(clock)

            coEvery {
                bulbController.applyScene(context, zone.macAddress, any(), any(), any(), 0, 100)
            } answers {
                clock.nowMs = endTimeMs
                false
            }

            runner.run(context, zone, startTimeMs, endTimeMs)

            coVerify(exactly = 1) {
                crashReporter.reportNonFatal(context = context, throwable = any(), source = "NightFadeRunner.run")
            }
        }

    @Test
    fun `propagates cancellation without reporting a non-fatal error`() {
        val startTimeMs = 1_000L
        val endTimeMs = 2_000L
        val clock = MutableClock(startTimeMs)
        val runner = createRunner(clock)

        coEvery {
            bulbController.applyScene(context, zone.macAddress, any(), any(), any(), 0, 100)
        } throws CancellationException("cancelled")

        assertThrows(CancellationException::class.java) {
            runTest { runner.run(context, zone, startTimeMs, endTimeMs) }
        }

        verify(exactly = 0) { crashReporter.reportNonFatal(any(), any(), any()) }
    }

    @Test
    fun `catches unexpected failures and reports a non-fatal error`() =
        runTest {
            val startTimeMs = 1_000L
            val endTimeMs = 2_000L
            val clock = MutableClock(startTimeMs)
            val runner = createRunner(clock)

            coEvery {
                bulbController.applyScene(context, zone.macAddress, any(), any(), any(), 0, 100)
            } throws IllegalStateException("boom")

            runner.run(context, zone, startTimeMs, endTimeMs)

            coVerify(exactly = 1) {
                crashReporter.reportNonFatal(context = context, throwable = any(), source = "NightFadeRunner.run")
            }
        }
}

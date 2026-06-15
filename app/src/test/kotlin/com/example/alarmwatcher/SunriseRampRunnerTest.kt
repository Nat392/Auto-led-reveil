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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SunriseRampRunnerTest {
    private val context = mockk<Context>()
    private val bulbController = mockk<BulbControllerApi>()
    private val session = mockk<BulbSession>(relaxUnitFun = true)
    private val crashReporter = mockk<CrashReporterApi>(relaxed = true)

    private class MutableClock(var nowMs: Long)

    private fun createRunner(clock: MutableClock = MutableClock(0L)) =
        SunriseRampRunner(
            bulbController = bulbController,
            crashReporter = crashReporter,
            nowMs = { clock.nowMs },
        )

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any(), any<Throwable>()) } returns 0
    }

    @Test
    fun `runs the ramp and sends the final target at the deadline`() =
        runTest {
            val clock = MutableClock(0L)
            val runner = createRunner(clock)
            val progress = mutableListOf<Pair<Int, Int>>()

            coEvery { bulbController.openSession(context, "AA:BB:CC:DD:EE:FF") } returns session
            coEvery { session.applyScene(any(), any(), any(), any()) } answers {
                clock.nowMs = if (clock.nowMs == 0L) 750L else 1_000L
                true
            }
            coEvery { bulbController.applyScene(context, "AA:BB:CC:DD:EE:FF", 255, 128, 64, 0, 100) } returns true

            runner.run(
                context = context,
                macAddress = "AA:BB:CC:DD:EE:FF",
                targetR = 255,
                targetG = 128,
                targetB = 64,
                durationMs = 1_000L,
                onProgress = { currentStep, totalSteps -> progress += currentStep to totalSteps },
            )

            assertEquals(listOf(0 to 4, 4 to 4), progress)
            verify(exactly = 1) { session.close() }
            coVerify(exactly = 1) { session.applyScene(0, 0, 0, 0) }
            coVerify(exactly = 1) { bulbController.applyScene(context, "AA:BB:CC:DD:EE:FF", 255, 128, 64, 0, 100) }
        }

    @Test
    fun `returns immediately when no BLE session can be opened`() =
        runTest {
            val runner = createRunner()
            coEvery { bulbController.openSession(context, any()) } returns null

            runner.run(
                context = context,
                macAddress = "AA:BB:CC:DD:EE:FF",
                targetR = 255,
                targetG = 128,
                targetB = 64,
                durationMs = SunriseRampSupport.MIN_STEP_DELAY_MS,
            )

            coVerify(exactly = 0) { session.applyScene(any(), any(), any(), any()) }
            verify(exactly = 0) { session.close() }
        }

    @Test
    fun `reports a non-fatal error when the final scene write fails`() =
        runTest {
            val runner = createRunner()
            coEvery { bulbController.openSession(context, any()) } returns session
            coEvery { bulbController.applyScene(context, "AA:BB:CC:DD:EE:FF", 255, 128, 64, 0, 100) } returns false

            runner.run(
                context = context,
                macAddress = "AA:BB:CC:DD:EE:FF",
                targetR = 255,
                targetG = 128,
                targetB = 64,
                durationMs = SunriseRampSupport.MIN_STEP_DELAY_MS,
            )

            coVerify(exactly = 1) { bulbController.applyScene(context, "AA:BB:CC:DD:EE:FF", 255, 128, 64, 0, 100) }
            coVerify(exactly = 1) {
                crashReporter.reportNonFatal(
                    context = context,
                    throwable = any(),
                    source = "SunriseService.sendFinalScene",
                )
            }
            verify(exactly = 1) { session.close() }
        }

    @Test
    fun `stops the ramp and closes the session when a BLE write fails softly`() =
        runTest {
            val runner = createRunner()
            coEvery { bulbController.openSession(context, any()) } returns session
            coEvery { session.applyScene(any(), any(), any(), any()) } returns false
            coEvery { bulbController.applyScene(context, "AA:BB:CC:DD:EE:FF", 255, 128, 64, 0, 100) } returns true

            runner.run(
                context = context,
                macAddress = "AA:BB:CC:DD:EE:FF",
                targetR = 255,
                targetG = 128,
                targetB = 64,
                durationMs = 1_000L,
            )

            coVerify(exactly = 1) { session.applyScene(any(), any(), any(), any()) }
            coVerify(exactly = 1) { bulbController.applyScene(context, "AA:BB:CC:DD:EE:FF", 255, 128, 64, 0, 100) }
            verify(exactly = 1) { session.close() }
        }

    @Test
    fun `propagates cancellation and still closes the session`() {
        val runner = createRunner()
        coEvery { bulbController.openSession(context, any()) } returns session
        coEvery { bulbController.applyScene(context, "AA:BB:CC:DD:EE:FF", any(), any(), any(), 0, 100) } throws
            CancellationException("cancelled")

        assertThrows(CancellationException::class.java) {
            runTest {
                runner.run(
                    context = context,
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    targetR = 255,
                    targetG = 128,
                    targetB = 64,
                    durationMs = SunriseRampSupport.MIN_STEP_DELAY_MS,
                )
            }
        }

        verify(exactly = 1) { session.close() }
    }

    @Test
    fun `propagates unexpected failures and still closes the session`() {
        val runner = createRunner()
        coEvery { bulbController.openSession(context, any()) } returns session
        coEvery { bulbController.applyScene(context, "AA:BB:CC:DD:EE:FF", any(), any(), any(), 0, 100) } throws
            IllegalStateException("boom")

        assertThrows(IllegalStateException::class.java) {
            runTest {
                runner.run(
                    context = context,
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    targetR = 255,
                    targetG = 128,
                    targetB = 64,
                    durationMs = SunriseRampSupport.MIN_STEP_DELAY_MS,
                )
            }
        }

        verify(exactly = 1) { session.close() }
    }
}

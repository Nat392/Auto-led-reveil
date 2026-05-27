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
    private val runner = SunriseRampRunner(bulbController, crashReporter)

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any(), any<Throwable>()) } returns 0
    }

    @Test
    fun `runs the full ramp and reports every step`() =
        runTest {
            val progress = mutableListOf<Pair<Int, Int>>()

            coEvery { bulbController.openSession(context, "AA:BB:CC:DD:EE:FF") } returns session
            coEvery { session.applyScene(any(), any(), any(), any()) } returns true

            runner.run(
                context = context,
                macAddress = "AA:BB:CC:DD:EE:FF",
                targetR = 255,
                targetG = 128,
                targetB = 64,
                durationMs = SunriseRampSupport.MIN_STEP_DELAY_MS,
                onProgress = { currentStep, totalSteps -> progress += currentStep to totalSteps },
            )

            assertEquals(listOf(0 to 1, 1 to 1), progress)
            verify(exactly = 1) { session.close() }
            coVerify(exactly = 1) { session.applyScene(0, 0, 0, 0) }
            coVerify(exactly = 1) { session.applyScene(255, 128, 64, 0) }
        }

    @Test
    fun `returns immediately when no BLE session can be opened`() =
        runTest {
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
    fun `stops the ramp and closes the session when a BLE write fails softly`() =
        runTest {
            coEvery { bulbController.openSession(context, any()) } returns session
            coEvery { session.applyScene(any(), any(), any(), any()) } returns false

            runner.run(
                context = context,
                macAddress = "AA:BB:CC:DD:EE:FF",
                targetR = 255,
                targetG = 128,
                targetB = 64,
                durationMs = SunriseRampSupport.MIN_STEP_DELAY_MS,
            )

            coVerify(exactly = 1) { session.applyScene(any(), any(), any(), any()) }
            verify(exactly = 1) { session.close() }
        }

    @Test
    fun `propagates cancellation and still closes the session`() {
        coEvery { bulbController.openSession(context, any()) } returns session
        coEvery { session.applyScene(any(), any(), any(), any()) } throws CancellationException("cancelled")

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
        coEvery { bulbController.openSession(context, any()) } returns session
        coEvery { session.applyScene(any(), any(), any(), any()) } throws IllegalStateException("boom")

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

package com.example.alarmwatcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SunsetCatchUpWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fakeCrashReporter = object : CrashReporterApi {
        val reportedSources = mutableListOf<String?>()

        override fun reportNonFatal(context: Context, throwable: Throwable, source: String?) =
            SupervisorJob().also { reportedSources += source }

        override fun reportFatalBlocking(context: Context, throwable: Throwable, threadName: String?) = Unit

        override fun reportDebugBlocking(context: Context, source: String, details: String) = Unit
    }

    @Before
    fun setUp() {
        mockkObject(SunsetSceneService.Companion)
        SunsetCatchUpWorker.crashReporter = fakeCrashReporter
    }

    @After
    fun tearDown() {
        SunsetCatchUpWorker.crashReporter = DiscordCrashReporter
        unmockkAll()
    }

    @Test
    fun `returns failure when the zone key is missing`() = runBlocking {
        val worker = TestListenableWorkerBuilder<SunsetCatchUpWorker>(context)
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `returns success when the sunset scene is applied`() = runBlocking {
        coEvery {
            SunsetSceneService.applySunsetScene(any(), SunsetAutomationScheduler.ZONE_BUREAU)
        } returns true

        val worker = TestListenableWorkerBuilder<SunsetCatchUpWorker>(context)
            .setInputData(workDataOf(SunsetCatchUpWorker.KEY_ZONE_KEY to SunsetAutomationScheduler.ZONE_BUREAU))
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) {
            SunsetSceneService.applySunsetScene(any(), SunsetAutomationScheduler.ZONE_BUREAU)
        }
    }

    @Test
    fun `returns failure and reports the error when the sunset scene cannot be applied`() = runBlocking {
        coEvery {
            SunsetSceneService.applySunsetScene(any(), SunsetAutomationScheduler.ZONE_CHAMBRE)
        } returns false

        val worker = TestListenableWorkerBuilder<SunsetCatchUpWorker>(context)
            .setInputData(workDataOf(SunsetCatchUpWorker.KEY_ZONE_KEY to SunsetAutomationScheduler.ZONE_CHAMBRE))
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 1) {
            SunsetSceneService.applySunsetScene(any(), SunsetAutomationScheduler.ZONE_CHAMBRE)
        }
        assertEquals(listOf("SunsetCatchUpWorker"), fakeCrashReporter.reportedSources)
    }
}
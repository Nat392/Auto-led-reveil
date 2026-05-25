package com.example.alarmwatcher

import android.app.AlarmManager
import android.content.Context
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import android.util.Log
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AlarmMonitorRunnerTest {

    private val context = mockk<Context>()
    private val alarmScheduler = mockk<AlarmSchedulerApi>(relaxed = true)
    private val crashReporter = mockk<CrashReporterApi>(relaxed = true)
    private val runner = AlarmMonitorRunner(alarmScheduler, crashReporter)

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any<Throwable>()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `returns early when AlarmManager is unavailable`() {
        every { context.getSystemService(AlarmManager::class.java) } returns null

        runner.scanNextAlarmAndSchedule(context)

        confirmVerified(alarmScheduler, crashReporter)
    }

    @Test
    fun `cancels the prewarn alarm when no next alarm is available`() {
        val alarmManager = mockk<AlarmManager>()
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.nextAlarmClock } returns null

        runner.scanNextAlarmAndSchedule(context)

        verify(exactly = 1) { alarmScheduler.cancelPreWarn(context) }
        confirmVerified(alarmScheduler, crashReporter)
    }

    @Test
    fun `cancels the prewarn alarm when the next alarm is already expired`() {
        val alarmManager = mockk<AlarmManager>()
        val alarmClock = mockk<AlarmManager.AlarmClockInfo>()
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.nextAlarmClock } returns alarmClock
        every { alarmClock.triggerTime } returns 1L

        runner.scanNextAlarmAndSchedule(context)

        verify(exactly = 1) { alarmScheduler.cancelPreWarn(context) }
        confirmVerified(alarmScheduler, crashReporter)
    }

    @Test
    fun `cancels the prewarn alarm when the computed window is invalid`() {
        val alarmManager = mockk<AlarmManager>()
        val alarmClock = mockk<AlarmManager.AlarmClockInfo>()
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.nextAlarmClock } returns alarmClock
        every { alarmClock.triggerTime } returns 42_000L

        mockkObject(AlarmTimingSupport)
        every { AlarmTimingSupport.computePreWarnWindow(any(), any()) } returns null

        runner.scanNextAlarmAndSchedule(context)

        verify(exactly = 1) { alarmScheduler.cancelPreWarn(context) }
        confirmVerified(alarmScheduler, crashReporter)
    }

    @Test
    fun `schedules the prewarn alarm when a valid window is computed`() {
        val alarmManager = mockk<AlarmManager>()
        val alarmClock = mockk<AlarmManager.AlarmClockInfo>()
        val triggerTime = System.currentTimeMillis() + AlarmScheduler.PREWARN_MS + 60_000L
        val window = AlarmPreWarnWindow(preWarnAt = 12_000L, scheduleAt = 13_000L, durationMs = 4_200L)

        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.nextAlarmClock } returns alarmClock
        every { alarmClock.triggerTime } returns triggerTime

        mockkObject(AlarmTimingSupport)
        every { AlarmTimingSupport.computePreWarnWindow(any(), any()) } returns window

        runner.scanNextAlarmAndSchedule(context)

        verify(exactly = 1) { alarmScheduler.schedulePreWarn(any(), any(), any(), any()) }
        confirmVerified(alarmScheduler, crashReporter)
    }

    @Test
    fun `reports unexpected failures as non fatal errors`() {
        val alarmManager = mockk<AlarmManager>()
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.nextAlarmClock } throws IllegalStateException("boom")

        runner.scanNextAlarmAndSchedule(context)

        verify(exactly = 1) {
            crashReporter.reportNonFatal(
                context = context,
                throwable = any(),
                source = "AlarmMonitor.scanNextAlarmAndSchedule"
            )
        }
    }

    @Test
    fun `reports security exceptions while reading the alarm clock as non fatal`() {
        every { context.getSystemService(AlarmManager::class.java) } throws SecurityException("denied")

        runner.scanNextAlarmAndSchedule(context)

        verify(exactly = 1) {
            crashReporter.reportNonFatal(
                context = context,
                throwable = any<SecurityException>(),
                source = "AlarmMonitor.scanNextAlarmAndSchedule"
            )
        }
        confirmVerified(alarmScheduler)
    }
}
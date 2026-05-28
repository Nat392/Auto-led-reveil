package com.example.alarmwatcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AlarmSchedulerTest {
    private val context = mockk<Context>()
    private val alarmManager = mockk<AlarmManager>(relaxed = true)
    private val pendingIntent = mockk<PendingIntent>(relaxed = true)
    private val launchIntent = mockk<Intent>(relaxed = true)
    private val defaultIntentFactory = AlarmScheduler.intentFactory

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        mockkStatic(PendingIntent::class)

        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns pendingIntent
        every { alarmManager.canScheduleExactAlarms() } returns true
        every { alarmManager.cancel(any<PendingIntent>()) } returns Unit
        every { pendingIntent.cancel() } returns Unit
        every { launchIntent.setAction(any()) } returns launchIntent
        every { launchIntent.putExtra(any<String>(), any<Long>()) } returns launchIntent

        AlarmScheduler.intentFactory = { _, _ -> launchIntent }
    }

    @AfterEach
    fun tearDown() {
        AlarmScheduler.intentFactory = defaultIntentFactory
        unmockkAll()
    }

    @Test
    fun `schedules an exact prewarn alarm with the expected payload`() {
        val whenMs = 1_700_000_123_000L
        val originalAlarmMs = 1_700_000_900_000L
        val durationMs = 87_000L

        AlarmScheduler.schedulePreWarn(
            context = context,
            whenMs = whenMs,
            originalAlarmMs = originalAlarmMs,
            durationMs = durationMs,
        )

        verify(exactly = 1) {
            PendingIntent.getBroadcast(
                any(),
                5401,
                any(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        verify(exactly = 1) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pendingIntent)
        }
    }

    @Test
    fun `honours exact alarm permission gating on Android S and above`() {
        every { alarmManager.canScheduleExactAlarms() } returns false

        AlarmScheduler.schedulePreWarn(
            context = context,
            whenMs = 1_700_000_123_000L,
            originalAlarmMs = 1_700_000_900_000L,
            durationMs = 87_000L,
        )

        verify(exactly = 1) { PendingIntent.getBroadcast(any(), any(), any(), any()) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
        } else {
            verify(exactly = 1) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
        }
    }

    @Test
    fun `returns early when the AlarmManager is unavailable`() {
        every { context.getSystemService(AlarmManager::class.java) } returns null

        AlarmScheduler.schedulePreWarn(
            context = context,
            whenMs = 1_700_000_123_000L,
            originalAlarmMs = 1_700_000_900_000L,
            durationMs = 87_000L,
        )

        verify(exactly = 0) { PendingIntent.getBroadcast(any(), any(), any(), any()) }
        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) }
    }

    @Test
    fun `cancels a previously scheduled prewarn alarm`() {
        AlarmScheduler.cancelPreWarn(context)

        verify(exactly = 1) {
            PendingIntent.getBroadcast(
                any(),
                5401,
                any(),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        verify(exactly = 1) { alarmManager.cancel(pendingIntent) }
        verify(exactly = 1) { pendingIntent.cancel() }
    }

    @Test
    fun `does nothing when there is no matching pending intent to cancel`() {
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns null

        AlarmScheduler.cancelPreWarn(context)

        verify(exactly = 0) { alarmManager.cancel(any<PendingIntent>()) }
        verify(exactly = 0) { pendingIntent.cancel() }
    }

    @Test
    fun `stops the sunrise service through the context`() {
        every { context.stopService(any()) } returns true

        AlarmScheduler.stopSunriseService(context)

        verify(exactly = 1) { context.stopService(any()) }
    }
}

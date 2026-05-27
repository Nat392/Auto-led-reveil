package com.example.alarmwatcher

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BootReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val receiver = BootReceiver()

    @Before
    fun setUp() {
        mockkObject(AlarmMonitor)
        mockkObject(SunsetAutomationScheduler)

        every { AlarmMonitor.scanNextAlarmAndSchedule(any()) } returns Unit
        every { SunsetAutomationScheduler.requestRefreshAndSchedule(any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `schedules both automations after boot completed`() {
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        verify(exactly = 1) { AlarmMonitor.scanNextAlarmAndSchedule(context) }
        verify(exactly = 1) { SunsetAutomationScheduler.requestRefreshAndSchedule(context) }
    }

    @Test
    fun `ignores unrelated broadcasts`() {
        receiver.onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))

        verify(exactly = 0) { AlarmMonitor.scanNextAlarmAndSchedule(any()) }
        verify(exactly = 0) { SunsetAutomationScheduler.requestRefreshAndSchedule(any()) }
    }
}
package com.example.alarmwatcher

import android.app.AlarmManager
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
class AlarmChangeReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val receiver = AlarmChangeReceiver()

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
    fun `refreshes scheduling when the next alarm clock changes`() {
        receiver.onReceive(context, Intent(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED))

        verify(exactly = 1) { AlarmMonitor.scanNextAlarmAndSchedule(context) }
        verify(exactly = 1) { SunsetAutomationScheduler.requestRefreshAndSchedule(context) }
    }

    @Test
    fun `refreshes scheduling when the timezone changes`() {
        receiver.onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))

        verify(exactly = 1) { AlarmMonitor.scanNextAlarmAndSchedule(context) }
        verify(exactly = 1) { SunsetAutomationScheduler.requestRefreshAndSchedule(context) }
    }

    @Test
    fun `ignores unrelated broadcasts`() {
        receiver.onReceive(context, Intent(Intent.ACTION_PACKAGE_ADDED))

        verify(exactly = 0) { AlarmMonitor.scanNextAlarmAndSchedule(any()) }
        verify(exactly = 0) { SunsetAutomationScheduler.requestRefreshAndSchedule(any()) }
    }
}
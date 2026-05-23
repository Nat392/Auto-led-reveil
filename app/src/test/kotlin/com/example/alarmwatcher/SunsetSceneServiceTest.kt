package com.example.alarmwatcher

import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SunsetSceneServiceTest {

    private val applicationContext = mockk<Context>(relaxed = true)
    private val service = spyk(SunsetSceneService(), recordPrivateCalls = true)

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        mockkObject(BlePermissionSupport)
        mockkObject(SunriseZoneConfig)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0
        every { service.applicationContext } returns applicationContext
        every { service.stopSelf(any()) } returns Unit
        every { service.stopForeground(any<Int>()) } returns Unit
        every { service.startForeground(any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `stops gracefully when the zone key is invalid`() = runTest {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns SunsetAutomationScheduler.ACTION_APPLY_SCENE
        every { intent.getStringExtra(SunsetAutomationScheduler.EXTRA_TARGET_ZONE) } returns "inconnu"

        val result = service.onStartCommand(intent, 0, 73)

        assertEquals(Service.START_NOT_STICKY, result)
        verify(exactly = 1) { service.stopForeground(Service.STOP_FOREGROUND_REMOVE) }
        verify(exactly = 1) { service.stopSelf(73) }
        verify(exactly = 0) { service.startForeground(any(), any()) }
    }

    @Test
    fun `stops immediately when bluetooth connect permission is missing`() = runTest {
        every { BlePermissionSupport.hasBluetoothConnectPermission(any()) } returns false
        every {
            SunriseZoneConfig.bureau
        } returns SunriseBulbZone(
            label = "Bureau",
            macAddress = "AA:BB:CC:DD:EE:FF",
            sunriseR = 255,
            sunriseG = 255,
            sunriseB = 255,
            sunsetR = 255,
            sunsetG = 140,
            sunsetB = 0,
            whiteChannel = 0,
            brightnessPercent = 100
        )
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns SunsetAutomationScheduler.ACTION_APPLY_SCENE
        every { intent.getStringExtra(SunsetAutomationScheduler.EXTRA_TARGET_ZONE) } returns
            SunsetAutomationScheduler.ZONE_BUREAU

        val result = service.onStartCommand(intent, 0, 74)

        assertEquals(Service.START_NOT_STICKY, result)
        verify(exactly = 1) { service.stopSelf(74) }
        verify(exactly = 0) { service.startForeground(any(), any()) }
    }
}
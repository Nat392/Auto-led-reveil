package com.example.alarmwatcher

import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import android.app.Notification
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
        mockkObject(ZenggeBulbController)
        mockkObject(DiscordCrashReporter)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0
        every { DiscordCrashReporter.reportNonFatal(any(), any(), any()) } returns mockk<Job>(relaxed = true)
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
    fun `stops immediately when the action does not target the sunset scene`() = runTest {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns "com.example.alarmwatcher.ACTION_OTHER"

        val result = service.onStartCommand(intent, 0, 70)

        assertEquals(Service.START_NOT_STICKY, result)
        verify(exactly = 1) { service.stopSelf(70) }
        verify(exactly = 0) { service.startForeground(any(), any()) }
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

    @Test
    fun `reports and stops when foreground startup is denied`() = runTest {
        every { BlePermissionSupport.hasBluetoothConnectPermission(any()) } returns true
        every { SunriseZoneConfig.bureau } returns configuredZone()
        every {
            service invoke "buildNotification" withArguments listOf("Bureau")
        } returns mockk<Notification>(relaxed = true)
        every { service.startForeground(any(), any()) } throws SecurityException("denied")

        val intent = mockk<Intent>(relaxed = true)
        every { intent.action } returns SunsetAutomationScheduler.ACTION_APPLY_SCENE
        every { intent.getStringExtra(SunsetAutomationScheduler.EXTRA_TARGET_ZONE) } returns
            SunsetAutomationScheduler.ZONE_BUREAU

        val result = service.onStartCommand(intent, 0, 75)

        assertEquals(Service.START_NOT_STICKY, result)
        verify(exactly = 1) { service.stopSelf(75) }
        verify(exactly = 1) { service.startForeground(any(), any()) }
        verify(exactly = 1) {
            DiscordCrashReporter.reportNonFatal(
                context = applicationContext,
                throwable = any(),
                source = "SunsetSceneService.startForeground"
            )
        }
    }

    @Test
    fun `resolves known zones and rejects unknown keys`() {
        val bureau = configuredZone(label = "Bureau", macAddress = "AA:BB:CC:DD:EE:FF")
        val chambre = configuredZone(label = "Chambre", macAddress = "11:22:33:44:55:66")
        every { SunriseZoneConfig.bureau } returns bureau
        every { SunriseZoneConfig.chambre } returns chambre

        assertEquals(bureau, SunsetSceneService.resolveZone(SunsetAutomationScheduler.ZONE_BUREAU))
        assertEquals(chambre, SunsetSceneService.resolveZone(SunsetAutomationScheduler.ZONE_CHAMBRE))
        assertNull(SunsetSceneService.resolveZone("autre"))
    }

    @Test
    fun `applies the sunset scene with the configured bureau zone`() = runTest {
        val bureau = configuredZone(
            label = "Bureau",
            macAddress = "AA:BB:CC:DD:EE:FF",
            sunsetR = 255,
            sunsetG = 140,
            sunsetB = 0
        )

        every { SunriseZoneConfig.bureau } returns bureau
        every { BlePermissionSupport.hasBluetoothConnectPermission(applicationContext) } returns true
        coEvery {
            ZenggeBulbController.applyScene(
                any(),
                bureau.macAddress,
                bureau.sunsetR,
                bureau.sunsetG,
                bureau.sunsetB,
                bureau.whiteChannel,
                bureau.brightnessPercent
            )
        } returns true

        val applied = SunsetSceneService.applySunsetScene(applicationContext, SunsetAutomationScheduler.ZONE_BUREAU)

        assertTrue(applied)
        coVerify(exactly = 1) {
            ZenggeBulbController.applyScene(
                any(),
                bureau.macAddress,
                255,
                140,
                0,
                0,
                100
            )
        }
    }

    @Test
    fun `returns false when the configured zone is missing`() = runTest {
        every { SunriseZoneConfig.bureau } returns configuredZone(macAddress = "")
        every { BlePermissionSupport.hasBluetoothConnectPermission(any()) } returns true

        val applied = SunsetSceneService.applySunsetScene(applicationContext, SunsetAutomationScheduler.ZONE_BUREAU)

        assertFalse(applied)
        coVerify(exactly = 0) {
            ZenggeBulbController.applyScene(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `returns false and reports a non fatal error when the controller fails`() = runTest {
        val bureau = configuredZone()
        every { SunriseZoneConfig.bureau } returns bureau
        every { BlePermissionSupport.hasBluetoothConnectPermission(any()) } returns true
        coEvery {
            ZenggeBulbController.applyScene(any(), any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("boom")

        val applied = SunsetSceneService.applySunsetScene(applicationContext, SunsetAutomationScheduler.ZONE_BUREAU)

        assertFalse(applied)
        verify(exactly = 1) {
            DiscordCrashReporter.reportNonFatal(
                context = applicationContext,
                throwable = any(),
                source = "SunsetSceneService.applySunsetScene"
            )
        }
    }

    @Test
    fun `propagates cancellation from the controller`() {
        val bureau = configuredZone()
        every { SunriseZoneConfig.bureau } returns bureau
        every { BlePermissionSupport.hasBluetoothConnectPermission(any()) } returns true
        coEvery {
            ZenggeBulbController.applyScene(any(), any(), any(), any(), any(), any(), any())
        } throws CancellationException("cancelled")

        org.junit.jupiter.api.Assertions.assertThrows(CancellationException::class.java) {
            runTest {
                SunsetSceneService.applySunsetScene(applicationContext, SunsetAutomationScheduler.ZONE_BUREAU)
            }
        }
    }

    private fun configuredZone(
        label: String = "Bureau",
        macAddress: String = "AA:BB:CC:DD:EE:FF",
        sunsetR: Int = 255,
        sunsetG: Int = 140,
        sunsetB: Int = 0
    ): SunriseBulbZone {
        return SunriseBulbZone(
            label = label,
            macAddress = macAddress,
            sunriseR = 255,
            sunriseG = 255,
            sunriseB = 255,
            sunsetR = sunsetR,
            sunsetG = sunsetG,
            sunsetB = sunsetB,
            whiteChannel = 0,
            brightnessPercent = 100
        )
    }
}
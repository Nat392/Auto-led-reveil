package com.example.alarmwatcher

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SunriseServiceTest {
    private val applicationContext = mockk<Context>(relaxed = true)
    private val service = spyk(SunriseService(), recordPrivateCalls = true)

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        mockkObject(BlePermissionSupport)
        mockkObject(ZenggeBulbController)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0
        every { service.applicationContext } returns applicationContext
        every { service.stopSelf(any()) } returns Unit
        every { service.startForeground(any(), any()) } returns Unit
        every { service["ensureChannel"]() } returns Unit
        every { service["buildRampNotification"](any<Int>(), any<Int>(), any<Long>()) } returns mockk<Notification>(relaxed = true)
        every { service["updateRampNotification"](any<Int>(), any<Int>(), any<Long>()) } returns Unit
        
        coEvery { ZenggeBulbController.openSession(any(), any()) } answers {
            delay(5000L)
            null
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `stops gracefully when sunrise intent misses rgb arrays`() =
        runTest {
            every { BlePermissionSupport.hasBluetoothConnectPermission(any()) } returns false

            val intent = mockk<Intent>(relaxed = true)
            every { intent.action } returns SunriseService.ACTION_START_SUNRISE
            every { intent.getStringArrayListExtra(SunriseService.EXTRA_BULB_MACS) } returns arrayListOf()

            val result = service.onStartCommand(intent, 0, 41)

            assertEquals(Service.START_NOT_STICKY, result)
            verify(exactly = 1) { service.stopSelf(41) }
            verify(exactly = 0) { service.startForeground(any(), any()) }
        }

    @Test
    fun `stops immediately when bluetooth connect permission is missing`() =
        runTest {
            every { BlePermissionSupport.hasBluetoothConnectPermission(any()) } returns false

            val intent = mockk<Intent>(relaxed = true)
            every { intent.action } returns SunriseService.ACTION_START_SUNRISE
            every { intent.getStringArrayListExtra(SunriseService.EXTRA_BULB_MACS) } returns
                arrayListOf("AA:BB:CC:DD:EE:FF")
            every { intent.getIntArrayExtra(SunriseService.EXTRA_TARGET_RS) } returns intArrayOf(255)
            every { intent.getIntArrayExtra(SunriseService.EXTRA_TARGET_GS) } returns intArrayOf(240)
            every { intent.getIntArrayExtra(SunriseService.EXTRA_TARGET_BS) } returns intArrayOf(210)

            val result = service.onStartCommand(intent, 0, 42)

            assertEquals(Service.START_NOT_STICKY, result)
            verify(exactly = 1) { service.stopSelf(42) }
            verify(exactly = 0) { service.startForeground(any(), any()) }
        }

    @Test
    fun `ignores intent when ramp is already running for the same alarm`() = 
        runTest {
            every { BlePermissionSupport.hasBluetoothConnectPermission(any()) } returns true

            val intent = mockk<Intent>(relaxed = true)
            every { intent.action } returns SunriseService.ACTION_START_SUNRISE
            every { intent.getStringArrayListExtra(SunriseService.EXTRA_BULB_MACS) } returns 
                arrayListOf("AA:BB:CC:DD:EE:FF")
            every { intent.getIntArrayExtra(SunriseService.EXTRA_TARGET_RS) } returns intArrayOf(255)
            every { intent.getIntArrayExtra(SunriseService.EXTRA_TARGET_GS) } returns intArrayOf(240)
            every { intent.getIntArrayExtra(SunriseService.EXTRA_TARGET_BS) } returns intArrayOf(210)
            
            val futureAlarmMs = System.currentTimeMillis() + 100000L
            every { intent.getLongExtra(SunriseService.EXTRA_ORIGINAL_ALARM_MS, -1L) } returns futureAlarmMs

            val result1 = service.onStartCommand(intent, 0, 1)
            assertEquals(Service.START_NOT_STICKY, result1)
            verify(exactly = 1) { service.startForeground(any(), any()) }

            val result2 = service.onStartCommand(intent, 0, 2)
            assertEquals(Service.START_NOT_STICKY, result2)
            
            verify { Log.i(any(), "Rampe déjà en cours pour cette alarme, on ignore le redémarrage.") }
            verify(exactly = 1) { service.startForeground(any(), any()) }
        }

    @Test
    fun `restarts ramp when receiving intent for a different alarm`() = 
        runTest {
            every { BlePermissionSupport.hasBluetoothConnectPermission(any()) } returns true

            val intent1 = mockk<Intent>(relaxed = true)
            every { intent1.action } returns SunriseService.ACTION_START_SUNRISE
            every { intent1.getStringArrayListExtra(SunriseService.EXTRA_BULB_MACS) } returns 
                arrayListOf("AA:BB:CC:DD:EE:FF")
            every { intent1.getIntArrayExtra(SunriseService.EXTRA_TARGET_RS) } returns intArrayOf(255)
            every { intent1.getIntArrayExtra(SunriseService.EXTRA_TARGET_GS) } returns intArrayOf(240)
            every { intent1.getIntArrayExtra(SunriseService.EXTRA_TARGET_BS) } returns intArrayOf(210)
            
            val futureAlarm1 = System.currentTimeMillis() + 100000L
            every { intent1.getLongExtra(SunriseService.EXTRA_ORIGINAL_ALARM_MS, -1L) } returns futureAlarm1

            val intent2 = mockk<Intent>(relaxed = true)
            every { intent2.action } returns SunriseService.ACTION_START_SUNRISE
            every { intent2.getStringArrayListExtra(SunriseService.EXTRA_BULB_MACS) } returns 
                arrayListOf("AA:BB:CC:DD:EE:FF")
            every { intent2.getIntArrayExtra(SunriseService.EXTRA_TARGET_RS) } returns intArrayOf(255)
            every { intent2.getIntArrayExtra(SunriseService.EXTRA_TARGET_GS) } returns intArrayOf(240)
            every { intent2.getIntArrayExtra(SunriseService.EXTRA_TARGET_BS) } returns intArrayOf(210)
            
            val futureAlarm2 = System.currentTimeMillis() + 200000L
            every { intent2.getLongExtra(SunriseService.EXTRA_ORIGINAL_ALARM_MS, -1L) } returns futureAlarm2 

            service.onStartCommand(intent1, 0, 1)
            
            service.onStartCommand(intent2, 0, 2)

            verify(exactly = 0) { Log.i(any(), "Rampe déjà en cours pour cette alarme, on ignore le redémarrage.") }
            verify(exactly = 2) { service.startForeground(any(), any()) }
        }
}
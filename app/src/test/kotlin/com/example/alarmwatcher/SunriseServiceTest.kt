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

class SunriseServiceTest {
    private val applicationContext = mockk<Context>(relaxed = true)
    private val service = spyk(SunriseService(), recordPrivateCalls = true)

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        mockkObject(BlePermissionSupport)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0
        every { service.applicationContext } returns applicationContext
        every { service.stopSelf(any()) } returns Unit
        every { service.startForeground(any(), any()) } returns Unit
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
}

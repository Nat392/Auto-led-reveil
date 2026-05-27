package com.example.alarmwatcher

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

class AlarmTestContextWrapper(base: Context) : ContextWrapper(base) {
    val startedServices = mutableListOf<Intent>()

    override fun startForegroundService(service: Intent?): ComponentName? {
        if (service != null) startedServices.add(service)
        return service?.component
    }

    override fun startService(service: Intent?): ComponentName? {
        if (service != null) startedServices.add(service)
        return service?.component
    }
}

@RunWith(AndroidJUnit4::class)
class AlarmTriggerReceiverTest {
    @Before
    fun setUp() {
        mockkObject(ZenggeBulbController)
        coEvery { ZenggeBulbController.applyScene(any(), any(), any(), any(), any(), any(), any()) } returns true

        mockkObject(BlePermissionSupport)
        every { BlePermissionSupport.hasBluetoothConnectPermission(any()) } returns true

        val realZone =
            SunriseBulbZone(
                label = "Test Zone",
                macAddress = "00:11:22:33:44:55",
                sunriseR = 255,
                sunriseG = 200,
                sunriseB = 100,
                sunsetR = 200,
                sunsetG = 150,
                sunsetB = 50,
            )

        SunriseZoneConfig.testZones = listOf(realZone)
    }

    @After
    fun tearDown() {
        try {
            unmockkObject(ZenggeBulbController)
        } catch (_: Throwable) {
        }
        try {
            unmockkObject(BlePermissionSupport)
        } catch (_: Throwable) {
        }

        SunriseZoneConfig.testZones = null
    }

    @Test
    fun testAlarmTriggerReceiverStartsSunriseService() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val testContext = AlarmTestContextWrapper(baseContext)

        val receiver = AlarmTriggerReceiver()

        val alarmIntent =
            Intent(testContext, AlarmTriggerReceiver::class.java).apply {
                putExtra("original_alarm_ms", 123456789L)
            }

        receiver.onReceive(testContext, alarmIntent)

        assertEquals("Le service n'a pas été lancé", 1, testContext.startedServices.size)
        val serviceIntent = testContext.startedServices.first()

        assertEquals(SunriseService::class.java.name, serviceIntent.component?.className)
        assertEquals(SunriseService.ACTION_START_SUNRISE, serviceIntent.action)
        assertEquals(123456789L, serviceIntent.getLongExtra(SunriseService.EXTRA_ORIGINAL_ALARM_MS, 0L))
    }
}

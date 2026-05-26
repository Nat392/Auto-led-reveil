package com.example.alarmwatcher

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmTriggerReceiverTest {

    @Before
    fun setUp() {
        // Mock du contr?leur BLE pour ?tre s?r qu'il n'essaie pas d'agir
        mockkObject(ZenggeBulbController)
        coEvery { ZenggeBulbController.applyScene(any(), any(), any(), any(), any(), any(), any()) } returns true
        
        mockkObject(BlePermissionSupport)
        every { BlePermissionSupport.hasBluetoothConnectPermission(any()) } returns true
        
        mockkObject(SunriseZoneConfig)
        val mockZone = mockk<SunriseBulbZone>(relaxed = true)
        every { mockZone.macAddress } returns "00:11:22:33:44:55"
        every { mockZone.sunriseR } returns 255
        every { mockZone.sunriseG } returns 200
        every { mockZone.sunriseB } returns 100

        every { SunriseZoneConfig.configuredZones() } returns listOf(mockZone)
    }

    @After
    fun tearDown() {
        try {
            unmockkObject(ZenggeBulbController)
        } catch (_: Throwable) {}
        try {
            unmockkObject(BlePermissionSupport)
        } catch (_: Throwable) {}
        try {
            unmockkObject(SunriseZoneConfig)
        } catch (_: Throwable) {}
    }

    @Test
    fun testAlarmTriggerReceiverStartsSunriseService() {
        // Pr?parer un intent simulant un d?clenchement d'alarme
        val spyContext = spyk(ApplicationProvider.getApplicationContext<Context>())
        val receiver = AlarmTriggerReceiver()
        val alarmIntent = Intent(spyContext, AlarmTriggerReceiver::class.java).apply {
            putExtra("original_alarm_ms", 123456789L)
        }

        // Action : on appelle manuellement le receiver avec le context espionn?
        receiver.onReceive(spyContext, alarmIntent)

        // V?rification : s'assure qu'un Intent visant ? d?marrer SunriseService a ?t? ?mis
        verify {
            spyContext.startForegroundService(withArg { serviceIntent ->
                assertEquals(SunriseService::class.java.name, serviceIntent.component?.className)
                assertEquals(SunriseService.ACTION_START_SUNRISE, serviceIntent.action)
                assertEquals(123456789L, serviceIntent.getLongExtra("original_alarm_ms", 0L))
            })
        }
    }
}

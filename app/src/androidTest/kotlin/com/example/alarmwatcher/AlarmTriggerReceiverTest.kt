package com.example.alarmwatcher

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// 1. Notre faux contexte qui capture les lancements de service sans crasher
class AlarmTestContextWrapper(base: Context) : ContextWrapper(base) {
    val startedServices = mutableListOf<Intent>()
    override fun startForegroundService(service: Intent?): ComponentName? {
        if (service != null) {
            startedServices.add(service)
        }
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
        
        mockkObject(SunriseZoneConfig)
        
        // On instancie la vraie data class avec tous les paramètres requis par ton modèle
        val realZone = SunriseBulbZone(
            label = "Test Zone",
            macAddress = "00:11:22:33:44:55",
            sunriseR = 255,
            sunriseG = 200,
            sunriseB = 100,
            sunsetR = 200,
            sunsetG = 150,
            sunsetB = 50
        )

        every { SunriseZoneConfig.configuredZones() } returns listOf(realZone)
    }

    @After
    fun tearDown() {
        try { unmockkObject(ZenggeBulbController) } catch (_: Throwable) {}
        try { unmockkObject(BlePermissionSupport) } catch (_: Throwable) {}
        try { unmockkObject(SunriseZoneConfig) } catch (_: Throwable) {}
    }

    @Test
    fun testAlarmTriggerReceiverStartsSunriseService() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val testContext = AlarmTestContextWrapper(baseContext) // Utilisation de notre wrapper natif

        val receiver = AlarmTriggerReceiver()
        
        val alarmIntent = Intent(testContext, AlarmTriggerReceiver::class.java).apply {
            putExtra("original_alarm_ms", 123456789L)
        }

        receiver.onReceive(testContext, alarmIntent)

        // 2. Vérification classique avec JUnit, MockK n'intervient plus ici !
        assertEquals("Le service n'a pas été lancé", 1, testContext.startedServices.size)
        val serviceIntent = testContext.startedServices.first()
        
        assertEquals(SunriseService::class.java.name, serviceIntent.component?.className)
        assertEquals(SunriseService.ACTION_START_SUNRISE, serviceIntent.action)
        assertEquals(123456789L, serviceIntent.getLongExtra("original_alarm_ms", 0L))
    }
}
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

class SunsetTestContextWrapper(base: Context) : ContextWrapper(base) {
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
class SunsetAutomationReceiverTest {

    @Before
    fun setUp() {
        mockkObject(ZenggeBulbController)
        coEvery { ZenggeBulbController.applyScene(any(), any(), any(), any(), any(), any(), any()) } returns true
        
        mockkObject(BlePermissionSupport)
        every { BlePermissionSupport.hasBluetoothConnectPermission(any()) } returns true
    }

    @After
    fun tearDown() {
        try { unmockkObject(ZenggeBulbController) } catch (_: Throwable) {}
        try { unmockkObject(BlePermissionSupport) } catch (_: Throwable) {}
    }

    @Test
    fun testSunsetAutomationReceiverStartsSunsetSceneService() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val testContext = SunsetTestContextWrapper(baseContext)

        val receiver = SunsetAutomationReceiver()
        
        val sunsetIntent = Intent(testContext, SunsetAutomationReceiver::class.java).apply {
            action = SunsetAutomationScheduler.ACTION_APPLY_SCENE
            putExtra(SunsetAutomationScheduler.EXTRA_TARGET_ZONE, "BUREAU")
        }

        receiver.onReceive(testContext, sunsetIntent)

        assertEquals("Le service n'a pas été lancé", 1, testContext.startedServices.size)
        val serviceIntent = testContext.startedServices.first()

        assertEquals(SunsetSceneService::class.java.name, serviceIntent.component?.className)
        assertEquals(SunsetAutomationScheduler.ACTION_APPLY_SCENE, serviceIntent.action)
        assertEquals("BUREAU", serviceIntent.getStringExtra(SunsetAutomationScheduler.EXTRA_TARGET_ZONE))
    }
}
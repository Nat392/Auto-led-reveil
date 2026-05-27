package com.example.alarmwatcher

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
        try {
            unmockkObject(ZenggeBulbController)
        } catch (_: Throwable) {}
        try {
            unmockkObject(BlePermissionSupport)
        } catch (_: Throwable) {}
    }

    @Test
    fun testSunsetAutomationReceiverStartsSunsetSceneService() {
        val mockContext = mockk<Context>(relaxed = true)
        val receiver = SunsetAutomationReceiver()
        
        val realContext = ApplicationProvider.getApplicationContext<Context>()
        val sunsetIntent = Intent(realContext, SunsetAutomationReceiver::class.java).apply {
            action = SunsetAutomationScheduler.ACTION_APPLY_SCENE
            putExtra(SunsetAutomationScheduler.EXTRA_TARGET_ZONE, "BUREAU")
        }

        receiver.onReceive(mockContext, sunsetIntent)

        verify {
            mockContext.startForegroundService(withArg { serviceIntent ->
                assertEquals(SunsetSceneService::class.java.name, serviceIntent.component?.className)
                assertEquals(SunsetAutomationScheduler.ACTION_APPLY_SCENE, serviceIntent.action)
                assertEquals("BUREAU", serviceIntent.getStringExtra(SunsetAutomationScheduler.EXTRA_TARGET_ZONE))
            })
        }
    }
}

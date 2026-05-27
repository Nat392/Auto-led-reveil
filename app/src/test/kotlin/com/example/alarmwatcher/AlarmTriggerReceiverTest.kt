package com.example.alarmwatcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AlarmTriggerReceiverTest {
    private val context = mockk<Context>()
    private val incomingIntent = mockk<Intent>()
    private val serviceIntent = mockk<Intent>(relaxed = true)
    private val defaultIntentFactory = AlarmTriggerReceiver.intentFactory
    private val receiver = AlarmTriggerReceiver()

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        mockkObject(BlePermissionSupport)
        mockkObject(SunriseZoneConfig)
        mockkObject(NotificationHelper)

        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0
        every { BlePermissionSupport.hasBluetoothConnectPermission(any()) } returns true
        every { NotificationHelper.showFallbackNotification(any()) } returns Unit
        every { context.startForegroundService(any()) } returns mockk<ComponentName>(relaxed = true)
        every { context.startService(any()) } returns mockk<ComponentName>(relaxed = true)

        every { serviceIntent.setAction(any()) } returns serviceIntent
        every { serviceIntent.putStringArrayListExtra(any(), any()) } returns serviceIntent
        every { serviceIntent.putExtra(any<String>(), any<IntArray>()) } returns serviceIntent
        every { serviceIntent.putExtra(any<String>(), any<Long>()) } returns serviceIntent
        AlarmTriggerReceiver.intentFactory = { _, _ -> serviceIntent }

        every { incomingIntent.getLongExtra("original_alarm_ms", -1L) } returns 1_700_000_900_000L
        every {
            incomingIntent.getLongExtra(
                SunriseService.EXTRA_DURATION_MS,
                AlarmScheduler.PREWARN_MS,
            )
        } returns 90_000L
    }

    @AfterEach
    fun tearDown() {
        AlarmTriggerReceiver.intentFactory = defaultIntentFactory
        unmockkAll()
    }

    @Test
    fun `shows the fallback notification when bluetooth permission is missing`() {
        every { BlePermissionSupport.hasBluetoothConnectPermission(context) } returns false

        receiver.onReceive(context, incomingIntent)

        verify(exactly = 1) { NotificationHelper.showFallbackNotification(context) }
        verify(exactly = 0) { context.startForegroundService(any()) }
        verify(exactly = 0) { context.startService(any()) }
    }

    @Test
    fun `shows the fallback notification when no sunrise zones are configured`() {
        every { SunriseZoneConfig.configuredZones() } returns emptyList()

        receiver.onReceive(context, incomingIntent)

        verify(exactly = 1) { NotificationHelper.showFallbackNotification(context) }
        verify(exactly = 0) { context.startForegroundService(any()) }
        verify(exactly = 0) { context.startService(any()) }
    }

    @Test
    fun `starts the sunrise service with the configured zones and extras`() {
        val bureau =
            SunriseBulbZone(
                label = "Bureau",
                macAddress = "AA:BB:CC:DD:EE:FF",
                sunriseR = 220,
                sunriseG = 240,
                sunriseB = 255,
                sunsetR = 40,
                sunsetG = 10,
                sunsetB = 0,
                whiteChannel = 0,
                brightnessPercent = 100,
            )
        val chambre =
            SunriseBulbZone(
                label = "Chambre",
                macAddress = "11:22:33:44:55:66",
                sunriseR = 255,
                sunriseG = 230,
                sunriseB = 210,
                sunsetR = 20,
                sunsetG = 0,
                sunsetB = 0,
                whiteChannel = 0,
                brightnessPercent = 100,
            )
        every { SunriseZoneConfig.configuredZones() } returns listOf(bureau, chambre)

        receiver.onReceive(context, incomingIntent)

        verify(exactly = 0) { NotificationHelper.showFallbackNotification(context) }
        verify(exactly = 1) { serviceIntent.setAction(SunriseService.ACTION_START_SUNRISE) }
        verify(exactly = 1) {
            serviceIntent.putStringArrayListExtra(
                SunriseService.EXTRA_BULB_MACS,
                arrayListOf(bureau.macAddress, chambre.macAddress),
            )
        }
        verify(exactly = 1) {
            serviceIntent.putExtra(
                SunriseService.EXTRA_TARGET_RS,
                intArrayOf(bureau.sunriseR, chambre.sunriseR),
            )
        }
        verify(exactly = 1) {
            serviceIntent.putExtra(
                SunriseService.EXTRA_TARGET_GS,
                intArrayOf(bureau.sunriseG, chambre.sunriseG),
            )
        }
        verify(exactly = 1) {
            serviceIntent.putExtra(
                SunriseService.EXTRA_TARGET_BS,
                intArrayOf(bureau.sunriseB, chambre.sunriseB),
            )
        }
        verify(exactly = 1) { serviceIntent.putExtra(SunriseService.EXTRA_ORIGINAL_ALARM_MS, 1_700_000_900_000L) }
        verify(exactly = 1) { serviceIntent.putExtra(SunriseService.EXTRA_DURATION_MS, 90_000L) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            verify(exactly = 1) { context.startForegroundService(serviceIntent) }
            verify(exactly = 0) { context.startService(any()) }
        } else {
            verify(exactly = 1) { context.startService(serviceIntent) }
            verify(exactly = 0) { context.startForegroundService(any()) }
        }
    }

    @Test
    fun `falls back when starting the sunrise service throws`() {
        every { SunriseZoneConfig.configuredZones() } returns
            listOf(
                SunriseBulbZone(
                    label = "Bureau",
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    sunriseR = 220,
                    sunriseG = 240,
                    sunriseB = 255,
                    sunsetR = 40,
                    sunsetG = 10,
                    sunsetB = 0,
                    whiteChannel = 0,
                    brightnessPercent = 100,
                ),
            )
        every { context.startForegroundService(any()) } throws IllegalStateException("boom")
        every { context.startService(any()) } throws IllegalStateException("boom")

        receiver.onReceive(context, incomingIntent)

        verify(exactly = 1) { NotificationHelper.showFallbackNotification(context) }
    }
}

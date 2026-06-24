package com.example.alarmwatcher

import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.alarmwatcher.settings.AppSettings
import com.example.alarmwatcher.settings.AppSettingsCache
import io.mockk.coEvery
import io.mockk.coVerify
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

class DaylightHarvestingFadeServiceTest {
    private val applicationContext = mockk<Context>(relaxed = true)
    private val service = spyk(DaylightHarvestingFadeService())

    private val chambre =
        SunriseBulbZone(
            label = "Chambre",
            macAddress = "11:22:33:44:55:66",
            sunriseR = 255,
            sunriseG = 230,
            sunriseB = 210,
            sunsetR = 180,
            sunsetG = 50,
            sunsetB = 0,
            whiteChannel = 0,
            brightnessPercent = 100,
        )
    private val cuisine =
        SunriseBulbZone(
            label = "Cuisine",
            macAddress = "22:33:44:55:66:77",
            sunriseR = 220,
            sunriseG = 240,
            sunriseB = 255,
            sunsetR = 255,
            sunsetG = 140,
            sunsetB = 0,
            whiteChannel = 0,
            brightnessPercent = 100,
        )

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        mockkObject(SunriseZoneConfig)
        mockkObject(ZenggeBulbController)
        mockkObject(DiscordCrashReporter)
        mockkObject(DaylightHarvestingStateStore)
        AppSettingsCache.current = AppSettings()
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0
        every { DiscordCrashReporter.reportNonFatal(any(), any(), any()) } returns mockk(relaxed = true)
        every { service.applicationContext } returns applicationContext
        every { service.stopSelf(any()) } returns Unit
        every { service.stopForeground(any<Int>()) } returns Unit
        every { service.startForeground(any(), any()) } returns Unit
        every { SunriseZoneConfig.chambre } returns chambre
        every { SunriseZoneConfig.cuisine } returns cuisine
        every { DaylightHarvestingStateStore.getState(any(), any()) } returns
            DaylightHarvestingStateStore.State(active = true, currentRgb = Triple(0, 0, 0))
        every { DaylightHarvestingStateStore.saveCurrentRgb(any(), any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `stops immediately when no zone keys are provided`() {
        val intent =
            mockk<Intent>(relaxed = true) {
                every { getStringArrayListExtra(DaylightHarvestingFadeService.EXTRA_ZONE_KEYS) } returns null
            }

        val result = service.onStartCommand(intent, 0, 90)

        assertEquals(Service.START_NOT_STICKY, result)
        verify(exactly = 1) { service.stopSelf(90) }
        verify(exactly = 0) { service.startForeground(any(), any()) }
    }

    @Test
    fun `fades every requested zone toward its computed target`() =
        runTest {
            coEvery {
                ZenggeBulbController.applyScene(any(), chambre.macAddress, any(), any(), any(), 0, 100)
            } returns true
            coEvery {
                ZenggeBulbController.applyScene(any(), cuisine.macAddress, any(), any(), any(), 0, 100)
            } returns true

            DaylightHarvestingFadeService.runFade(
                applicationContext,
                listOf(SunsetAutomationScheduler.ZONE_CHAMBRE, SunsetAutomationScheduler.ZONE_CUISINE),
                shortwaveRadiation = 0.0,
            )

            coVerify(atLeast = 1) {
                ZenggeBulbController.applyScene(any(), chambre.macAddress, any(), any(), any(), 0, 100)
            }
            coVerify(atLeast = 1) {
                ZenggeBulbController.applyScene(any(), cuisine.macAddress, any(), any(), any(), 0, 100)
            }
        }

    @Test
    fun `skips a zone whose target already matches the current color`() =
        runTest {
            every { DaylightHarvestingStateStore.getState(any(), SunsetAutomationScheduler.ZONE_CHAMBRE) } returns
                DaylightHarvestingStateStore.State(active = true, currentRgb = Triple(255, 230, 210))

            DaylightHarvestingFadeService.runFade(
                applicationContext,
                listOf(SunsetAutomationScheduler.ZONE_CHAMBRE),
                shortwaveRadiation = 0.0,
            )

            coVerify(exactly = 0) {
                ZenggeBulbController.applyScene(any(), chambre.macAddress, any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `skips an unknown zone key without throwing`() =
        runTest {
            DaylightHarvestingFadeService.runFade(applicationContext, listOf("inconnu"), shortwaveRadiation = 0.0)

            coVerify(exactly = 0) {
                ZenggeBulbController.applyScene(any(), any(), any(), any(), any(), any(), any())
            }
        }
}

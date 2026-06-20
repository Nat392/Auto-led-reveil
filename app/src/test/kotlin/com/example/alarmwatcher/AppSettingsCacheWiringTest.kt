package com.example.alarmwatcher

import android.content.Context
import android.util.Log
import com.example.alarmwatcher.settings.AppSettings
import com.example.alarmwatcher.settings.AppSettingsCache
import com.example.alarmwatcher.settings.RgbColor
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Vérifie que la logique métier réagit aux réglages utilisateur (Catégorie A) via
 * [AppSettingsCache], et pas seulement aux valeurs par défaut.
 */
class AppSettingsCacheWiringTest {
    @AfterEach
    fun tearDown() {
        AppSettingsCache.current = AppSettings()
        unmockkAll()
    }

    @Test
    fun `AlarmScheduler PREWARN_MS reflects the configured pre-warn duration`() {
        AppSettingsCache.current = AppSettings(preWarnMinutes = 45)

        assertEquals(45 * 60_000L, AlarmScheduler.PREWARN_MS)
    }

    @Test
    fun `NightFadeTimingSupport uses the configured lead time and morning window`() {
        AppSettingsCache.current =
            AppSettings(
                nightFadeLeadTimeMinutes = 60,
                nightFadeMorningStartMinutes = 6 * 60,
                nightFadeMorningEndMinutes = 10 * 60,
            )
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.of(2026, 6, 10)
        val tomorrow = today.plusDays(1)
        val now = today.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()
        val eveningStartMs = today.atTime(21, 0).atZone(zoneId).toInstant().toEpochMilli()
        val alarmTimeMs = tomorrow.atTime(8, 0).atZone(zoneId).toInstant().toEpochMilli()

        val result = NightFadeTimingSupport.computeScheduleOrNull(alarmTimeMs, eveningStartMs, now)

        assertNotNull(result)
        assertEquals(alarmTimeMs - 60 * 60_000L, result?.targetEndTimeMs)
    }

    @Test
    fun `NightFadeTimingSupport rejects alarms outside the configured morning window`() {
        AppSettingsCache.current =
            AppSettings(
                nightFadeMorningStartMinutes = 6 * 60,
                nightFadeMorningEndMinutes = 7 * 60,
            )
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.of(2026, 6, 10)
        val tomorrow = today.plusDays(1)
        val now = today.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()
        val eveningStartMs = today.atTime(21, 0).atZone(zoneId).toInstant().toEpochMilli()
        // 8h00 est hors de la fenêtre configurée (6h00-7h00).
        val alarmTimeMs = tomorrow.atTime(8, 0).atZone(zoneId).toInstant().toEpochMilli()

        val result = NightFadeTimingSupport.computeScheduleOrNull(alarmTimeMs, eveningStartMs, now)

        assertNull(result)
    }

    @Test
    fun `SunriseZoneConfig bureau reflects the configured sunrise and sunset colors`() {
        AppSettingsCache.current =
            AppSettings(
                bureauSunriseColor = RgbColor(10, 20, 30),
                bureauSunsetColor = RgbColor(40, 50, 60),
            )

        val bureau = SunriseZoneConfig.bureau

        assertEquals(Triple(10, 20, 30), Triple(bureau.sunriseR, bureau.sunriseG, bureau.sunriseB))
        assertEquals(Triple(40, 50, 60), Triple(bureau.sunsetR, bureau.sunsetG, bureau.sunsetB))
    }

    @Test
    fun `SunsetAutomationScheduler computeNextRefreshAtMillis uses the configured refresh time`() {
        AppSettingsCache.current = AppSettings(sunsetRefreshHour = 1, sunsetRefreshMinute = 30)
        val zoneId = ZoneId.of("UTC")
        val now =
            LocalDate.of(2026, 5, 24)
                .atTime(0, 0)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()

        val refreshAt = SunsetAutomationScheduler.computeNextRefreshAtMillis(now, zoneId)
        val refreshInstant = java.time.Instant.ofEpochMilli(refreshAt).atZone(zoneId)

        assertEquals(LocalDate.of(2026, 5, 24), refreshInstant.toLocalDate())
        assertEquals(LocalTime.of(1, 30), refreshInstant.toLocalTime())
    }

    @Test
    fun `OpenMeteoClient requests the configured coordinates`() =
        runTest {
            mockkStatic(Log::class)
            every { Log.w(any(), any<String>()) } returns 0
            every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0

            AppSettingsCache.current = AppSettings(latitude = 12.34, longitude = 56.78)
            val defaultConnectionFactory = OpenMeteoClient.openMeteoConnectionFactory
            val requestedUrls = mutableListOf<String>()
            val body = """{ "current": { "shortwave_radiation": 0.0 } }"""
            try {
                OpenMeteoClient.openMeteoConnectionFactory = { urlString ->
                    requestedUrls.add(urlString)
                    mockk<HttpURLConnection>(relaxed = true).also { connection ->
                        every { connection.responseCode } returns HttpURLConnection.HTTP_OK
                        every { connection.inputStream } returns ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
                        every { connection.disconnect() } returns Unit
                    }
                }

                OpenMeteoClient.fetchCurrentConditions(mockk<Context>())

                assertEquals(1, requestedUrls.size)
                assertEquals(true, requestedUrls.single().contains("latitude=12.34"))
                assertEquals(true, requestedUrls.single().contains("longitude=56.78"))
            } finally {
                OpenMeteoClient.openMeteoConnectionFactory = defaultConnectionFactory
            }
        }
}

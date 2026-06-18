package com.example.alarmwatcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SunsetAutomationSchedulerTest {
    private val context = mockk<Context>()
    private val alarmManager = mockk<AlarmManager>()
    private val pendingIntent = mockk<PendingIntent>(relaxed = true)
    private val defaultConnectionFactory = SunsetAutomationScheduler.sunsetConnectionFactory
    private val defaultIntentFactory = SunsetAutomationScheduler.intentFactory

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        mockkStatic(PendingIntent::class)
        mockkObject(BlePermissionSupport)
        mockkObject(SunriseZoneConfig)
        mockkObject(ZenggeBulbController)
        mockkObject(SunsetTimesStore)
        mockkObject(AlarmMonitor)
        mockkObject(DaylightHarvestingStateStore)
        mockkObject(DiscordCrashReporter)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0
        every { DiscordCrashReporter.reportNonFatal(any(), any(), any()) } returns mockk(relaxed = true)
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { context.applicationContext } returns context
        every { alarmManager.canScheduleExactAlarms() } returns true
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns pendingIntent
        every { pendingIntent.cancel() } returns Unit
        every { alarmManager.cancel(any<PendingIntent>()) } returns Unit
        every { BlePermissionSupport.hasBluetoothConnectPermission(any()) } returns true
        every { SunsetTimesStore.save(any(), any(), any(), any()) } returns Unit
        every { AlarmMonitor.scanNextAlarmAndSchedule(any()) } returns Unit
        every { DaylightHarvestingStateStore.deactivate(any()) } returns Unit
        SunsetAutomationScheduler.sunsetConnectionFactory = defaultConnectionFactory
        SunsetAutomationScheduler.intentFactory = defaultIntentFactory
    }

    @AfterEach
    fun tearDown() {
        SunsetAutomationScheduler.sunsetConnectionFactory = defaultConnectionFactory
        SunsetAutomationScheduler.intentFactory = defaultIntentFactory
        unmockkAll()
    }

    @Test
    fun `fetchSunsetInstant parses the sunset timestamp from the JSON response`() =
        runTest {
            val body =
                """
                {
                  "results": {
                    "sunset": "2026-05-23T18:47:00+00:00"
                  }
                }
                """.trimIndent()
            val connection = mockk<HttpURLConnection>(relaxed = true)

            SunsetAutomationScheduler.sunsetConnectionFactory = { connection }
            every { connection.responseCode } returns HttpURLConnection.HTTP_OK
            every { connection.inputStream } returns ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
            every { connection.disconnect() } returns Unit

            val sunsetInstant = SunsetAutomationScheduler.fetchSunsetInstant(context)

            assertEquals(Instant.parse("2026-05-23T18:47:00Z"), sunsetInstant)
            verify(exactly = 1) { connection.disconnect() }
        }

    @Test
    fun `fetchSunsetInstant returns null when the API responds with an error status`() =
        runTest {
            val connection = mockk<HttpURLConnection>(relaxed = true)

            SunsetAutomationScheduler.sunsetConnectionFactory = { connection }
            every { connection.responseCode } returns 503
            every { connection.disconnect() } returns Unit

            val sunsetInstant = SunsetAutomationScheduler.fetchSunsetInstant(context)

            assertNull(sunsetInstant)
            verify(exactly = 1) { connection.disconnect() }
        }

    @Test
    fun `fetchSunsetInstant returns null when the payload is malformed`() =
        runTest {
            val connection = mockk<HttpURLConnection>(relaxed = true)

            SunsetAutomationScheduler.sunsetConnectionFactory = { connection }
            every { connection.responseCode } returns HttpURLConnection.HTTP_OK
            every { connection.inputStream } returns
                ByteArrayInputStream(
                    """
                    {
                      "results": {}
                    }
                    """.trimIndent().toByteArray(Charsets.UTF_8),
                )
            every { connection.disconnect() } returns Unit

            val sunsetInstant = SunsetAutomationScheduler.fetchSunsetInstant(context)

            assertNull(sunsetInstant)
            verify(exactly = 1) { connection.disconnect() }
        }

    @Test
    fun `refreshAndSchedule schedules bureau, chambre and cuisine offsets from sunset`() =
        runTest {
            val sunsetInstant = Instant.parse("2030-05-23T20:00:00Z")
            val body =
                """
                {
                  "results": {
                    "sunset": "$sunsetInstant"
                  }
                }
                """.trimIndent()
            val scheduledTimes = mutableListOf<Long>()

            SunsetAutomationScheduler.intentFactory = { _, _ -> mockk(relaxed = true) }
            SunsetAutomationScheduler.sunsetConnectionFactory = {
                mockk<HttpURLConnection>(relaxed = true).also { connection ->
                    every { connection.responseCode } returns HttpURLConnection.HTTP_OK
                    every { connection.inputStream } returns ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
                    every { connection.disconnect() } returns Unit
                }
            }
            every { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) } answers {
                scheduledTimes.add(secondArg())
                Unit
            }

            SunsetAutomationScheduler.refreshAndSchedule(context)

            assertEquals(4, scheduledTimes.size)
            // Bureau et Cuisine partagent le même offset (60 min) : deux occurrences attendues.
            assertEquals(2, scheduledTimes.count { it == sunsetInstant.toEpochMilli() - 60 * 60 * 1000L })
            assertTrue(scheduledTimes.contains(sunsetInstant.toEpochMilli() - 30 * 60 * 1000L))
        }

    @Test
    fun `refreshAndSchedule catches up expired bureau scenes and keeps future alarms`() =
        runTest {
            val now = System.currentTimeMillis()
            val sunsetInstant = Instant.ofEpochMilli(now + 45 * 60 * 1000L)
            val bureau =
                SunriseBulbZone(
                    label = "Bureau",
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    sunriseR = 255,
                    sunriseG = 255,
                    sunriseB = 255,
                    sunsetR = 255,
                    sunsetG = 140,
                    sunsetB = 0,
                    whiteChannel = 0,
                    brightnessPercent = 100,
                )
            val chambre =
                SunriseBulbZone(
                    label = "Chambre",
                    macAddress = "11:22:33:44:55:66",
                    sunriseR = 255,
                    sunriseG = 240,
                    sunriseB = 210,
                    sunsetR = 180,
                    sunsetG = 50,
                    sunsetB = 0,
                    whiteChannel = 0,
                    brightnessPercent = 100,
                )
            // Le rattrapage de la Cuisine (même offset que Bureau, donc lui aussi expiré) ne doit
            // rien faire car la zone n'est pas configurée.
            val cuisine =
                SunriseBulbZone(
                    label = "Cuisine",
                    macAddress = "",
                    sunriseR = 220,
                    sunriseG = 240,
                    sunriseB = 255,
                    sunsetR = 255,
                    sunsetG = 140,
                    sunsetB = 0,
                    whiteChannel = 0,
                    brightnessPercent = 100,
                )
            val body =
                """
                {
                  "results": {
                    "sunset": "$sunsetInstant"
                  }
                }
                """.trimIndent()
            val scheduledTimes = mutableListOf<Long>()

            every { SunriseZoneConfig.bureau } returns bureau
            every { SunriseZoneConfig.chambre } returns chambre
            every { SunriseZoneConfig.cuisine } returns cuisine
            SunsetAutomationScheduler.intentFactory = { _, _ -> mockk(relaxed = true) }
            SunsetAutomationScheduler.sunsetConnectionFactory = {
                mockk<HttpURLConnection>(relaxed = true).also { connection ->
                    every { connection.responseCode } returns HttpURLConnection.HTTP_OK
                    every { connection.inputStream } returns ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
                    every { connection.disconnect() } returns Unit
                }
            }
            every { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) } answers {
                scheduledTimes.add(secondArg())
                Unit
            }
            coEvery {
                ZenggeBulbController.applyScene(
                    any(),
                    bureau.macAddress,
                    bureau.sunsetR,
                    bureau.sunsetG,
                    bureau.sunsetB,
                    bureau.whiteChannel,
                    bureau.brightnessPercent,
                )
            } returns true

            SunsetAutomationScheduler.refreshAndSchedule(context)

            assertEquals(2, scheduledTimes.size)
            assertTrue(scheduledTimes.contains(sunsetInstant.toEpochMilli() - 30 * 60 * 1000L))
            coVerify(exactly = 1) {
                ZenggeBulbController.applyScene(
                    any(),
                    bureau.macAddress,
                    bureau.sunsetR,
                    bureau.sunsetG,
                    bureau.sunsetB,
                    bureau.whiteChannel,
                    bureau.brightnessPercent,
                )
            }
        }

    @Test
    fun `refreshAndSchedule deactivates daylight harvesting for the new day`() =
        runTest {
            val sunsetInstant = Instant.parse("2030-05-23T20:00:00Z")
            val body =
                """
                {
                  "results": {
                    "sunset": "$sunsetInstant"
                  }
                }
                """.trimIndent()

            SunsetAutomationScheduler.intentFactory = { _, _ -> mockk(relaxed = true) }
            SunsetAutomationScheduler.sunsetConnectionFactory = {
                mockk<HttpURLConnection>(relaxed = true).also { connection ->
                    every { connection.responseCode } returns HttpURLConnection.HTTP_OK
                    every { connection.inputStream } returns ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
                    every { connection.disconnect() } returns Unit
                }
            }
            every { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) } returns Unit

            SunsetAutomationScheduler.refreshAndSchedule(context)

            verify(exactly = 1) { DaylightHarvestingStateStore.deactivate(context) }
        }

    @Test
    fun `computeNextRefreshAtMillis rolls to the next day at the exact cutoff`() {
        val zoneId = ZoneId.of("UTC")
        val now =
            LocalDate.of(2026, 5, 24)
                .atTime(0, 5)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()

        val refreshAt = SunsetAutomationScheduler.computeNextRefreshAtMillis(now, zoneId)
        val refreshInstant = Instant.ofEpochMilli(refreshAt).atZone(zoneId)

        assertEquals(LocalDate.of(2026, 5, 25), refreshInstant.toLocalDate())
        assertEquals(LocalTime.of(0, 5), refreshInstant.toLocalTime())
    }

    @Test
    fun `computeNextRefreshAtMillis returns same day at zero hours and five minutes before the cutoff`() {
        val zoneId = ZoneId.of("UTC")
        val now =
            LocalDate.of(2026, 5, 24)
                .atTime(0, 4)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()

        val refreshAt = SunsetAutomationScheduler.computeNextRefreshAtMillis(now, zoneId)
        val refreshInstant = Instant.ofEpochMilli(refreshAt).atZone(zoneId)

        assertEquals(LocalDate.of(2026, 5, 24), refreshInstant.toLocalDate())
        assertEquals(LocalTime.of(0, 5), refreshInstant.toLocalTime())
    }

    @Test
    fun `computeNextRefreshAtMillis rolls over to the next day after the cutoff`() {
        val zoneId = ZoneId.of("UTC")
        val now =
            LocalDate.of(2026, 5, 24)
                .atTime(0, 6)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()

        val refreshAt = SunsetAutomationScheduler.computeNextRefreshAtMillis(now, zoneId)
        val refreshInstant = Instant.ofEpochMilli(refreshAt).atZone(zoneId)

        assertEquals(LocalDate.of(2026, 5, 25), refreshInstant.toLocalDate())
        assertEquals(LocalTime.of(0, 5), refreshInstant.toLocalTime())
    }

    @Test
    fun `refreshAndSchedule retries after a network failure`() =
        runTest {
            val beforeCall = System.currentTimeMillis()
            val scheduledTimes = mutableListOf<Long>()

            SunsetAutomationScheduler.intentFactory = { _, _ -> mockk(relaxed = true) }
            SunsetAutomationScheduler.sunsetConnectionFactory = {
                throw SocketTimeoutException("timeout")
            }
            every { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) } answers {
                scheduledTimes.add(secondArg())
                Unit
            }

            SunsetAutomationScheduler.refreshAndSchedule(context)

            assertEquals(1, scheduledTimes.size)
            assertTrue(scheduledTimes.single() >= beforeCall + 3_600_000L)
            assertTrue(scheduledTimes.single() <= beforeCall + 3_600_000L + 1_000L)
        }
}

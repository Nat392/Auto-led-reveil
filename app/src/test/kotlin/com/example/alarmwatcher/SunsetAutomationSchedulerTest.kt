package com.example.alarmwatcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.canScheduleExactAlarms() } returns true
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns pendingIntent
        every { pendingIntent.cancel() } returns Unit
        every { alarmManager.cancel(any<PendingIntent>()) } returns Unit
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
    fun `fetchSunsetInstant parses the sunset timestamp from the JSON response`() = runTest {
        val body = """
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

        val sunsetInstant = SunsetAutomationScheduler.fetchSunsetInstant()

        assertEquals(Instant.parse("2026-05-23T18:47:00Z"), sunsetInstant)
        verify(exactly = 1) { connection.disconnect() }
    }

    @Test
    fun `refreshAndSchedule schedules bureau and chambre offsets from sunset`() = runTest {
                val sunsetInstant = Instant.parse("2030-05-23T20:00:00Z")
        val body = """
            {
              "results": {
                "sunset": "${sunsetInstant}"
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

        assertEquals(3, scheduledTimes.size)
        assertTrue(scheduledTimes.contains(sunsetInstant.toEpochMilli() - 60 * 60 * 1000L))
        assertTrue(scheduledTimes.contains(sunsetInstant.toEpochMilli() - 30 * 60 * 1000L))
    }

    @Test
    fun `computeNextRefreshAtMillis returns the next day at zero hours and five minutes`() {
        val zoneId = ZoneId.of("UTC")

        val refreshAt = SunsetAutomationScheduler.computeNextRefreshAtMillis(zoneId)
        val refreshInstant = Instant.ofEpochMilli(refreshAt).atZone(zoneId)

        assertEquals(LocalDate.now(zoneId).plusDays(1), refreshInstant.toLocalDate())
        assertEquals(LocalTime.of(0, 5), refreshInstant.toLocalTime())
    }

    @Test
    fun `refreshAndSchedule retries after a network failure`() = runTest {
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
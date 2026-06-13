package com.example.alarmwatcher

import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException

class OpenMeteoClientTest {
    private val defaultConnectionFactory = OpenMeteoClient.openMeteoConnectionFactory

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        OpenMeteoClient.openMeteoConnectionFactory = defaultConnectionFactory
        unmockkAll()
    }

    @Test
    fun `fetchCurrentConditions parses the shortwave radiation from the JSON response`() =
        runTest {
            val body =
                """
                {
                  "current": {
                    "shortwave_radiation": 123.4
                  }
                }
                """.trimIndent()
            val connection = mockk<HttpURLConnection>(relaxed = true)
            OpenMeteoClient.openMeteoConnectionFactory = { connection }
            every { connection.responseCode } returns HttpURLConnection.HTTP_OK
            every { connection.inputStream } returns ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
            every { connection.disconnect() } returns Unit

            val conditions = OpenMeteoClient.fetchCurrentConditions()

            assertEquals(123.4, conditions?.shortwaveRadiation ?: Double.NaN, 0.0001)
            verify(exactly = 1) { connection.disconnect() }
        }

    @Test
    fun `fetchCurrentConditions returns null when the API responds with an error status`() =
        runTest {
            val connection = mockk<HttpURLConnection>(relaxed = true)
            OpenMeteoClient.openMeteoConnectionFactory = { connection }
            every { connection.responseCode } returns 503
            every { connection.disconnect() } returns Unit

            val conditions = OpenMeteoClient.fetchCurrentConditions()

            assertNull(conditions)
            verify(exactly = 1) { connection.disconnect() }
        }

    @Test
    fun `fetchCurrentConditions returns null when the payload is malformed`() =
        runTest {
            val connection = mockk<HttpURLConnection>(relaxed = true)
            OpenMeteoClient.openMeteoConnectionFactory = { connection }
            every { connection.responseCode } returns HttpURLConnection.HTTP_OK
            every { connection.inputStream } returns
                ByteArrayInputStream(
                    """{ "current": {} }""".toByteArray(Charsets.UTF_8),
                )
            every { connection.disconnect() } returns Unit

            val conditions = OpenMeteoClient.fetchCurrentConditions()

            assertNull(conditions)
            verify(exactly = 1) { connection.disconnect() }
        }

    @Test
    fun `fetchCurrentConditions returns null on a network failure`() =
        runTest {
            OpenMeteoClient.openMeteoConnectionFactory = { throw SocketTimeoutException("timeout") }

            val conditions = OpenMeteoClient.fetchCurrentConditions()

            assertNull(conditions)
        }
}

package com.example.alarmwatcher

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DiscordCrashReporterTest {
    private val context = mockk<Context>()
    private val packageManager = mockk<PackageManager>()

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0

        every { context.packageManager } returns packageManager
        every { context.packageName } returns "com.example.alarmwatcher"
        every { packageManager.getPackageInfo("com.example.alarmwatcher", 0) } returns
            PackageInfo().apply {
                versionName = "9.9.9"
            }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `buildPayload assembles the expected fatal embed`() {
        val payload =
            DiscordCrashReporter.buildPayload(
                context = context,
                stacktrace = "ligne 1\nligne 2",
                source = "MainActivity",
                fatal = true,
                hasScreenshot = false,
            )

        assertEquals("Une exception fatale a été capturée.", payload.getString("content"))

        val embed = firstEmbed(payload)
        assertEquals("Crash fatal détecté", embed.getString("title"))
        assertEquals(0xE74C3C, embed.getInt("color"))
        assertEquals("Aucune capture disponible", fieldValue(embed.getJSONArray("fields"), "Screenshot"))
        assertEquals("MainActivity", fieldValue(embed.getJSONArray("fields"), "Source"))
        assertEquals("9.9.9", fieldValue(embed.getJSONArray("fields"), "App"))
    }

    @Test
    fun `buildPayload chunks long stacktraces in 900 character blocks and keeps the discord field limit`() {
        val stacktrace = "S".repeat(30 * 900 + 137)

        val payload =
            DiscordCrashReporter.buildPayload(
                context = context,
                stacktrace = stacktrace,
                source = null,
                fatal = false,
                hasScreenshot = true,
            )

        val fields = firstEmbed(payload).getJSONArray("fields")
        val stackFields = jsonObjects(fields).filter { it.getString("name").startsWith("Stacktrace ") }

        assertTrue(fields.length() <= 25)
        assertEquals(20, stackFields.size)
        assertTrue(stackFields.all { it.getString("value").length <= 900 })
        assertEquals("Pièce jointe envoyée", fieldValue(fields, "Screenshot"))
    }

    @Test
    fun `buildDebugPayload assembles the debug embed`() {
        val payload =
            DiscordCrashReporter.buildDebugPayload(
                context = context,
                source = "Startup",
                details = "Détail de debug",
            )

        assertEquals("Un log de debug au démarrage a été capturé.", payload.getString("content"))

        val embed = firstEmbed(payload)
        assertEquals("Debug launch log", embed.getString("title"))
        assertEquals("Startup", fieldValue(embed.getJSONArray("fields"), "Source"))
    }

    @Test
    fun `buildDebugPayload chunks long debug details without exceeding the discord limit`() {
        val details = "D".repeat(30 * 900 + 11)

        val payload =
            DiscordCrashReporter.buildDebugPayload(
                context = context,
                source = "Startup",
                details = details,
            )

        val fields = firstEmbed(payload).getJSONArray("fields")
        val debugFields = jsonObjects(fields).filter { it.getString("name").startsWith("Debug ") }

        assertTrue(fields.length() <= 25)
        assertEquals(20, debugFields.size)
        assertTrue(debugFields.all { it.getString("value").length <= 900 })
    }

    private fun firstEmbed(payload: JSONObject): JSONObject {
        return payload.getJSONArray("embeds").getJSONObject(0)
    }

    private fun fieldValue(
        fields: JSONArray,
        fieldName: String,
    ): String {
        return jsonObjects(fields)
            .first { it.getString("name") == fieldName }
            .getString("value")
    }

    private fun jsonObjects(fields: JSONArray): List<JSONObject> {
        return (0 until fields.length()).map { index -> fields.getJSONObject(index) }
    }
}

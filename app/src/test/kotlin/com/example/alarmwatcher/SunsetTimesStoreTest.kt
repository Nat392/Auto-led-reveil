package com.example.alarmwatcher

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SunsetTimesStoreTest {
    private val context = mockk<Context>()
    private val prefs = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    private fun setUpPrefs() {
        every { context.applicationContext } returns context
        every { context.getSharedPreferences("sunset_times", Context.MODE_PRIVATE) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.apply() } returns Unit
    }

    @Test
    fun `save persists bureau and chambre evening start times`() {
        setUpPrefs()

        SunsetTimesStore.save(context, bureauEveningStartMs = 1_000L, chambreEveningStartMs = 2_000L)

        verify(exactly = 1) { editor.putLong("bureau_evening_start_ms", 1_000L) }
        verify(exactly = 1) { editor.putLong("chambre_evening_start_ms", 2_000L) }
        verify(exactly = 1) { editor.apply() }
    }

    @Test
    fun `getEveningStartMs returns the stored value for the bureau zone`() {
        setUpPrefs()
        every { prefs.getLong("bureau_evening_start_ms", -1L) } returns 1_000L

        val result = SunsetTimesStore.getEveningStartMs(context, SunsetAutomationScheduler.ZONE_BUREAU)

        assertEquals(1_000L, result)
    }

    @Test
    fun `getEveningStartMs returns the stored value for the chambre zone`() {
        setUpPrefs()
        every { prefs.getLong("chambre_evening_start_ms", -1L) } returns 2_000L

        val result = SunsetTimesStore.getEveningStartMs(context, SunsetAutomationScheduler.ZONE_CHAMBRE)

        assertEquals(2_000L, result)
    }

    @Test
    fun `getEveningStartMs returns null when no value has been saved yet`() {
        setUpPrefs()
        every { prefs.getLong("bureau_evening_start_ms", -1L) } returns -1L

        val result = SunsetTimesStore.getEveningStartMs(context, SunsetAutomationScheduler.ZONE_BUREAU)

        assertNull(result)
    }

    @Test
    fun `getEveningStartMs returns null for an unknown zone key`() {
        setUpPrefs()

        val result = SunsetTimesStore.getEveningStartMs(context, "unknown_zone")

        assertNull(result)
    }
}

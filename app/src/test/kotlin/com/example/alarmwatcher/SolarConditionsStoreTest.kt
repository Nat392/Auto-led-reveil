package com.example.alarmwatcher

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SolarConditionsStoreTest {
    private val context = mockk<Context>()
    private val prefs = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    private fun setUpPrefs() {
        every { context.applicationContext } returns context
        every { context.getSharedPreferences("solar_conditions", Context.MODE_PRIVATE) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putFloat(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.apply() } returns Unit
    }

    @Test
    fun `save persists the radiation reading and its timestamp`() {
        setUpPrefs()

        SolarConditionsStore.save(context, shortwaveRadiationWm2 = 412.5, readAtMs = 9_000L)

        verify(exactly = 1) { editor.putFloat("shortwave_radiation_wm2", 412.5f) }
        verify(exactly = 1) { editor.putLong("read_at_ms", 9_000L) }
        verify(exactly = 1) { editor.apply() }
    }

    @Test
    fun `get returns the stored reading when one was saved`() {
        setUpPrefs()
        every { prefs.getLong("read_at_ms", -1L) } returns 9_000L
        every { prefs.getFloat("shortwave_radiation_wm2", 0f) } returns 412.5f

        val result = SolarConditionsStore.get(context)

        assertEquals(SolarConditionsStore.Reading(shortwaveRadiationWm2 = 412.5, readAtMs = 9_000L), result)
    }

    @Test
    fun `get returns null when no reading has been saved yet`() {
        setUpPrefs()
        every { prefs.getLong("read_at_ms", -1L) } returns -1L

        val result = SolarConditionsStore.get(context)

        assertNull(result)
    }
}

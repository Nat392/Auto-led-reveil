package com.example.alarmwatcher

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DaylightHarvestingStateStoreTest {
    private val context = mockk<Context>()
    private val prefs = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    private fun setUpPrefs() {
        every { context.applicationContext } returns context
        every { context.getSharedPreferences("daylight_harvesting", Context.MODE_PRIVATE) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.apply() } returns Unit
    }

    @Test
    fun `getState returns inactive with the day profile by default`() {
        setUpPrefs()
        every { prefs.getBoolean("active", false) } returns false
        every { prefs.getInt("current_r", 220) } returns 220
        every { prefs.getInt("current_g", 240) } returns 240
        every { prefs.getInt("current_b", 255) } returns 255

        val state = DaylightHarvestingStateStore.getState(context)

        assertFalse(state.active)
        assertEquals(Triple(220, 240, 255), state.currentRgb)
    }

    @Test
    fun `getState returns the persisted active flag and rgb vector`() {
        setUpPrefs()
        every { prefs.getBoolean("active", false) } returns true
        every { prefs.getInt("current_r", 220) } returns 10
        every { prefs.getInt("current_g", 240) } returns 20
        every { prefs.getInt("current_b", 255) } returns 30

        val state = DaylightHarvestingStateStore.getState(context)

        assertTrue(state.active)
        assertEquals(Triple(10, 20, 30), state.currentRgb)
    }

    @Test
    fun `activate persists the active flag and the initial rgb vector`() {
        setUpPrefs()

        DaylightHarvestingStateStore.activate(context, Triple(220, 240, 255))

        verify(exactly = 1) { editor.putBoolean("active", true) }
        verify(exactly = 1) { editor.putInt("current_r", 220) }
        verify(exactly = 1) { editor.putInt("current_g", 240) }
        verify(exactly = 1) { editor.putInt("current_b", 255) }
        verify(exactly = 1) { editor.apply() }
    }

    @Test
    fun `deactivate clears the active flag without touching the rgb vector`() {
        setUpPrefs()

        DaylightHarvestingStateStore.deactivate(context)

        verify(exactly = 1) { editor.putBoolean("active", false) }
        verify(exactly = 0) { editor.putInt(any(), any()) }
        verify(exactly = 1) { editor.apply() }
    }

    @Test
    fun `saveCurrentRgb updates the rgb vector without touching the active flag`() {
        setUpPrefs()

        DaylightHarvestingStateStore.saveCurrentRgb(context, Triple(1, 2, 3))

        verify(exactly = 1) { editor.putInt("current_r", 1) }
        verify(exactly = 1) { editor.putInt("current_g", 2) }
        verify(exactly = 1) { editor.putInt("current_b", 3) }
        verify(exactly = 0) { editor.putBoolean(any(), any()) }
        verify(exactly = 1) { editor.apply() }
    }
}

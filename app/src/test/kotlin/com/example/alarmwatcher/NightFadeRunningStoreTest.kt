package com.example.alarmwatcher

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NightFadeRunningStoreTest {
    private val context = mockk<Context>()
    private val prefs = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    private fun setUpPrefs() {
        every { context.applicationContext } returns context
        every { context.getSharedPreferences("night_fade_running", Context.MODE_PRIVATE) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.apply() } returns Unit
    }

    @Test
    fun `isRunning returns false for an unknown zone key without touching prefs`() {
        setUpPrefs()

        assertFalse(NightFadeRunningStore.isRunning(context, "unknown_zone"))
    }

    @Test
    fun `markRunning then isRunning reflects the persisted flag for the bureau zone`() {
        setUpPrefs()
        every { prefs.getBoolean("bureau_running", false) } returns true

        NightFadeRunningStore.markRunning(context, SunsetAutomationScheduler.ZONE_BUREAU)

        verify(exactly = 1) { editor.putBoolean("bureau_running", true) }
        assertTrue(NightFadeRunningStore.isRunning(context, SunsetAutomationScheduler.ZONE_BUREAU))
    }

    @Test
    fun `clearRunning persists false for the chambre zone`() {
        setUpPrefs()
        every { prefs.getBoolean("chambre_running", false) } returns false

        NightFadeRunningStore.clearRunning(context, SunsetAutomationScheduler.ZONE_CHAMBRE)

        verify(exactly = 1) { editor.putBoolean("chambre_running", false) }
        assertFalse(NightFadeRunningStore.isRunning(context, SunsetAutomationScheduler.ZONE_CHAMBRE))
    }

    @Test
    fun `markRunning and clearRunning do nothing for an unknown zone key`() {
        setUpPrefs()

        NightFadeRunningStore.markRunning(context, "unknown_zone")
        NightFadeRunningStore.clearRunning(context, "unknown_zone")

        verify(exactly = 0) { editor.putBoolean(any(), any()) }
    }

    @Test
    fun `clearAll removes both zone flags`() {
        setUpPrefs()

        NightFadeRunningStore.clearAll(context)

        verify(exactly = 1) { editor.remove("bureau_running") }
        verify(exactly = 1) { editor.remove("chambre_running") }
        verify(exactly = 1) { editor.apply() }
    }
}

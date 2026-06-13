package com.example.alarmwatcher

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NightFadeScheduleStoreTest {
    private val context = mockk<Context>()
    private val prefs = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    private fun setUpPrefs() {
        every { context.applicationContext } returns context
        every { context.getSharedPreferences("night_fade_schedule", Context.MODE_PRIVATE) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.apply() } returns Unit
    }

    @Test
    fun `getAnchor returns null for an unknown zone key`() {
        setUpPrefs()

        val result = NightFadeScheduleStore.getAnchor(context, "unknown_zone")

        assertNull(result)
    }

    @Test
    fun `getAnchor returns null when nothing has been saved yet`() {
        setUpPrefs()
        every { prefs.getLong("bureau_anchor_evening_start_ms", -1L) } returns -1L
        every { prefs.getLong("bureau_anchor_original_start_ms", -1L) } returns -1L
        every { prefs.getLong("bureau_anchor_target_end_ms", -1L) } returns -1L

        val result = NightFadeScheduleStore.getAnchor(context, SunsetAutomationScheduler.ZONE_BUREAU)

        assertNull(result)
    }

    @Test
    fun `saveAnchor and getAnchor round trip for the bureau zone`() {
        setUpPrefs()
        every { prefs.getLong("bureau_anchor_evening_start_ms", -1L) } returns 1_000L
        every { prefs.getLong("bureau_anchor_original_start_ms", -1L) } returns 2_000L
        every { prefs.getLong("bureau_anchor_target_end_ms", -1L) } returns 4_000L
        every { prefs.getBoolean("bureau_anchor_fired", false) } returns true

        NightFadeScheduleStore.saveAnchor(
            context,
            SunsetAutomationScheduler.ZONE_BUREAU,
            NightFadeScheduleStore.Anchor(
                eveningStartMs = 1_000L,
                originalStartTimeMs = 2_000L,
                targetEndTimeMs = 4_000L,
                fired = true,
            ),
        )
        val result = NightFadeScheduleStore.getAnchor(context, SunsetAutomationScheduler.ZONE_BUREAU)

        verify(exactly = 1) { editor.putLong("bureau_anchor_evening_start_ms", 1_000L) }
        verify(exactly = 1) { editor.putLong("bureau_anchor_original_start_ms", 2_000L) }
        verify(exactly = 1) { editor.putLong("bureau_anchor_target_end_ms", 4_000L) }
        verify(exactly = 1) { editor.putBoolean("bureau_anchor_fired", true) }
        verify(exactly = 1) { editor.apply() }
        assertEquals(
            NightFadeScheduleStore.Anchor(
                eveningStartMs = 1_000L,
                originalStartTimeMs = 2_000L,
                targetEndTimeMs = 4_000L,
                fired = true,
            ),
            result,
        )
    }

    @Test
    fun `saveAnchor and getAnchor round trip for the chambre zone`() {
        setUpPrefs()
        every { prefs.getLong("chambre_anchor_evening_start_ms", -1L) } returns 3_000L
        every { prefs.getLong("chambre_anchor_original_start_ms", -1L) } returns 3_000L
        every { prefs.getLong("chambre_anchor_target_end_ms", -1L) } returns 5_000L
        every { prefs.getBoolean("chambre_anchor_fired", false) } returns false

        NightFadeScheduleStore.saveAnchor(
            context,
            SunsetAutomationScheduler.ZONE_CHAMBRE,
            NightFadeScheduleStore.Anchor(
                eveningStartMs = 3_000L,
                originalStartTimeMs = 3_000L,
                targetEndTimeMs = 5_000L,
                fired = false,
            ),
        )
        val result = NightFadeScheduleStore.getAnchor(context, SunsetAutomationScheduler.ZONE_CHAMBRE)

        assertEquals(
            NightFadeScheduleStore.Anchor(
                eveningStartMs = 3_000L,
                originalStartTimeMs = 3_000L,
                targetEndTimeMs = 5_000L,
                fired = false,
            ),
            result,
        )
    }

    @Test
    fun `saveAnchor and getAnchor round trip for the cuisine zone`() {
        setUpPrefs()
        every { prefs.getLong("cuisine_anchor_evening_start_ms", -1L) } returns 6_000L
        every { prefs.getLong("cuisine_anchor_original_start_ms", -1L) } returns 6_000L
        every { prefs.getLong("cuisine_anchor_target_end_ms", -1L) } returns 8_000L
        every { prefs.getBoolean("cuisine_anchor_fired", false) } returns false

        NightFadeScheduleStore.saveAnchor(
            context,
            SunsetAutomationScheduler.ZONE_CUISINE,
            NightFadeScheduleStore.Anchor(
                eveningStartMs = 6_000L,
                originalStartTimeMs = 6_000L,
                targetEndTimeMs = 8_000L,
                fired = false,
            ),
        )
        val result = NightFadeScheduleStore.getAnchor(context, SunsetAutomationScheduler.ZONE_CUISINE)

        verify(exactly = 1) { editor.putLong("cuisine_anchor_evening_start_ms", 6_000L) }
        verify(exactly = 1) { editor.putLong("cuisine_anchor_original_start_ms", 6_000L) }
        verify(exactly = 1) { editor.putLong("cuisine_anchor_target_end_ms", 8_000L) }
        verify(exactly = 1) { editor.putBoolean("cuisine_anchor_fired", false) }
        verify(exactly = 1) { editor.apply() }
        assertEquals(
            NightFadeScheduleStore.Anchor(
                eveningStartMs = 6_000L,
                originalStartTimeMs = 6_000L,
                targetEndTimeMs = 8_000L,
                fired = false,
            ),
            result,
        )
    }

    @Test
    fun `clearAnchor removes the persisted keys for the cuisine zone`() {
        setUpPrefs()

        NightFadeScheduleStore.clearAnchor(context, SunsetAutomationScheduler.ZONE_CUISINE)

        verify(exactly = 1) { editor.remove("cuisine_anchor_evening_start_ms") }
        verify(exactly = 1) { editor.remove("cuisine_anchor_original_start_ms") }
        verify(exactly = 1) { editor.remove("cuisine_anchor_target_end_ms") }
        verify(exactly = 1) { editor.remove("cuisine_anchor_fired") }
        verify(exactly = 1) { editor.apply() }
    }

    @Test
    fun `clearAnchor removes the persisted keys for the bureau zone`() {
        setUpPrefs()

        NightFadeScheduleStore.clearAnchor(context, SunsetAutomationScheduler.ZONE_BUREAU)

        verify(exactly = 1) { editor.remove("bureau_anchor_evening_start_ms") }
        verify(exactly = 1) { editor.remove("bureau_anchor_original_start_ms") }
        verify(exactly = 1) { editor.remove("bureau_anchor_target_end_ms") }
        verify(exactly = 1) { editor.remove("bureau_anchor_fired") }
        verify(exactly = 1) { editor.apply() }
    }

    @Test
    fun `clearAnchor does nothing for an unknown zone key`() {
        setUpPrefs()

        NightFadeScheduleStore.clearAnchor(context, "unknown_zone")

        verify(exactly = 0) { editor.remove(any()) }
    }
}

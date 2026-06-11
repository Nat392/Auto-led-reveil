package com.example.alarmwatcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class NightFadeTimingSupportTest {
    private val zoneId = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 6, 10)
    private val tomorrow = today.plusDays(1)

    private val leadTimeMs = (8 * 60 + 35) * 60 * 1000L

    private fun atTime(
        date: LocalDate,
        hour: Int,
        minute: Int,
    ): Long = date.atTime(hour, minute).atZone(zoneId).toInstant().toEpochMilli()

    @Test
    fun `returns null when alarm time is in the past`() {
        val now = atTime(today, 12, 0)
        val alarmTimeMs = now - 1L

        val result =
            NightFadeTimingSupport.computeScheduleOrNull(alarmTimeMs, originalStartTimeMs = now - 1L, now = now)

        assertNull(result)
    }

    @Test
    fun `returns null when alarm time is just before the morning window`() {
        val now = atTime(today, 12, 0)
        val alarmTimeMs = atTime(tomorrow, 6, 59)
        val eveningStartMs = atTime(today, 21, 0)

        val result = NightFadeTimingSupport.computeScheduleOrNull(alarmTimeMs, eveningStartMs, now)

        assertNull(result)
    }

    @Test
    fun `returns a schedule when alarm time is at the start of the morning window`() {
        val now = atTime(today, 12, 0)
        val alarmTimeMs = atTime(tomorrow, 7, 0)
        val eveningStartMs = atTime(today, 21, 0)

        val result = NightFadeTimingSupport.computeScheduleOrNull(alarmTimeMs, eveningStartMs, now)

        assertEquals(eveningStartMs, result?.originalStartTimeMs)
        assertEquals(alarmTimeMs - leadTimeMs, result?.targetEndTimeMs)
    }

    @Test
    fun `returns a schedule when alarm time is at the end of the morning window`() {
        val now = atTime(today, 12, 0)
        val alarmTimeMs = atTime(tomorrow, 9, 30)
        val eveningStartMs = atTime(today, 21, 0)

        val result = NightFadeTimingSupport.computeScheduleOrNull(alarmTimeMs, eveningStartMs, now)

        assertEquals(eveningStartMs, result?.originalStartTimeMs)
        assertEquals(alarmTimeMs - leadTimeMs, result?.targetEndTimeMs)
    }

    @Test
    fun `returns null when alarm time is just after the morning window`() {
        val now = atTime(today, 12, 0)
        val alarmTimeMs = atTime(tomorrow, 9, 31)
        val eveningStartMs = atTime(today, 21, 0)

        val result = NightFadeTimingSupport.computeScheduleOrNull(alarmTimeMs, eveningStartMs, now)

        assertNull(result)
    }

    @Test
    fun `returns null when the ramp would already need to be finished`() {
        val alarmTimeMs = atTime(tomorrow, 7, 0)
        val eveningStartMs = atTime(today, 21, 0)
        // "now" est après la fin visée de la rampe (alarme - 8h35)
        val now = (alarmTimeMs - leadTimeMs) + 1L

        val result = NightFadeTimingSupport.computeScheduleOrNull(alarmTimeMs, eveningStartMs, now)

        assertNull(result)
    }

    @Test
    fun `returns null when the evening start time is after the targeted end of the ramp`() {
        val now = atTime(today, 12, 0)
        val alarmTimeMs = atTime(tomorrow, 7, 0)
        val targetEndMs = alarmTimeMs - leadTimeMs
        // Le mode soirée ne se déclenche qu'après la fin visée du mode nuit : pas de rampe possible
        val eveningStartMs = targetEndMs + 1L

        val result = NightFadeTimingSupport.computeScheduleOrNull(alarmTimeMs, eveningStartMs, now)

        assertNull(result)
    }

    @Test
    fun `starts the ramp at the evening start time when it is in the future`() {
        val now = atTime(today, 12, 0)
        val alarmTimeMs = atTime(tomorrow, 8, 0)
        val eveningStartMs = atTime(today, 21, 0)

        val result = NightFadeTimingSupport.computeScheduleOrNull(alarmTimeMs, eveningStartMs, now)

        requireNotNull(result)
        assertEquals(eveningStartMs, result.alarmTriggerAtMs)
        assertEquals(eveningStartMs, result.originalStartTimeMs)
        assertEquals(alarmTimeMs - leadTimeMs, result.targetEndTimeMs)
    }

    @Test
    fun `starts the ramp immediately and shortens it when the alarm is set after the evening start`() {
        val eveningStartMs = atTime(today, 21, 0)
        val now = eveningStartMs + 60 * 60 * 1000L // L'alarme est posée 1h après le début du mode soirée
        val alarmTimeMs = atTime(tomorrow, 8, 0)

        val result = NightFadeTimingSupport.computeScheduleOrNull(alarmTimeMs, eveningStartMs, now)

        requireNotNull(result)
        // La rampe démarre maintenant (en retard), mais conserve son heure de début "logique" d'origine
        assertEquals(now, result.alarmTriggerAtMs)
        assertEquals(eveningStartMs, result.originalStartTimeMs)
        assertEquals(alarmTimeMs - leadTimeMs, result.targetEndTimeMs)
    }
}

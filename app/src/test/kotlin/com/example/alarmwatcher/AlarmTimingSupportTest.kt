package com.example.alarmwatcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AlarmTimingSupportTest {
    @Test
    fun `returns null when trigger time is not in the future`() {
        val now = 1_700_000_000_000L

        assertNull(AlarmTimingSupport.computePreWarnWindow(now, now))
        assertNull(AlarmTimingSupport.computePreWarnWindow(now - 1, now))
    }

    @Test
    fun `keeps the full prewarn window when the alarm is far enough in the future`() {
        val now = 1_700_000_000_000L
        val triggerTime = now + AlarmScheduler.PREWARN_MS + 15_000L

        val window = AlarmTimingSupport.computePreWarnWindow(triggerTime, now)

        requireNotNull(window)
        assertEquals(triggerTime - AlarmScheduler.PREWARN_MS, window.preWarnAt)
        assertEquals(window.preWarnAt, window.scheduleAt)
        assertEquals(AlarmScheduler.PREWARN_MS, window.durationMs)
    }

    @Test
    fun `clamps schedule time to now when the alarm is already inside the prewarn window`() {
        val now = 1_700_000_000_000L
        val triggerTime = now + 1_000L

        val window = AlarmTimingSupport.computePreWarnWindow(triggerTime, now)

        requireNotNull(window)
        assertEquals(triggerTime - AlarmScheduler.PREWARN_MS, window.preWarnAt)
        assertEquals(now, window.scheduleAt)
        assertEquals(1_000L, window.durationMs)
    }

    @Test
    fun `never returns a zero duration window`() {
        val now = 1_700_000_000_000L
        val triggerTime = now + 1L

        val window = AlarmTimingSupport.computePreWarnWindow(triggerTime, now)

        requireNotNull(window)
        assertEquals(1L, window.durationMs)
    }
}

package com.example.alarmwatcher

import kotlin.math.max

internal data class AlarmPreWarnWindow(
    val preWarnAt: Long,
    val scheduleAt: Long,
    val durationMs: Long
)

internal object AlarmTimingSupport {
    fun computePreWarnWindow(triggerTime: Long, now: Long = System.currentTimeMillis()): AlarmPreWarnWindow? {
        if (triggerTime <= now) {
            return null
        }

        val preWarnAt = triggerTime - AlarmScheduler.PREWARN_MS
        val scheduleAt = max(preWarnAt, now)
        val durationMs = max(triggerTime - scheduleAt, 1L)

        return AlarmPreWarnWindow(
            preWarnAt = preWarnAt,
            scheduleAt = scheduleAt,
            durationMs = durationMs
        )
    }
}
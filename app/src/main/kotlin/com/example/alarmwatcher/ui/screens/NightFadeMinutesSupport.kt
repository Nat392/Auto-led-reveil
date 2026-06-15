package com.example.alarmwatcher.ui.screens

import com.example.alarmwatcher.settings.AppSettings

private const val MINUTES_PER_HOUR = 60

internal fun leadTimeText(totalMinutes: Int): String {
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = (totalMinutes % MINUTES_PER_HOUR).toString().padStart(2, '0')
    return "${hours}h$minutes avant l'alarme"
}

internal fun minutesToHour(totalMinutes: Int): Int = totalMinutes / MINUTES_PER_HOUR

internal fun minutesToMinute(totalMinutes: Int): Int = totalMinutes % MINUTES_PER_HOUR

private fun withHour(
    totalMinutes: Int,
    hour: Int,
): Int = hour * MINUTES_PER_HOUR + totalMinutes % MINUTES_PER_HOUR

private fun withMinute(
    totalMinutes: Int,
    minute: Int,
): Int = (totalMinutes / MINUTES_PER_HOUR) * MINUTES_PER_HOUR + minute

internal fun AppSettings.withMorningStartHour(hour: Int): AppSettings {
    return copy(nightFadeMorningStartMinutes = withHour(nightFadeMorningStartMinutes, hour))
}

internal fun AppSettings.withMorningStartMinute(minute: Int) =
    copy(nightFadeMorningStartMinutes = withMinute(nightFadeMorningStartMinutes, minute))

internal fun AppSettings.withMorningEndHour(hour: Int): AppSettings {
    return copy(nightFadeMorningEndMinutes = withHour(nightFadeMorningEndMinutes, hour))
}

internal fun AppSettings.withMorningEndMinute(minute: Int) =
    copy(nightFadeMorningEndMinutes = withMinute(nightFadeMorningEndMinutes, minute))

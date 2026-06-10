package com.example.alarmwatcher

import java.time.Instant
import java.time.ZoneId
import kotlin.math.max

internal object NightFadeTimingSupport {

    private const val LEAD_TIME_MS = (8 * 60 + 35) * 60 * 1000L
    private const val MINUTES_PER_HOUR = 60
    private const val MORNING_WINDOW_START_MINUTES = 7 * MINUTES_PER_HOUR
    private const val MORNING_WINDOW_END_MINUTES = 9 * MINUTES_PER_HOUR + 30

    data class Schedule(
        val alarmTriggerAtMs: Long,
        val originalStartTimeMs: Long,
        val targetEndTimeMs: Long,
    )

    /**
     * @param zoneEveningStartMs L'heure absolue de déclenchement du mode soirée pour cette zone
     * (coucher du soleil - offset de zone, fourni par [SunsetTimesStore]). `null` si cette
     * donnée n'a pas encore été calculée (pas de mode nuit programmé tant qu'elle est inconnue).
     */
    fun computeScheduleOrNull(
        alarmTimeMs: Long,
        zoneEveningStartMs: Long?,
        now: Long = System.currentTimeMillis(),
    ): Schedule? {
        if (zoneEveningStartMs == null) return null

        val alarmZdt = Instant.ofEpochMilli(alarmTimeMs).atZone(ZoneId.systemDefault())
        val minutesOfDay = alarmZdt.hour * MINUTES_PER_HOUR + alarmZdt.minute
        val targetEndMs = alarmTimeMs - LEAD_TIME_MS

        // L'alarme doit être future, dans la fenêtre 07:00-09:30, avec une rampe encore réalisable
        // et un mode soirée déclenché avant la fin visée du mode nuit.
        val isSchedulable =
            alarmTimeMs > now &&
                minutesOfDay in MORNING_WINDOW_START_MINUTES..MORNING_WINDOW_END_MINUTES &&
                targetEndMs > now &&
                zoneEveningStartMs < targetEndMs

        return if (isSchedulable) {
            Schedule(
                // L'heure à laquelle le service doit réellement se réveiller
                // (maintenant, ou au déclenchement du mode soirée)
                alarmTriggerAtMs = max(zoneEveningStartMs, now),
                originalStartTimeMs = zoneEveningStartMs,
                targetEndTimeMs = targetEndMs,
            )
        } else {
            null
        }
    }
}

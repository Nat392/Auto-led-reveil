package com.example.alarmwatcher

import java.time.Instant
import java.time.ZoneId
import kotlin.math.max

internal object NightFadeTimingSupport {

    private const val LEAD_TIME_MS = (8 * 60 + 35) * 60 * 1000L

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
        if (alarmTimeMs <= now) return null
        if (zoneEveningStartMs == null) return null

        val alarmZdt = Instant.ofEpochMilli(alarmTimeMs).atZone(ZoneId.systemDefault())
        val minutes = alarmZdt.hour * 60 + alarmZdt.minute

        // L'alarme doit être entre 07:00 (420) et 09:30 (570)
        if (minutes !in 420..570) return null

        val targetEndMs = alarmTimeMs - LEAD_TIME_MS
        if (targetEndMs <= now) return null // Déjà trop tard pour finir la rampe

        // Le mode soirée doit avoir lieu avant la fin visée du mode nuit, sinon la rampe n'a pas de sens
        if (zoneEveningStartMs >= targetEndMs) return null

        // L'heure à laquelle le service doit réellement se réveiller (maintenant, ou au déclenchement du mode soirée)
        val actualStartMs = max(zoneEveningStartMs, now)

        return Schedule(
            alarmTriggerAtMs = actualStartMs,
            originalStartTimeMs = zoneEveningStartMs,
            targetEndTimeMs = targetEndMs,
        )
    }
}

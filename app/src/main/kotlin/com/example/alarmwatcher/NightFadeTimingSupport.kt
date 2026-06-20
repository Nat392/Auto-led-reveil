package com.example.alarmwatcher

import com.example.alarmwatcher.settings.AppSettingsCache
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max

internal object NightFadeTimingSupport {
    private const val MINUTES_PER_HOUR = 60
    private const val MILLIS_PER_MINUTE = 60_000L

    private val leadTimeMs: Long
        get() = AppSettingsCache.current.nightFadeLeadTimeMinutes * MILLIS_PER_MINUTE
    private val morningWindowStartMinutes: Int
        get() = AppSettingsCache.current.nightFadeMorningStartMinutes
    private val morningWindowEndMinutes: Int
        get() = AppSettingsCache.current.nightFadeMorningEndMinutes

    data class Schedule(
        val alarmTriggerAtMs: Long,
        val originalStartTimeMs: Long,
        val targetEndTimeMs: Long,
    )

    /**
     * @param originalStartTimeMs L'heure absolue "logique" de début du fondu nocturne pour cette
     * zone, déjà résolue par [AlarmMonitorRunner] (ancrée sur le début du mode soirée, ou sur
     * l'instant présent si le mode soirée est déjà passé sans fondu déjà programmé pour cette
     * zone).
     */
    fun computeScheduleOrNull(
        alarmTimeMs: Long,
        originalStartTimeMs: Long,
        now: Long = System.currentTimeMillis(),
    ): Schedule? {
        val alarmZdt = Instant.ofEpochMilli(alarmTimeMs).atZone(ZoneId.systemDefault())
        val minutesOfDay = alarmZdt.hour * MINUTES_PER_HOUR + alarmZdt.minute
        val targetEndMs = alarmTimeMs - leadTimeMs

        // L'alarme doit être future, dans la fenêtre matinale configurée, avec une rampe encore
        // réalisable et un début de fondu avant la fin visée du mode nuit.
        val isSchedulable =
            alarmTimeMs > now &&
                minutesOfDay in morningWindowStartMinutes..morningWindowEndMinutes &&
                targetEndMs > now &&
                originalStartTimeMs < targetEndMs

        return if (isSchedulable) {
            Schedule(
                // L'heure à laquelle le service doit réellement se réveiller
                // (maintenant, ou au déclenchement du fondu)
                alarmTriggerAtMs = max(originalStartTimeMs, now),
                originalStartTimeMs = originalStartTimeMs,
                targetEndTimeMs = targetEndMs,
            )
        } else {
            null
        }
    }
}

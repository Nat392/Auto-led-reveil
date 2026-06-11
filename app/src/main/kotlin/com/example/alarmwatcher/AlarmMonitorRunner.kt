package com.example.alarmwatcher

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import java.time.Instant
import java.time.ZoneId

internal class AlarmMonitorRunner(
    private val alarmScheduler: AlarmSchedulerApi,
    private val crashReporter: CrashReporterApi,
) {
    fun scanNextAlarmAndSchedule(context: Context) {
        try {
            if (SunriseService.isRunning) {
                Log.i(TAG, "Sunrise already running; skipping alarm rescan")
                return
            }

            val alarmManager = context.getSystemService(AlarmManager::class.java)
            if (alarmManager == null) {
                Log.w(TAG, "AlarmManager unavailable")
                return
            }

            val next = alarmManager.nextAlarmClock
            if (next == null) {
                alarmScheduler.cancelPreWarn(context)
                cancelAllNightFades(context)
                return
            }

            val creatorPackage = next.showIntent?.creatorPackage
            if (creatorPackage != null && creatorPackage !in ALLOWED_CLOCK_PACKAGES) {
                Log.i(TAG, "Skipping alarm from unauthorized package: $creatorPackage")
                alarmScheduler.cancelPreWarn(context)
                cancelAllNightFades(context)
                return
            }

            val trigger = next.triggerTime
            val now = System.currentTimeMillis()

            scheduleNightFades(context, trigger, now)
            scheduleSunrisePreWarn(context, trigger, now)
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission manquante pour lire les alarmes : ${e.message}")
            crashReporter.reportNonFatal(
                context = context,
                throwable = e,
                source = "AlarmMonitor.scanNextAlarmAndSchedule",
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected alarm scan failure", e)
            crashReporter.reportNonFatal(
                context = context,
                throwable = e,
                source = "AlarmMonitor.scanNextAlarmAndSchedule",
            )
        }
    }

    private fun scheduleNightFades(
        context: Context,
        trigger: Long,
        now: Long,
    ) {
        for (zoneKey in NIGHT_FADE_ZONE_KEYS) {
            scheduleNightFadeForZone(context, zoneKey, trigger, now)
        }
    }

    private fun scheduleNightFadeForZone(
        context: Context,
        zoneKey: String,
        trigger: Long,
        now: Long,
    ) {
        val zone = SunsetSceneService.resolveZone(zoneKey)
        val zoneEveningStartMs = zoneEveningStartMsOrNull(context, zone, zoneKey)
        if (zoneEveningStartMs == null) {
            alarmScheduler.cancelNightFade(context, zoneKey)
            NightFadeScheduleStore.clearAnchor(context, zoneKey)
            return
        }

        val anchor = NightFadeScheduleStore.getAnchor(context, zoneKey)
        val anchorForThisEvening = anchor?.takeIf { it.eveningStartMs == zoneEveningStartMs }

        // Si une alarme était déjà connue avant le mode soirée, la rampe démarre à l'heure du
        // mode soirée (durée complète). Sinon (mode soirée déjà passé sans rampe encore
        // programmée), elle démarre maintenant avec un fondu plus rapide jusqu'à la cible. Cette
        // heure de départ "logique" reste figée pour tout le mode soirée, même une fois le
        // fondu déclenché.
        val originalStartTimeMs =
            anchorForThisEvening?.originalStartTimeMs
                ?: if (zoneEveningStartMs >= now) zoneEveningStartMs else now

        val nightFadeSchedule = NightFadeTimingSupport.computeScheduleOrNull(trigger, originalStartTimeMs, now)
        // Si le fondu a déjà été déclenché mais que l'heure de l'alarme a changé entretemps,
        // la cible (targetEndTimeMs) est recalculée et le fondu en cours est réajusté
        // dynamiquement en relançant immédiatement le service avec la nouvelle cible.
        val alreadyFiredWithSameTarget =
            anchorForThisEvening?.fired == true &&
                nightFadeSchedule != null &&
                anchorForThisEvening.targetEndTimeMs == nightFadeSchedule.targetEndTimeMs

        if (nightFadeSchedule != null && !alreadyFiredWithSameTarget) {
            alarmScheduler.scheduleNightFade(
                context,
                zoneKey,
                nightFadeSchedule.alarmTriggerAtMs,
                nightFadeSchedule.originalStartTimeMs,
                nightFadeSchedule.targetEndTimeMs,
            )
            NightFadeScheduleStore.saveAnchor(
                context,
                zoneKey,
                NightFadeScheduleStore.Anchor(
                    eveningStartMs = zoneEveningStartMs,
                    originalStartTimeMs = nightFadeSchedule.originalStartTimeMs,
                    targetEndTimeMs = nightFadeSchedule.targetEndTimeMs,
                    fired = nightFadeSchedule.alarmTriggerAtMs <= now,
                ),
            )
        } else if (nightFadeSchedule == null) {
            alarmScheduler.cancelNightFade(context, zoneKey)
            NightFadeScheduleStore.clearAnchor(context, zoneKey)
        }
    }

    /**
     * Retourne l'heure de début du mode soirée pour [zoneKey], ou `null` si la zone n'est pas
     * configurée ou si cette heure n'a pas encore été calculée.
     */
    private fun zoneEveningStartMsOrNull(
        context: Context,
        zone: SunriseBulbZone?,
        zoneKey: String,
    ): Long? {
        if (zone == null || !zone.isConfigured) return null
        return SunsetTimesStore.getEveningStartMs(context, zoneKey)
    }

    private fun scheduleSunrisePreWarn(
        context: Context,
        trigger: Long,
        now: Long,
    ) {
        val hour = Instant.ofEpochMilli(trigger).atZone(ZoneId.systemDefault()).hour
        if (hour !in MORNING_WINDOW_START_HOUR..MORNING_WINDOW_END_HOUR) {
            Log.i(TAG, "Skipping non-morning alarm for sunrise at $trigger (hour=$hour)")
            alarmScheduler.cancelPreWarn(context)
            return
        }

        val window = AlarmTimingSupport.computePreWarnWindow(trigger, now)
        if (window != null) {
            alarmScheduler.schedulePreWarn(context, window.scheduleAt, trigger, window.durationMs)
        } else {
            alarmScheduler.cancelPreWarn(context)
        }
    }

    private fun cancelAllNightFades(context: Context) {
        for (zoneKey in NIGHT_FADE_ZONE_KEYS) {
            alarmScheduler.cancelNightFade(context, zoneKey)
        }
    }

    private companion object {
        const val TAG = "AlarmMonitor"
        const val MORNING_WINDOW_START_HOUR = 2
        const val MORNING_WINDOW_END_HOUR = 13

        val NIGHT_FADE_ZONE_KEYS =
            listOf(SunsetAutomationScheduler.ZONE_BUREAU, SunsetAutomationScheduler.ZONE_CHAMBRE)

        val ALLOWED_CLOCK_PACKAGES =
            setOf(
                "com.google.android.deskclock",
                "com.sec.android.app.clockpackage",
                "com.android.deskclock",
                "com.oneplus.deskclock",
                "com.coloros.alarmclock",
                "com.miui.deskclock",
                "com.android.alarmclock",
                "com.lge.clock",
                "com.asus.deskclock",
                "com.sonyericsson.organizer",
            )
    }
}

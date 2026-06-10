package com.example.alarmwatcher

import android.app.AlarmManager
import android.content.Context
import android.util.Log

internal class AlarmMonitorRunner(
    private val alarmScheduler: AlarmSchedulerApi,
    private val crashReporter: CrashReporterApi,
) {
    fun scanNextAlarmAndSchedule(context: Context) {
        try {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            if (alarmManager == null) {
                Log.w(TAG, "AlarmManager unavailable")
                return
            }

            val next = alarmManager.nextAlarmClock
            if (next != null) {
                val creatorPackage = next.showIntent?.creatorPackage
                if (creatorPackage != null && creatorPackage !in ALLOWED_CLOCK_PACKAGES) {
                    Log.i(TAG, "Skipping alarm from unauthorized package: $creatorPackage")
                    alarmScheduler.cancelPreWarn(context)
                    cancelAllNightFades(context)
                    return
                }

                val trigger = next.triggerTime
                val now = System.currentTimeMillis()

                // 1. Gérer la rampe nocturne (Night Fade) de manière indépendante, par zone
                for (zoneKey in NIGHT_FADE_ZONE_KEYS) {
                    val zone = SunsetSceneService.resolveZone(zoneKey)
                    if (zone == null || !zone.isConfigured) {
                        alarmScheduler.cancelNightFade(context, zoneKey)
                        continue
                    }

                    val zoneEveningStartMs = SunsetTimesStore.getEveningStartMs(context, zoneKey)
                    val nightFadeSchedule = NightFadeTimingSupport.computeScheduleOrNull(trigger, zoneEveningStartMs)
                    if (nightFadeSchedule != null) {
                        alarmScheduler.scheduleNightFade(
                            context,
                            zoneKey,
                            nightFadeSchedule.alarmTriggerAtMs,
                            nightFadeSchedule.originalStartTimeMs,
                            nightFadeSchedule.targetEndTimeMs,
                        )
                    } else {
                        alarmScheduler.cancelNightFade(context, zoneKey)
                    }
                }

                // 2. Gérer le lever de soleil (Sunrise Prewarn)
                val hour = java.time.Instant.ofEpochMilli(trigger).atZone(java.time.ZoneId.systemDefault()).hour
                if (hour in 2..13) {
                    val window = AlarmTimingSupport.computePreWarnWindow(trigger, now)
                    if (window != null) {
                        alarmScheduler.schedulePreWarn(context, window.scheduleAt, trigger, window.durationMs)
                    } else {
                        alarmScheduler.cancelPreWarn(context)
                    }
                } else {
                    Log.i(TAG, "Skipping non-morning alarm for sunrise at $trigger (hour=$hour)")
                    alarmScheduler.cancelPreWarn(context)
                }
                
            } else {
                alarmScheduler.cancelPreWarn(context)
                cancelAllNightFades(context)
            }
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

    private fun cancelAllNightFades(context: Context) {
        for (zoneKey in NIGHT_FADE_ZONE_KEYS) {
            alarmScheduler.cancelNightFade(context, zoneKey)
        }
    }

    private companion object {
        const val TAG = "AlarmMonitor"

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
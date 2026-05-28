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
                    return
                }

                val trigger = next.triggerTime
                val now = System.currentTimeMillis()

                if (trigger <= now) {
                    Log.i(TAG, "Skipping expired nextAlarmClock at $trigger (now=$now)")
                    alarmScheduler.cancelPreWarn(context)
                    return
                }

                // Check if the alarm is in the morning/noon
                val hour =
                    java.time.Instant.ofEpochMilli(trigger)
                        .atZone(java.time.ZoneId.systemDefault())
                        .hour
                if (hour < 2 || hour >= 14) {
                    Log.i(TAG, "Skipping non-morning alarm at $trigger (hour=$hour)")
                    alarmScheduler.cancelPreWarn(context)
                    return
                }

                val window =
                    AlarmTimingSupport.computePreWarnWindow(trigger, now)
                        ?: run {
                            alarmScheduler.cancelPreWarn(context)
                            return
                        }

                alarmScheduler.schedulePreWarn(context, window.scheduleAt, trigger, window.durationMs)
            } else {
                alarmScheduler.cancelPreWarn(context)
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

    private companion object {
        const val TAG = "AlarmMonitor"

        val ALLOWED_CLOCK_PACKAGES =
            setOf(
                // Horloge Google (Pixel, etc.)
                "com.google.android.deskclock",
                // Horloge Samsung
                "com.sec.android.app.clockpackage",
                // Horloge AOSP (utilisée par Xiaomi, Motorola, Nothing, etc.)
                "com.android.deskclock",
                // Horloge OnePlus
                "com.oneplus.deskclock",
                // Horloge Oppo / Realme (ColorOS)
                "com.coloros.alarmclock",
                // Horloge Xiaomi (sur certaines versions de MIUI)
                "com.miui.deskclock",
                // Anciennes versions Android
                "com.android.alarmclock",
                // Horloge LG
                "com.lge.clock",
                // Horloge Asus
                "com.asus.deskclock",
                // Horloge Sony
                "com.sonyericsson.organizer",
            )
    }
}

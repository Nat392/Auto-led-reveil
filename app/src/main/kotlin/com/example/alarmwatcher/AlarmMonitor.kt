package com.example.alarmwatcher

import android.content.Context

object AlarmMonitor {
    private val defaultRunner = AlarmMonitorRunner(AlarmScheduler, DiscordCrashReporter)

    fun scanNextAlarmAndSchedule(context: Context) {
        defaultRunner.scanNextAlarmAndSchedule(context)
    }
}
package com.example.alarmwatcher

import android.content.Context
import kotlinx.coroutines.Job

interface AlarmSchedulerApi {
    fun schedulePreWarn(context: Context, whenMs: Long, originalAlarmMs: Long, durationMs: Long = AlarmScheduler.PREWARN_MS)
    fun cancelPreWarn(context: Context)
    fun stopSunriseService(context: Context)
}

interface CrashReporterApi {
    fun reportNonFatal(context: Context, throwable: Throwable, source: String? = null): Job
    fun reportFatalBlocking(context: Context, throwable: Throwable, threadName: String? = null)
    fun reportDebugBlocking(context: Context, source: String, details: String)
}

interface BulbSession : AutoCloseable {
    suspend fun applyScene(red: Int, green: Int, blue: Int, white: Int): Boolean
}

interface BulbControllerApi {
    suspend fun openSession(context: Context, macAddress: String): BulbSession?
    suspend fun applyScene(
        context: Context,
        macAddress: String,
        red: Int,
        green: Int,
        blue: Int,
        white: Int,
        brightnessPercent: Int
    ): Boolean

    suspend fun powerOff(context: Context, macAddress: String): Boolean

    suspend fun diagnosticApplyScene(
        context: Context,
        macAddress: String,
        red: Int,
        green: Int,
        blue: Int,
        white: Int
    ): String
}
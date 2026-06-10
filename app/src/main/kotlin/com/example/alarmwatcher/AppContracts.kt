package com.example.alarmwatcher

import android.content.Context
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Job

const val DEFAULT_PREWARN_MS = 30 * 60 * 1000L

interface AlarmSchedulerApi {
    fun schedulePreWarn(
        context: Context,
        whenMs: Long,
        originalAlarmMs: Long,
        durationMs: Long = DEFAULT_PREWARN_MS,
    )

    fun cancelPreWarn(context: Context)

    fun stopSunriseService(context: Context)

    // Planification par zone, basée sur des timestamps absolus
    fun scheduleNightFade(
        context: Context,
        zoneKey: String,
        triggerAtMs: Long,
        startTimeMs: Long,
        endTimeMs: Long,
    )

    fun cancelNightFade(
        context: Context,
        zoneKey: String,
    )
}

interface CrashReporterApi {
    fun reportNonFatal(
        context: Context,
        throwable: Throwable,
        source: String? = null,
    ): Job

    fun reportFatalBlocking(
        context: Context,
        throwable: Throwable,
        threadName: String? = null,
    )

    fun reportDebugBlocking(
        context: Context,
        source: String,
        details: String,
    )
}

interface BulbSession : AutoCloseable {
    @WorkerThread
    suspend fun applyScene(
        red: Int,
        green: Int,
        blue: Int,
        white: Int,
    ): Boolean
}

interface BulbControllerApi {
    @WorkerThread
    suspend fun openSession(
        context: Context,
        macAddress: String,
    ): BulbSession?

    @WorkerThread
    suspend fun applyScene(
        context: Context,
        macAddress: String,
        red: Int,
        green: Int,
        blue: Int,
        white: Int,
        brightnessPercent: Int,
    ): Boolean

    @WorkerThread
    suspend fun powerOff(
        context: Context,
        macAddress: String,
    ): Boolean

    @WorkerThread
    suspend fun diagnosticApplyScene(
        context: Context,
        macAddress: String,
        red: Int,
        green: Int,
        blue: Int,
        white: Int,
    ): String
}

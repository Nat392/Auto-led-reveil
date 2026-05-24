package com.example.alarmwatcher

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SunsetCatchUpWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val zoneKey = inputData.getString(KEY_ZONE_KEY)
            ?: return Result.failure()

        val applied = SunsetSceneService.applySunsetScene(applicationContext, zoneKey)
        return if (applied) Result.success() else Result.failure()
    }

    companion object {
        const val KEY_ZONE_KEY = "zone_key"

        fun uniqueWorkName(zoneKey: String): String = "sunset_catch_up_$zoneKey"
    }
}
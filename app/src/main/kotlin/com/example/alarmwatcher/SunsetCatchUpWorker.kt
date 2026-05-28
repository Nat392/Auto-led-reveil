package com.example.alarmwatcher

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SunsetCatchUpWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val zoneKey = inputData.getString(KEY_ZONE_KEY) ?: return Result.failure()

        val applied = SunsetSceneService.applySunsetScene(applicationContext, zoneKey)

        return if (applied) {
            Result.success()
        } else {
            val errorMessage =
                "Echec de l'application de la scene de coucher de soleil pour la zone $zoneKey"
            crashReporter.reportNonFatal(
                context = applicationContext,
                throwable = IllegalStateException(errorMessage),
                source = "SunsetCatchUpWorker",
            )
            Result.failure()
        }
    }

    companion object {
        const val KEY_ZONE_KEY = "zone_key"
        internal var crashReporter: CrashReporterApi = DiscordCrashReporter

        fun uniqueWorkName(zoneKey: String): String = "sunset_catch_up_$zoneKey"
    }
}

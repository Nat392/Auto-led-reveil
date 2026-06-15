package com.example.alarmwatcher

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.alarmwatcher.settings.AppSettingsCache
import java.util.concurrent.TimeUnit

/**
 * Programme le worker périodique du Daylight Harvesting. L'intervalle est piloté par
 * [com.example.alarmwatcher.settings.AppSettings.daylightIntervalMinutes] ; WorkManager applique
 * un minimum incompressible de 15 minutes pour le travail périodique, quelle que soit la valeur
 * demandée.
 */
internal object DaylightHarvestingScheduler {
    fun schedule(context: Context) {
        val intervalMinutes = AppSettingsCache.current.daylightIntervalMinutes.toLong()
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val request =
            PeriodicWorkRequestBuilder<DaylightHarvestingWorker>(intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            DaylightHarvestingWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

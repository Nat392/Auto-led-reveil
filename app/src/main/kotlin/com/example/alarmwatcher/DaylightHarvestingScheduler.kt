package com.example.alarmwatcher

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Programme le worker périodique du Daylight Harvesting. L'intervalle (15 min) est supérieur à
 * la durée d'un fondu ([DaylightFadeRunner], 14 min), ce qui laisse à chaque cycle le temps de se
 * terminer avant le suivant dans le cas normal.
 */
internal object DaylightHarvestingScheduler {
    private const val INTERVAL_MINUTES = 15L

    fun schedule(context: Context) {
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val request =
            PeriodicWorkRequestBuilder<DaylightHarvestingWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            DaylightHarvestingWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

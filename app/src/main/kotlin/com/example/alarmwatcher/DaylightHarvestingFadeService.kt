package com.example.alarmwatcher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Exécute le fondu du Daylight Harvesting (jusqu'à 14 minutes, voir [DaylightFadeRunner]) en
 * foreground service plutôt que dans [DaylightHarvestingWorker.doWork] : un Worker WorkManager
 * standard est tué par le système au bout d'environ 10 minutes d'exécution en arrière-plan, ce qui
 * interrompait le fondu et décalait le cycle périodique suivant de dizaines de minutes.
 */
class DaylightHarvestingFadeService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fadeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val zoneKeys = intent?.getStringArrayListExtra(EXTRA_ZONE_KEYS).orEmpty()
        val shortwaveRadiation = intent?.getDoubleExtra(EXTRA_SHORTWAVE_RADIATION, Double.NaN) ?: Double.NaN

        if (zoneKeys.isNotEmpty() && !shortwaveRadiation.isNaN() && startForegroundSafely()) {
            fadeJob?.cancel()
            fadeJob =
                serviceScope.launch {
                    try {
                        runFade(applicationContext, zoneKeys, shortwaveRadiation)
                    } finally {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                    }
                }
        } else {
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        fadeJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundSafely(): Boolean =
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Unable to start foreground service for daylight harvesting fade", e)
            DiscordCrashReporter.reportNonFatal(
                context = applicationContext,
                throwable = e,
                source = "DaylightHarvestingFadeService.startForeground",
            )
            false
        }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel =
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Daylight Harvesting",
                NotificationManager.IMPORTANCE_LOW,
            )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm Watcher")
            .setContentText("Ajustement de la lumière naturelle…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val TAG = "DaylightHarvestingFadeService"
        private const val NOTIFICATION_ID = 403
        private const val NOTIFICATION_CHANNEL_ID = "daylight_harvesting_fade_service"
        internal const val EXTRA_ZONE_KEYS = "extra_zone_keys"
        internal const val EXTRA_SHORTWAVE_RADIATION = "extra_shortwave_radiation"

        internal fun start(
            context: Context,
            zoneKeys: List<String>,
            shortwaveRadiation: Double,
        ) {
            val serviceIntent =
                Intent(context, DaylightHarvestingFadeService::class.java).apply {
                    putStringArrayListExtra(EXTRA_ZONE_KEYS, ArrayList(zoneKeys))
                    putExtra(EXTRA_SHORTWAVE_RADIATION, shortwaveRadiation)
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                @Suppress("DEPRECATION")
                context.startService(serviceIntent)
            }
        }

        internal suspend fun runFade(
            context: Context,
            zoneKeys: List<String>,
            shortwaveRadiation: Double,
        ) {
            coroutineScope {
                zoneKeys.forEach { zoneKey ->
                    launch { applyHarvesting(context, zoneKey, shortwaveRadiation) }
                }
            }
        }

        private suspend fun applyHarvesting(
            context: Context,
            zoneKey: String,
            shortwaveRadiation: Double,
        ) {
            val zone = SunsetSceneService.resolveZone(zoneKey) ?: return
            val state = DaylightHarvestingStateStore.getState(context, zoneKey)
            val saturationRadiationWm2 = DaylightHarvestingWorker.saturationThresholdWm2(zoneKey)

            val targetRgb =
                DaylightHarvestingEstimator.calculateTargetRgb(
                    shortwaveRadiation,
                    Triple(zone.sunriseR, zone.sunriseG, zone.sunriseB),
                    saturationRadiationWm2,
                )

            if (targetRgb == state.currentRgb) {
                return
            }

            DaylightFadeRunner(ZenggeBulbController, DiscordCrashReporter).fade(
                context = context,
                fadeZone = FadeZone(key = zoneKey, bulb = zone),
                transition = ColorTransition(from = state.currentRgb, to = targetRgb),
            )
        }
    }
}

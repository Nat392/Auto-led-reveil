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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NightFadeService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fadeJob: Job? = null
    private val crashReporter: CrashReporterApi = DiscordCrashReporter

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val zoneKey = intent?.getStringExtra(AlarmScheduler.EXTRA_ZONE_KEY)
        val startTimeMs = intent?.getLongExtra(AlarmScheduler.EXTRA_START_TIME_MS, 0L) ?: 0L
        val endTimeMs = intent?.getLongExtra(AlarmScheduler.EXTRA_END_TIME_MS, 0L) ?: 0L

        if (zoneKey == null || startTimeMs <= 0L || endTimeMs <= startTimeMs) {
            Log.w(TAG, "Paramètres invalides, fondu nocturne annulé")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val zone = SunsetSceneService.resolveZone(zoneKey)
        if (zone == null || !zone.isConfigured) {
            Log.w(TAG, "Zone inconnue ou non configurée ($zoneKey), fondu nocturne annulé")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (!BlePermissionSupport.hasBluetoothConnectPermission(applicationContext)) {
            Log.w(TAG, "Arrêt du fondu nocturne : permission BLUETOOTH_CONNECT manquante")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: SecurityException) {
            Log.e(TAG, "Impossible de démarrer le service en avant-plan pour le fondu nocturne", e)
            crashReporter.reportNonFatal(
                context = applicationContext,
                throwable = e,
                source = "NightFadeService.startForeground",
            )
            stopSelf(startId)
            return START_NOT_STICKY
        }

        fadeJob?.cancel()
        fadeJob = serviceScope.launch {
            try {
                val runner = NightFadeRunner(ZenggeBulbController, crashReporter)
                runner.run(
                    context = applicationContext,
                    macAddress = zone.macAddress,
                    startR = zone.sunsetR,
                    startG = zone.sunsetG,
                    startB = zone.sunsetB,
                    startTimeMs = startTimeMs,
                    endTimeMs = endTimeMs,
                )
            } catch (e: CancellationException) {
                Log.i(TAG, "Fondu nocturne annulé")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur durant le fondu nocturne", e)
                crashReporter.reportNonFatal(
                    context = applicationContext,
                    throwable = e,
                    source = "NightFadeService.fadeJob",
                )
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        fadeJob?.cancel()
        fadeJob = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Fondu nocturne",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm Watcher")
            .setContentText("Fondu nocturne en cours…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val TAG = "NightFadeService"
        private const val NOTIFICATION_ID = 403
        private const val NOTIFICATION_CHANNEL_ID = "night_fade_service"
    }
}
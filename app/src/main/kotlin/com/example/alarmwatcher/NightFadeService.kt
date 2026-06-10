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
        val request = validateRequest(intent)
        if (request != null) {
            startFadeJob(request)
        } else {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun validateRequest(intent: Intent?): NightFadeRequest? {
        val request = parseRequest(intent)
        val hasPermission = BlePermissionSupport.hasBluetoothConnectPermission(applicationContext)

        return when {
            request == null -> {
                Log.w(TAG, "Paramètres invalides ou zone non configurée, fondu nocturne annulé")
                null
            }
            !hasPermission -> {
                Log.w(TAG, "Arrêt du fondu nocturne : permission BLUETOOTH_CONNECT manquante")
                null
            }
            !startForegroundSafely() -> null
            else -> request
        }
    }

    private fun parseRequest(intent: Intent?): NightFadeRequest? {
        val zoneKey = intent?.getStringExtra(AlarmScheduler.EXTRA_ZONE_KEY)
        val startTimeMs = intent?.getLongExtra(AlarmScheduler.EXTRA_START_TIME_MS, 0L) ?: 0L
        val endTimeMs = intent?.getLongExtra(AlarmScheduler.EXTRA_END_TIME_MS, 0L) ?: 0L
        val zone = zoneKey?.let { SunsetSceneService.resolveZone(it) }

        val isValid =
            zoneKey != null && startTimeMs > 0L && endTimeMs > startTimeMs && zone != null && zone.isConfigured

        return if (isValid) {
            NightFadeRequest(zone, startTimeMs, endTimeMs)
        } else {
            null
        }
    }

    private fun startForegroundSafely(): Boolean =
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Impossible de démarrer le service en avant-plan pour le fondu nocturne", e)
            crashReporter.reportNonFatal(
                context = applicationContext,
                throwable = e,
                source = "NightFadeService.startForeground",
            )
            false
        }

    private fun startFadeJob(request: NightFadeRequest) {
        fadeJob?.cancel()
        fadeJob = serviceScope.launch {
            try {
                val runner = NightFadeRunner(ZenggeBulbController, crashReporter)
                runner.run(
                    context = applicationContext,
                    zone = request.zone,
                    startTimeMs = request.startTimeMs,
                    endTimeMs = request.endTimeMs,
                )
            } catch (e: CancellationException) {
                Log.i(TAG, "Fondu nocturne annulé")
                throw e
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
    }

    private data class NightFadeRequest(
        val zone: SunriseBulbZone,
        val startTimeMs: Long,
        val endTimeMs: Long,
    )

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

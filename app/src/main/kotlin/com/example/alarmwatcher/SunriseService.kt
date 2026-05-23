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
import kotlinx.coroutines.launch

class SunriseService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var rampJob: Job? = null
    private val rampRunner = SunriseRampRunner(ZenggeBulbController, DiscordCrashReporter)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START_SUNRISE) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification("Démarrage du lever de soleil"))

        rampJob?.cancel()
        val macAddress = intent.getStringExtra(EXTRA_BULB_MAC).orEmpty().trim()
        val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, AlarmScheduler.PREWARN_MS).coerceAtLeast(1L)

        rampJob = serviceScope.launch {
            try {
                rampRunner.run(applicationContext, macAddress, durationMs)
            } catch (e: Exception) {
                Log.e(TAG, "Erreur durant la rampe de luminosité", e)
                DiscordCrashReporter.reportNonFatal(
                    context = applicationContext,
                    throwable = e,
                    source = "SunriseService.rampJob"
                )
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        rampJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Lever de soleil",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("Alarm Watcher")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private data class Scene(
        val red: Int,
        val green: Int,
        val blue: Int
    )

    companion object {
        private const val TAG = "SunriseService"
        private const val NOTIFICATION_ID = 401
        private const val NOTIFICATION_CHANNEL_ID = "sunrise_service"

        const val ACTION_START_SUNRISE = "com.example.alarmwatcher.ACTION_START_SUNRISE"
        const val EXTRA_BULB_MAC = "extra_bulb_mac"
        const val EXTRA_ORIGINAL_ALARM_MS = "extra_original_alarm_ms"
        const val EXTRA_DURATION_MS = "extra_duration_ms"
    }
}
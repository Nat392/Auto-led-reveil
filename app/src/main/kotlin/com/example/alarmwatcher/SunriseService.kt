package com.example.alarmwatcher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

class SunriseService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var rampJob: Job? = null

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
                // Steps: 0..30 inclusive (31 iterations). 'steps' is the max t value.
                val steps = 30
                // We want duration split into 30 intervals (ramp granularity), so divide by steps.
                val stepDelayMs = max(1L, durationMs / steps)
                for (t in 0..steps) {
                    val palette = reportSceneAtStep(t)
                    val brightnessPercentForController = 100
                    ZenggeBulbController.applyScene(
                        context = applicationContext,
                        macAddress = macAddress,
                        red = palette.red,
                        green = palette.green,
                        blue = palette.blue,
                        white = 0,
                        brightnessPercent = brightnessPercentForController
                    )
                    if (t < steps) {
                        delay(stepDelayMs)
                    }
                }
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
        const val ACTION_START_SUNRISE = "com.example.alarmwatcher.ACTION_START_SUNRISE"
        const val EXTRA_BULB_MAC = "extra_bulb_mac"
        const val EXTRA_ORIGINAL_ALARM_MS = "extra_original_alarm_ms"
        const val EXTRA_DURATION_MS = "extra_duration_ms"

        private const val NOTIFICATION_ID = 401
        private const val NOTIFICATION_CHANNEL_ID = "sunrise_service"

        private val REPORT_RGB_TABLE = listOf(
            Scene(0, 0, 0),
            Scene(1, 0, 0),
            Scene(2, 0, 0),
            Scene(3, 0, 0),
            Scene(4, 0, 0),
            Scene(6, 1, 0),
            Scene(8, 2, 0),
            Scene(10, 3, 0),
            Scene(13, 5, 0),
            Scene(17, 7, 0),
            Scene(22, 10, 1),
            Scene(28, 14, 2),
            Scene(35, 18, 4),
            Scene(43, 23, 6),
            Scene(52, 29, 9),
            Scene(61, 36, 13),
            Scene(72, 44, 18),
            Scene(84, 53, 24),
            Scene(96, 62, 31),
            Scene(109, 73, 40),
            Scene(123, 84, 49),
            Scene(138, 97, 60),
            Scene(154, 111, 73),
            Scene(170, 126, 88),
            Scene(188, 142, 104),
            Scene(206, 159, 122),
            Scene(220, 177, 142),
            Scene(220, 196, 165),
            Scene(220, 216, 191),
            Scene(220, 237, 220),
            Scene(220, 240, 255)
        )
    }

    private fun reportSceneAtStep(t: Int): Scene {
        return REPORT_RGB_TABLE.getOrElse(t) { REPORT_RGB_TABLE.last() }
    }
}
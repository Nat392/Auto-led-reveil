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
        val originalAlarmMs = intent.getLongExtra(EXTRA_ORIGINAL_ALARM_MS, -1L)
        val red = intent.getIntExtra(EXTRA_TARGET_R, 255)
        val green = intent.getIntExtra(EXTRA_TARGET_G, 230)
        val blue = intent.getIntExtra(EXTRA_TARGET_B, 210)
        val white = intent.getIntExtra(EXTRA_TARGET_WHITE, 255)
        val initialBrightness = intent.getIntExtra(EXTRA_INITIAL_BRIGHTNESS, 1).coerceIn(1, 100)
        val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, AlarmScheduler.PREWARN_MS).coerceAtLeast(1L)

        rampJob = serviceScope.launch {
            try {
                val steps = 30
                val stepDelayMs = max(1L, durationMs / steps)
                for (step in 0 until steps) {
                    val computedBrightness = ((step * 99) / (steps - 1)) + 1
                    val brightness = max(initialBrightness, computedBrightness.coerceIn(1, 100))
                    val ts = System.currentTimeMillis()
                    Log.d("SunriseService", "[$ts] Step ${step + 1}/$steps start brightness=$brightness targetR=$red targetG=$green targetB=$blue targetW=$white mac=$macAddress")
                    val applied = ZenggeBulbController.applyScene(
                        context = applicationContext,
                        macAddress = macAddress,
                        red = red,
                        green = green,
                        blue = blue,
                        white = white,
                        brightnessPercent = brightness
                    )
                    val ts2 = System.currentTimeMillis()
                    Log.d("SunriseService", "[$ts2] Step ${step + 1}/$steps end applied=$applied brightness=$brightness durationMs=${ts2 - ts}")
                    DiscordCrashReporter.reportDebugBlocking(
                        context = applicationContext,
                        source = "SunriseService.step",
                        details = buildString {
                            appendLine("SunriseService.step")
                            appendLine("step=${step + 1}/$steps")
                            appendLine("brightness=$brightness")
                            appendLine("applied=$applied")
                            appendLine("mac=$macAddress")
                            appendLine("originalAlarmMs=$originalAlarmMs")
                        }
                    )
                    delay(stepDelayMs)
                }
            } catch (e: Exception) {
                DiscordCrashReporter.reportDebugBlocking(
                    context = applicationContext,
                    source = "SunriseService.ramp",
                    details = buildString {
                        appendLine("SunriseService ramp failed")
                        appendLine("error=${e::class.java.name}")
                        appendLine("message=${e.message}")
                    }
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

    companion object {
        const val ACTION_START_SUNRISE = "com.example.alarmwatcher.ACTION_START_SUNRISE"
        const val EXTRA_BULB_MAC = "extra_bulb_mac"
        const val EXTRA_ORIGINAL_ALARM_MS = "extra_original_alarm_ms"
        const val EXTRA_TARGET_R = "extra_target_r"
        const val EXTRA_TARGET_G = "extra_target_g"
        const val EXTRA_TARGET_B = "extra_target_b"
        const val EXTRA_TARGET_WHITE = "extra_target_white"
        const val EXTRA_INITIAL_BRIGHTNESS = "extra_initial_brightness"
        const val EXTRA_DURATION_MS = "extra_duration_ms"

        private const val NOTIFICATION_ID = 401
        private const val NOTIFICATION_CHANNEL_ID = "sunrise_service"
    }
}
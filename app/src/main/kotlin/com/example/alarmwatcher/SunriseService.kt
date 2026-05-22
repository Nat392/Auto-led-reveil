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
            DiscordCrashReporter.reportDebugBlocking(
                context = applicationContext,
                source = "SunriseService.onStartCommand.ignored",
                details = buildString {
                    appendLine("Ignored start command")
                    appendLine("action=${intent?.action}")
                    appendLine("startId=$startId")
                }
            )
            stopSelf(startId)
            return START_NOT_STICKY
        }

        DiscordCrashReporter.reportDebugBlocking(
            context = applicationContext,
            source = "SunriseService.onStartCommand.entry",
            details = buildString {
                appendLine("SunriseService start command received")
                appendLine("flags=$flags")
                appendLine("startId=$startId")
                appendLine("action=${intent.action}")
            }
        )
        startForeground(NOTIFICATION_ID, buildNotification("Démarrage du lever de soleil"))

        rampJob?.cancel()
        val macAddress = intent.getStringExtra(EXTRA_BULB_MAC).orEmpty().trim()
        val originalAlarmMs = intent.getLongExtra(EXTRA_ORIGINAL_ALARM_MS, -1L)
        // Targets (treated as per-channel maxima for the RGB Dimming Hack)
        val targetRMax = intent.getIntExtra(EXTRA_TARGET_R, 220)
        val targetGMax = intent.getIntExtra(EXTRA_TARGET_G, 240)
        val targetBMax = intent.getIntExtra(EXTRA_TARGET_B, 255)
        // White channel not used for this RGB-only sequence; keep user-provided value but
        // we will force sending W=0 during the ramp.
        val targetWhite = intent.getIntExtra(EXTRA_TARGET_WHITE, 0)
        val initialBrightness = intent.getIntExtra(EXTRA_INITIAL_BRIGHTNESS, 1).coerceIn(1, 100)
        val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, AlarmScheduler.PREWARN_MS).coerceAtLeast(1L)

        rampJob = serviceScope.launch {
            try {
                // Steps: 0..30 inclusive (31 iterations). 'steps' is the max t value.
                val steps = 30
                // We want duration split into 30 intervals (ramp granularity), so divide by steps.
                val stepDelayMs = max(1L, durationMs / steps)
                DiscordCrashReporter.reportDebugBlocking(
                    context = applicationContext,
                    source = "SunriseService.ramp.start",
                    details = buildString {
                        appendLine("Starting sunrise ramp")
                        appendLine("mac=$macAddress")
                        appendLine("originalAlarmMs=$originalAlarmMs")
                        appendLine("durationMs=$durationMs")
                        appendLine("steps=$steps (inclusive 0..$steps)")
                        appendLine("initialBrightness=$initialBrightness")
                    }
                )
                // RGB Dimming Hack: keep master brightness at 100% and modulate R/G/B per step.
                val gamma = 2.4
                for (t in 0..steps) {
                    // Normalized progression with channel-specific delays (Rayleigh-like timing)
                    val tR = t.toDouble() / steps.toDouble() // starts at t=0
                    val tG = max(0.0, (t - 3).toDouble() / (steps - 3).toDouble()) // green starts at t=3
                    val tB = max(0.0, (t - 8).toDouble() / (steps - 8).toDouble()) // blue starts at t=8

                    // Apply gamma exponent and scale to per-channel maxima
                    val rRaw = if (tR <= 0.0) 0.0 else Math.pow(tR, gamma) * targetRMax.toDouble()
                    val gRaw = if (tG <= 0.0) 0.0 else Math.pow(tG, gamma) * targetGMax.toDouble()
                    val bRaw = if (tB <= 0.0) 0.0 else Math.pow(tB, gamma) * targetBMax.toDouble()

                    var r = Math.round(rRaw).toInt().coerceIn(0, 255)
                    var g = Math.round(gRaw).toInt().coerceIn(0, 255)
                    var b = Math.round(bRaw).toInt().coerceIn(0, 255)

                    // Anti-stagnation: force small red values on first iterations
                    if (t == 1) r = 1
                    if (t == 2) r = 2
                    if (t == 3) r = 3

                    val brightnessPercentForController = 100 // master brightness locked to 100%
                    val ts = System.currentTimeMillis()
                    Log.d("SunriseService", "[$ts] Step ${t + 1}/${steps + 1} start t=$t r=$r g=$g b=$b mac=$macAddress")

                    val applied = ZenggeBulbController.applyScene(
                        context = applicationContext,
                        macAddress = macAddress,
                        red = r,
                        green = g,
                        blue = b,
                        white = 0,
                        brightnessPercent = brightnessPercentForController
                    )

                    val ts2 = System.currentTimeMillis()
                    Log.d("SunriseService", "[$ts2] Step ${t + 1}/${steps + 1} end applied=$applied r=$r g=$g b=$b durationMs=${ts2 - ts}")
                    DiscordCrashReporter.reportDebugBlocking(
                        context = applicationContext,
                        source = "SunriseService.step",
                        details = buildString {
                            appendLine("SunriseService.step")
                            appendLine("step=${t + 1}/${steps + 1}")
                            appendLine("r=$r g=$g b=$b controllerBrightness=100")
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
                    source = "SunriseService.ramp.exception",
                    details = buildString {
                        appendLine("SunriseService ramp exception")
                        appendLine("error=${e::class.java.name}")
                        appendLine("message=${e.message}")
                    }
                )
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
                DiscordCrashReporter.reportDebugBlocking(
                    context = applicationContext,
                    source = "SunriseService.ramp.finally",
                    details = buildString {
                        appendLine("Stopping SunriseService")
                        appendLine("mac=$macAddress")
                        appendLine("originalAlarmMs=$originalAlarmMs")
                    }
                )
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
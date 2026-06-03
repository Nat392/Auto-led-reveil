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
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class SunriseService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var rampJob: Job? = null
    private val rampRunner = SunriseRampRunner(ZenggeBulbController)
    private var currentTargetAlarmMs: Long = -1L

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
        if (intent?.action != ACTION_START_SUNRISE) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val originalAlarmMs = intent.getLongExtra(EXTRA_ORIGINAL_ALARM_MS, -1L)
        val shouldDeduplicate = originalAlarmMs > 0L && currentTargetAlarmMs == originalAlarmMs
        if (rampJob?.isActive == true && shouldDeduplicate) {
            Log.i(TAG, "Rampe déjà en cours pour cette alarme, on ignore le redémarrage.")
            return START_NOT_STICKY
        }
        currentTargetAlarmMs = if (originalAlarmMs > 0L) originalAlarmMs else -1L
        val zones =
            extractZones(intent)
                .ifEmpty { SunriseZoneConfig.configuredZones() }

        rampJob?.cancel()
        val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, AlarmScheduler.PREWARN_MS).coerceAtLeast(1L)
        val totalSteps = SunriseRampSupport.computeStepCount(durationMs)

        if (!BlePermissionSupport.hasBluetoothConnectPermission(applicationContext)) {
            Log.w(TAG, "Stopping sunrise service: BLUETOOTH_CONNECT permission is not granted")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIFICATION_ID, buildRampNotification(0, totalSteps, originalAlarmMs))
        } catch (e: SecurityException) {
            Log.e(TAG, "Unable to start foreground sunrise service", e)
            DiscordCrashReporter.reportNonFatal(
                context = applicationContext,
                throwable = e,
                source = "SunriseService.startForeground",
            )
            stopSelf(startId)
            return START_NOT_STICKY
        }

        rampJob =
            serviceScope.launch {
                try {
                    coroutineScope {
                        zones.map { zone ->
                            async {
                                rampRunner.run(
                                    applicationContext,
                                    zone.macAddress,
                                    zone.sunriseR,
                                    zone.sunriseG,
                                    zone.sunriseB,
                                    durationMs,
                                ) { currentStep, total ->
                                    updateRampNotification(currentStep + 1, total, originalAlarmMs)
                                }
                            }
                        }.forEach { it.await() }
                    }
                } catch (e: CancellationException) {
                    Log.i(TAG, "Rampe sunrise annulée")
                } catch (e: InterruptedException) {
                    Log.i(TAG, "Rampe sunrise interrompue")
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur durant la rampe de luminosité", e)
                    DiscordCrashReporter.reportNonFatal(
                        context = applicationContext,
                        throwable = e,
                        source = "SunriseService.rampJob",
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
        rampJob = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel =
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Lever de soleil",
                NotificationManager.IMPORTANCE_LOW,
            )
        manager.createNotificationChannel(channel)
    }

    private fun buildRampNotification(
        completedSteps: Int,
        totalSteps: Int,
        originalAlarmMs: Long,
    ) = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("Alarm Watcher")
        .setContentText("Lever de soleil — $completedSteps / $totalSteps")
        .setSubText(formatAlarmTime(originalAlarmMs))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun updateRampNotification(
        completedSteps: Int,
        totalSteps: Int,
        originalAlarmMs: Long,
    ) {
        getSystemService(NotificationManager::class.java)?.notify(
            NOTIFICATION_ID,
            buildRampNotification(completedSteps, totalSteps, originalAlarmMs),
        )
    }

    private fun formatAlarmTime(originalAlarmMs: Long): String? {
        if (originalAlarmMs <= 0L) {
            return null
        }
        val formatter = DateFormat.getTimeInstance(DateFormat.SHORT)
        return "Alarme cible : ${formatter.format(Date(originalAlarmMs))}"
    }

    companion object {
        private const val TAG = "SunriseService"
        private const val NOTIFICATION_ID = 401
        private const val NOTIFICATION_CHANNEL_ID = "sunrise_service"

        const val ACTION_START_SUNRISE = "com.example.alarmwatcher.ACTION_START_SUNRISE"
        const val EXTRA_BULB_MACS = "extra_bulb_macs"
        const val EXTRA_TARGET_RS = "extra_target_rs"
        const val EXTRA_TARGET_GS = "extra_target_gs"
        const val EXTRA_TARGET_BS = "extra_target_bs"
        const val EXTRA_ORIGINAL_ALARM_MS = "extra_original_alarm_ms"
        const val EXTRA_DURATION_MS = "extra_duration_ms"
    }

    private fun extractZones(intent: Intent): List<SunriseBulbZone> {
        val macAddresses = intent.getStringArrayListExtra(EXTRA_BULB_MACS).orEmpty()
        val targetRs = intent.getIntArrayExtra(EXTRA_TARGET_RS)
        val targetGs = intent.getIntArrayExtra(EXTRA_TARGET_GS)
        val targetBs = intent.getIntArrayExtra(EXTRA_TARGET_BS)

        if (macAddresses.isEmpty() || targetRs == null || targetGs == null || targetBs == null) {
            return emptyList()
        }

        val zoneCount = minOf(macAddresses.size, targetRs.size, targetGs.size, targetBs.size)
        return (0 until zoneCount)
            .mapNotNull { index ->
                val macAddress = macAddresses[index].trim()
                if (macAddress.isBlank()) {
                    return@mapNotNull null
                }
                SunriseBulbZone(
                    label = "Zone ${index + 1}",
                    macAddress = macAddress,
                    sunriseR = targetRs[index],
                    sunriseG = targetGs[index],
                    sunriseB = targetBs[index],
                    sunsetR = targetRs[index],
                    sunsetG = targetGs[index],
                    sunsetB = targetBs[index],
                )
            }
    }
}

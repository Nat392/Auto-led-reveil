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

class SunsetSceneService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sceneJob: Job? = null

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
        if (intent?.action != SunsetAutomationScheduler.ACTION_APPLY_SCENE) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val zoneKey = intent.getStringExtra(SunsetAutomationScheduler.EXTRA_TARGET_ZONE)
        val zone = resolveZone(zoneKey)

        if (zoneKey == null || zone == null || !zone.isConfigured) {
            Log.w(TAG, "Missing configured zone for sunset scene: $zoneKey")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (!BlePermissionSupport.hasBluetoothConnectPermission(applicationContext)) {
            Log.w(TAG, "Stopping sunset scene: BLUETOOTH_CONNECT permission is not granted")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification(zone.label))
        } catch (e: SecurityException) {
            Log.e(TAG, "Unable to start foreground service for sunset scene", e)
            DiscordCrashReporter.reportNonFatal(
                context = applicationContext,
                throwable = e,
                source = "SunsetSceneService.startForeground",
            )
            stopSelf(startId)
            return START_NOT_STICKY
        }

        sceneJob?.cancel()
        sceneJob =
            serviceScope.launch {
                try {
                    if (!applySunsetScene(applicationContext, zoneKey)) {
                        Log.w(TAG, "Failed to apply sunset scene for ${zone.label}")
                    }
                } finally {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        sceneJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel =
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Mode soirée",
                NotificationManager.IMPORTANCE_LOW,
            )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(zoneLabel: String) =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm Watcher")
            .setContentText("Mode soirée — $zoneLabel")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val TAG = "SunsetSceneService"
        private const val NOTIFICATION_ID = 402
        private const val NOTIFICATION_CHANNEL_ID = "sunset_scene_service"

        internal fun resolveZone(zoneKey: String?): SunriseBulbZone? {
            return when (zoneKey) {
                SunsetAutomationScheduler.ZONE_BUREAU -> SunriseZoneConfig.bureau
                SunsetAutomationScheduler.ZONE_CHAMBRE -> SunriseZoneConfig.chambre
                SunsetAutomationScheduler.ZONE_CUISINE -> SunriseZoneConfig.cuisine
                else -> null
            }
        }

        internal suspend fun applySunsetScene(
            context: Context,
            zoneKey: String,
        ): Boolean {
            // La scène du soir "ferme la fenêtre" du Daylight Harvesting de cette zone pour la
            // nuit, quel que soit le résultat (succès, zone non configurée, permission
            // manquante, échec BLE).
            fun deactivateHarvestingIfApplicable() {
                if (zoneKey == SunsetAutomationScheduler.ZONE_CHAMBRE || zoneKey == SunsetAutomationScheduler.ZONE_CUISINE) {
                    DaylightHarvestingStateStore.deactivate(context, zoneKey)
                }
            }

            val zone = resolveZone(zoneKey)
            if (zone == null || !zone.isConfigured) {
                Log.w(TAG, "Missing configured zone for sunset scene: $zoneKey")
                deactivateHarvestingIfApplicable()
                return false
            }

            if (!BlePermissionSupport.hasBluetoothConnectPermission(context)) {
                Log.w(TAG, "Stopping sunset scene: BLUETOOTH_CONNECT permission is not granted")
                deactivateHarvestingIfApplicable()
                return false
            }

            return try {
                val applied =
                    ZenggeBulbController.applyScene(
                        context = context,
                        macAddress = zone.macAddress,
                        red = zone.sunsetR,
                        green = zone.sunsetG,
                        blue = zone.sunsetB,
                        white = zone.whiteChannel,
                        brightnessPercent = zone.brightnessPercent,
                    )

                if (!applied) {
                    Log.w(TAG, "Failed to apply sunset scene for ${zone.label}")
                    val errorMessage = "Failed to apply sunset scene for ${zone.label} (macAddress=${zone.macAddress})"
                    DiscordCrashReporter.reportNonFatal(
                        context = context,
                        throwable = IllegalStateException(errorMessage),
                        source = "SunsetSceneService.applySunsetScene",
                    )
                }

                applied
            } catch (e: CancellationException) {
                Log.i(TAG, "Sunset scene cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error while applying sunset scene", e)
                DiscordCrashReporter.reportNonFatal(
                    context = context,
                    throwable = e,
                    source = "SunsetSceneService.applySunsetScene",
                )
                false
            } finally {
                // La scène du soir "ferme la fenêtre" du Daylight Harvesting de cette zone pour
                // la nuit, quel que soit le succès BLE.
                deactivateHarvestingIfApplicable()
            }
        }
    }
}

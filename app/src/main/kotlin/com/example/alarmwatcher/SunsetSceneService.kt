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
import com.example.alarmwatcher.settings.AppSettingsCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
            DiscordCrashReporter.reportNonFatal(
                context = applicationContext,
                throwable = IllegalStateException("Missing configured zone for sunset scene (zoneKey=$zoneKey)"),
                source = "SunsetSceneService.onStartCommand.zoneMissing",
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (!BlePermissionSupport.hasBluetoothConnectPermission(applicationContext)) {
            Log.w(TAG, "Stopping sunset scene: BLUETOOTH_CONNECT permission is not granted")
            val permissionError =
                SecurityException(
                    "BLUETOOTH_CONNECT permission missing, sunset scene not applied " +
                        "(zoneKey=$zoneKey, macAddress=${zone.macAddress})",
                )
            DiscordCrashReporter.reportNonFatal(
                context = applicationContext,
                throwable = permissionError,
                source = "SunsetSceneService.onStartCommand.permission",
            )
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
        private const val MILLIS_PER_SECOND = 1_000.0
        private const val MAX_SOLAR_READING_AGE_MS = 30 * 60 * 1_000L
        private const val MIN_EVENING_BRIGHTNESS_PERCENT = 30
        private val sceneApplyMaxAttempts: Int
            get() = AppSettingsCache.current.sunsetSceneRetryAttempts
        private val sceneApplyRetryDelayMs: Long
            get() = (AppSettingsCache.current.sunsetSceneRetryDelaySeconds * MILLIS_PER_SECOND).toLong()

        /**
         * Sur les longues journées, la bascule soirée peut survenir alors qu'il fait encore
         * assez clair (radiation solaire résiduelle au-dessus du seuil de saturation de la
         * zone) : démarrer directement à pleine luminosité serait une marche artificielle.
         * On atténue la scène du soir en proportion de la lumière naturelle restante, avec un
         * plancher pour rester visible — ce calcul est ponctuel (au moment de la bascule), pas
         * réévalué ensuite, donc volontairement conservateur plutôt que précis à la minute.
         */
        private fun resolveEveningBrightnessPercent(
            context: Context,
            zoneKey: String,
            configuredBrightnessPercent: Int,
        ): Int {
            val isHarvestingZone =
                zoneKey == SunsetAutomationScheduler.ZONE_CHAMBRE ||
                    zoneKey == SunsetAutomationScheduler.ZONE_CUISINE
            if (!isHarvestingZone) return configuredBrightnessPercent

            val freshReading =
                SolarConditionsStore.get(context)?.takeIf {
                    System.currentTimeMillis() - it.readAtMs <= MAX_SOLAR_READING_AGE_MS
                }

            return freshReading?.let { reading ->
                val saturationRadiationWm2 = DaylightHarvestingWorker.saturationThresholdWm2(zoneKey)
                val artificialFraction =
                    DaylightHarvestingEstimator.artificialFraction(
                        reading.shortwaveRadiationWm2,
                        saturationRadiationWm2,
                    )
                (configuredBrightnessPercent * artificialFraction)
                    .roundToInt()
                    .coerceIn(MIN_EVENING_BRIGHTNESS_PERCENT, configuredBrightnessPercent)
            } ?: configuredBrightnessPercent
        }

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
                val isHarvestingZone =
                    zoneKey == SunsetAutomationScheduler.ZONE_CHAMBRE ||
                        zoneKey == SunsetAutomationScheduler.ZONE_CUISINE
                if (isHarvestingZone) {
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
                var applied = false
                val maxAttempts = sceneApplyMaxAttempts
                val brightnessPercent = resolveEveningBrightnessPercent(context, zoneKey, zone.brightnessPercent)
                for (attempt in 1..maxAttempts) {
                    applied =
                        ZenggeBulbController.applyScene(
                            context = context,
                            macAddress = zone.macAddress,
                            red = zone.sunsetR,
                            green = zone.sunsetG,
                            blue = zone.sunsetB,
                            white = zone.whiteChannel,
                            brightnessPercent = brightnessPercent,
                        )
                    if (applied || attempt == maxAttempts) break
                    Log.w(TAG, "Retrying sunset scene for ${zone.label} (attempt $attempt failed)")
                    delay(sceneApplyRetryDelayMs)
                }

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

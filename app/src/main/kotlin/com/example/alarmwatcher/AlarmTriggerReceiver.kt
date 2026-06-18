package com.example.alarmwatcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmTriggerReceiver : BroadcastReceiver() {
    internal companion object {
        var intentFactory: (Context, Class<*>) -> Intent = { context, targetClass ->
            Intent(context, targetClass)
        }

        private const val TAG = "AlarmTriggerReceiver"
        const val ACTION_PREWARN = "com.example.alarmwatcher.ACTION_PREWARN"
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            ACTION_PREWARN -> handleSunrise(context, intent)
            AlarmScheduler.ACTION_START_NIGHT_FADE -> handleNightFade(context, intent)
        }
    }

    private fun handleNightFade(
        context: Context,
        intent: Intent,
    ) {
        val zoneKey = intent.getStringExtra(AlarmScheduler.EXTRA_ZONE_KEY)
        val startTimeMs = intent.getLongExtra(AlarmScheduler.EXTRA_START_TIME_MS, -1L)
        val endTimeMs = intent.getLongExtra(AlarmScheduler.EXTRA_END_TIME_MS, -1L)

        if (zoneKey != null && startTimeMs > 0 && endTimeMs > 0) {
            Log.i(TAG, "Lancement du fondu nocturne ($zoneKey, absolu: $startTimeMs -> $endTimeMs)")
            val serviceIntent =
                Intent(context, NightFadeService::class.java).apply {
                    putExtra(AlarmScheduler.EXTRA_ZONE_KEY, zoneKey)
                    putExtra(AlarmScheduler.EXTRA_START_TIME_MS, startTimeMs)
                    putExtra(AlarmScheduler.EXTRA_END_TIME_MS, endTimeMs)
                }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else {
            val errorMessage =
                "Fondu nocturne ignoré: paramètres invalides (zoneKey=$zoneKey, " +
                    "startTimeMs=$startTimeMs, endTimeMs=$endTimeMs)"
            Log.w(TAG, errorMessage)
            DiscordCrashReporter.reportNonFatal(
                context = context,
                throwable = IllegalStateException(errorMessage),
                source = "AlarmTriggerReceiver.handleNightFade",
            )
        }
    }

    private fun handleSunrise(
        context: Context,
        intent: Intent,
    ) {
        if (!BlePermissionSupport.hasBluetoothConnectPermission(context)) {
            Log.w(TAG, "Cannot start sunrise service: BLUETOOTH_CONNECT permission missing")
            NotificationHelper.showFallbackNotification(context)
            return
        }

        val originalAlarmMs = intent.getLongExtra("original_alarm_ms", -1L)
        val durationMs = intent.getLongExtra(SunriseService.EXTRA_DURATION_MS, AlarmScheduler.PREWARN_MS)
        val sunriseZones = SunriseZoneConfig.configuredZones()

        try {
            if (sunriseZones.isNotEmpty()) {
                val macAddresses = ArrayList<String>(sunriseZones.size)
                val targetRValues = IntArray(sunriseZones.size)
                val targetGValues = IntArray(sunriseZones.size)
                val targetBValues = IntArray(sunriseZones.size)

                sunriseZones.forEachIndexed { index, zone ->
                    macAddresses += zone.macAddress
                    targetRValues[index] = zone.sunriseR
                    targetGValues[index] = zone.sunriseG
                    targetBValues[index] = zone.sunriseB
                }

                val serviceIntent =
                    intentFactory(context, SunriseService::class.java).apply {
                        setAction(SunriseService.ACTION_START_SUNRISE)
                        putStringArrayListExtra(SunriseService.EXTRA_BULB_MACS, macAddresses)
                        putExtra(SunriseService.EXTRA_TARGET_RS, targetRValues)
                        putExtra(SunriseService.EXTRA_TARGET_GS, targetGValues)
                        putExtra(SunriseService.EXTRA_TARGET_BS, targetBValues)
                        putExtra(SunriseService.EXTRA_ORIGINAL_ALARM_MS, originalAlarmMs)
                        putExtra(SunriseService.EXTRA_DURATION_MS, durationMs)
                    }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct BLE control failed: ${e.message}")
            DiscordCrashReporter.reportNonFatal(
                context = context,
                throwable = e,
                source =
                    "AlarmTriggerReceiver.handleSunrise(zoneCount=${sunriseZones.size}, " +
                        "macAddresses=${sunriseZones.joinToString(",") { it.macAddress }})",
            )
        }

        NotificationHelper.showFallbackNotification(context)
    }
}

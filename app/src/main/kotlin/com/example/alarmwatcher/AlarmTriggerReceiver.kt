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
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        handleAlarm(context, intent)
    }

    private fun handleAlarm(
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
            Log.w("AlarmTriggerReceiver", "Direct BLE control failed: ${e.message}")
        }

        // Show fallback notification only when automation is not configured or cannot be started
        NotificationHelper.showFallbackNotification(context)
    }
}

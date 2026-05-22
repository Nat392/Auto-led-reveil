package com.example.alarmwatcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("AlarmTriggerReceiver", "onReceive entry action=${intent.action}")
        handleAlarm(context, intent)
    }

    private fun handleAlarm(context: Context, intent: Intent) {
        val action = intent.action
        val originalAlarmMs = intent.getLongExtra("original_alarm_ms", -1L)
        Log.i("AlarmTriggerReceiver", "Received pre-warn action=$action")
        DiscordCrashReporter.reportDebugBlocking(
            context = context,
            source = "AlarmTriggerReceiver.onReceive",
            details = buildString {
                appendLine("AlarmTriggerReceiver.onReceive()")
                appendLine("action=$action")
                appendLine("originalAlarmMs=$originalAlarmMs")
                appendLine("now=${System.currentTimeMillis()}")
            }
        )

        val bulbMac = BuildConfig.ZENGGE_BULB_MAC.trim()
        try {
            if (bulbMac.isNotBlank()) {
                val serviceIntent = Intent(context, SunriseService::class.java).apply {
                    setAction(SunriseService.ACTION_START_SUNRISE)
                    putExtra(SunriseService.EXTRA_BULB_MAC, bulbMac)
                    putExtra(SunriseService.EXTRA_ORIGINAL_ALARM_MS, originalAlarmMs)
                    putExtra(SunriseService.EXTRA_TARGET_R, intent.getIntExtra("target_r", 255))
                    putExtra(SunriseService.EXTRA_TARGET_G, intent.getIntExtra("target_g", 230))
                    putExtra(SunriseService.EXTRA_TARGET_B, intent.getIntExtra("target_b", 210))
                    putExtra(SunriseService.EXTRA_TARGET_WHITE, intent.getIntExtra("target_white", 255))
                    putExtra(SunriseService.EXTRA_INITIAL_BRIGHTNESS, intent.getIntExtra("brightness_percent", 1))
                    putExtra(SunriseService.EXTRA_DURATION_MS, AlarmScheduler.PREWARN_MS)
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

        // Always show fallback notification in case automation is not enabled or fails
        NotificationHelper.showFallbackNotification(context)
    }
}

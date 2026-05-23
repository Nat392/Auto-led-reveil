package com.example.alarmwatcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.alarmwatcher.BuildConfig

class AlarmTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        handleAlarm(context, intent)
    }

    private fun handleAlarm(context: Context, intent: Intent) {
        val originalAlarmMs = intent.getLongExtra("original_alarm_ms", -1L)
        val durationMs = intent.getLongExtra(SunriseService.EXTRA_DURATION_MS, AlarmScheduler.PREWARN_MS)

        val bulbMac = BuildConfig.ZENGGE_BULB_MAC.trim()
        try {
            if (bulbMac.isNotBlank()) {
                val targetR: Int = intent.getIntExtra("target_r", 220)
                val targetG: Int = intent.getIntExtra("target_g", 240)
                val targetB: Int = intent.getIntExtra("target_b", 255)
                val targetWhite: Int = intent.getIntExtra("target_white", 0)
                val initialBrightness: Int = intent.getIntExtra("brightness_percent", 1)
                val serviceIntent = Intent(context, SunriseService::class.java).apply {
                    setAction(SunriseService.ACTION_START_SUNRISE)
                    putExtra(SunriseService.EXTRA_BULB_MAC, bulbMac)
                    putExtra(SunriseService.EXTRA_ORIGINAL_ALARM_MS, originalAlarmMs)
                    putExtra(SunriseService.EXTRA_TARGET_R, targetR)
                    putExtra(SunriseService.EXTRA_TARGET_G, targetG)
                    putExtra(SunriseService.EXTRA_TARGET_B, targetB)
                    putExtra(SunriseService.EXTRA_TARGET_WHITE, targetWhite)
                    putExtra(SunriseService.EXTRA_INITIAL_BRIGHTNESS, initialBrightness)
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

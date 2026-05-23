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
                val serviceIntent = Intent(context, SunriseService::class.java).apply {
                    setAction(SunriseService.ACTION_START_SUNRISE)
                    putExtra(SunriseService.EXTRA_BULB_MAC, bulbMac)
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

package com.example.alarmwatcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("AlarmTriggerReceiver", "onReceive entry action=${intent.action}")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAlarm(context, intent)
            } finally {
                pendingResult.finish()
            }
        }
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
                val applied = ZenggeBulbController.applyScene(
                    context = context,
                    macAddress = bulbMac,
                    red = intent.getIntExtra("target_r", 255),
                    green = intent.getIntExtra("target_g", 230),
                    blue = intent.getIntExtra("target_b", 210),
                    white = intent.getIntExtra("target_white", 255),
                    brightnessPercent = intent.getIntExtra("brightness_percent", 100)
                )
                DiscordCrashReporter.reportDebugBlocking(
                    context = context,
                    source = "AlarmTriggerReceiver.directBle",
                    details = buildString {
                        appendLine("AlarmTriggerReceiver.directBle()")
                        appendLine("mac=$bulbMac")
                        appendLine("applied=$applied")
                    }
                )
                if (applied) {
                    return
                }
            }
        } catch (e: Exception) {
            Log.w("AlarmTriggerReceiver", "Direct BLE control failed: ${e.message}")
            DiscordCrashReporter.reportDebugBlocking(
                context = context,
                source = "AlarmTriggerReceiver.directBle",
                details = buildString {
                    appendLine("AlarmTriggerReceiver.directBle() failed")
                    appendLine("mac=$bulbMac")
                    appendLine("error=${e::class.java.name}")
                    appendLine("message=${e.message}")
                }
            )
        }

        // Always show fallback notification in case automation is not enabled or fails
        NotificationHelper.showFallbackNotification(context)
    }
}

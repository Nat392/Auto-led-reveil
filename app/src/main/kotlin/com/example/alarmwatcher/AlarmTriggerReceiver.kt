package com.example.alarmwatcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i("AlarmTriggerReceiver", "Received pre-warn action=$action")
        // Attempt to open com.zengge.blev2 in foreground
        val originalAlarmMs = intent.getLongExtra("original_alarm_ms", -1L)
        try {
            val launch = context.packageManager.getLaunchIntentForPackage("com.zengge.blev2")
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(launch)
                Log.i("AlarmTriggerReceiver", "Launched com.zengge.blev2")
            }
        } catch (e: Exception) {
            Log.w("AlarmTriggerReceiver", "Failed to start target app: ${e.message}")
        }

        // Broadcast an automation request to the AccessibilityService to perform UI actions
        val now = System.currentTimeMillis()
        val durationMs = if (originalAlarmMs > now) (originalAlarmMs - now) else AlarmMonitorService.PREWARN_MS
        val auto = Intent("com.example.alarmwatcher.ACTION_RUN_AUTOMATION").apply {
            putExtra("duration_ms", durationMs)
            putExtra("target_r", 255)
            putExtra("target_g", 230)
            putExtra("target_b", 210)
            putExtra("room_name_click", "Chambre")
            putExtra("fallback_room", "Bureau")
        }
        context.sendBroadcast(auto)

        // Always show fallback notification in case automation is not enabled or fails
        NotificationHelper.showFallbackNotification(context)
    }
}

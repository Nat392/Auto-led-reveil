package com.example.alarmwatcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SunsetAutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            SunsetAutomationScheduler.ACTION_REFRESH_SCHEDULE -> handleRefresh(context)
            SunsetAutomationScheduler.ACTION_APPLY_SCENE -> handleApplyScene(context, intent)
            else -> Log.w(TAG, "Ignoring unknown action=${intent.action}")
        }
    }

    private fun handleRefresh(context: Context) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                SunsetAutomationScheduler.refreshAndSchedule(context.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh sunset schedule", e)
                DiscordCrashReporter.reportNonFatal(
                    context = context.applicationContext,
                    throwable = e,
                    source = "SunsetAutomationReceiver.handleRefresh"
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleApplyScene(context: Context, intent: Intent) {
        if (!BlePermissionSupport.hasBluetoothConnectPermission(context)) {
            Log.w(TAG, "Skipping sunset scene: BLUETOOTH_CONNECT permission is not granted")
            return
        }

        val serviceIntent = Intent(context, SunsetSceneService::class.java).apply {
            action = SunsetAutomationScheduler.ACTION_APPLY_SCENE
            putExtra(
                SunsetAutomationScheduler.EXTRA_TARGET_ZONE,
                intent.getStringExtra(SunsetAutomationScheduler.EXTRA_TARGET_ZONE)
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            @Suppress("DEPRECATION")
            context.startService(serviceIntent)
        }
    }

    private companion object {
        const val TAG = "SunsetAutomation"
    }
}
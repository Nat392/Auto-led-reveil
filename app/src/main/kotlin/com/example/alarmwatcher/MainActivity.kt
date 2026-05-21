package com.example.alarmwatcher

import android.app.AlarmManager
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val tag = "MainActivity"

    private val requestBluetoothConnect = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.i(tag, "BLUETOOTH_CONNECT granted=$granted")
    }

    private val requestScheduleExact = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* handle result if needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestBluetoothConnect.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val alarmManager = getSystemService(AlarmManager::class.java)
        val canScheduleExact = alarmManager?.canScheduleExactAlarms() == true
        val nextAlarmClock = alarmManager?.nextAlarmClock

        val debugLog = buildString {
            appendLine("MainActivity.onCreate()")
            appendLine("savedInstanceState=${savedInstanceState != null}")
            appendLine("intentAction=${intent?.action ?: "(none)"}")
            appendLine("packageName=$packageName")
            appendLine("versionName=${BuildConfig.VERSION_NAME}")
            appendLine("versionCode=${BuildConfig.VERSION_CODE}")
            appendLine("targetSdk=35")
            appendLine("android=${Build.VERSION.RELEASE} api=${Build.VERSION.SDK_INT}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("canScheduleExactAlarms=$canScheduleExact")
            appendLine("nextAlarmClock=${nextAlarmClock?.triggerTime ?: "none"}")
            appendLine("class=MainActivity")
            appendLine("finishWillRun=true")
        }

        Log.i(tag, debugLog)
        DiscordCrashReporter.reportDebugBlocking(
            context = this,
            source = "MainActivity.onCreate",
            details = debugLog
        )

        val am = getSystemService(AlarmManager::class.java)
        if (am != null && !am.canScheduleExactAlarms()) {
            // open system UI to grant SCHEDULE_EXACT_ALARM to this app
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            requestScheduleExact.launch(intent)
        }

        // Rescan and schedule the next pre-warn directly.
        AlarmMonitor.scanNextAlarmAndSchedule(this)

        finish() // no UI needed
    }
}

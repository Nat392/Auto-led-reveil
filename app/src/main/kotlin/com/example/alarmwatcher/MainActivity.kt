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
    ) { _ -> }

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

        val am = getSystemService(AlarmManager::class.java)
        if (am != null && !am.canScheduleExactAlarms()) {
            // open system UI to grant SCHEDULE_EXACT_ALARM to this app
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            requestScheduleExact.launch(intent)
        }

        // Rescan and schedule the next pre-warn directly.
        AlarmMonitor.scanNextAlarmAndSchedule(this)

        // If launched with diagnostic action, run BLE diagnostic routine.
        if (intent?.action == "com.example.alarmwatcher.DIAGNOSTIC") {
            Thread {
                try {
                    val mac = BuildConfig.ZENGGE_BULB_MAC.trim()
                    ZenggeBulbController.diagnosticApplyScene(this, mac, 255, 230, 210, 255)
                } catch (e: Exception) {
                    Log.e(tag, "Diagnostic failed", e)
                }
            }.start()
            // let the service run while we finish
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else {
            finish() // no UI needed
        }
    }
}

package com.example.alarmwatcher

import android.app.AlarmManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val requestScheduleExact = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* handle result if needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Minimal UI: just request the exact alarm affordance if needed
        // Check whether we can schedule exact alarms and, if not, send user to settings
        val am = getSystemService(AlarmManager::class.java)
        if (am != null && !am.canScheduleExactAlarms()) {
            // open system UI to grant SCHEDULE_EXACT_ALARM to this app
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            requestScheduleExact.launch(intent)
        }

        // Rescan and schedule the next pre-warn directly.
        AlarmMonitor.scanNextAlarmAndSchedule(this)

        finish() // no UI needed
    }
}

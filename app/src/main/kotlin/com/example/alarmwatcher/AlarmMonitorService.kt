package com.example.alarmwatcher

import android.app.*
import android.content.*
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class AlarmMonitorService : Service() {
    private val TAG = "AlarmMonitorService"
    private lateinit var alarmManager: AlarmManager
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Alarm change broadcast received: ${intent?.action}")
            scanNextAlarmAndSchedule()
        }
    }

    override fun onCreate() {
        super.onCreate()
        alarmManager = getSystemService(AlarmManager::class.java)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // listen for system alarm changes
        val filter = IntentFilter().apply {
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        registerReceiver(receiver, filter)

        scanNextAlarmAndSchedule()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun scanNextAlarmAndSchedule() {
        try {
            val next = alarmManager.nextAlarmClock
            if (next != null) {
                val trigger = next.triggerTime
                Log.d(TAG, "Next alarm at $trigger")
                // schedule a pre-warn 30 minutes before
                val preWarnAt = trigger - PREWARN_MS
                if (preWarnAt > System.currentTimeMillis()) {
                    AlarmScheduler.schedulePreWarn(this, preWarnAt, trigger)
                } else {
                    Log.d(TAG, "Pre-warn time already passed")
                }
            } else {
                Log.d(TAG, "No next alarm available")
                // cancel any previously scheduled pre-warns
                AlarmScheduler.cancelPreWarn(this)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing permission to read alarms: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected alarm scan failure", e)
            DiscordCrashReporter.reportNonFatal(
                context = this,
                throwable = e,
                source = "AlarmMonitorService.scanNextAlarmAndSchedule"
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, "Alarm Watcher", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Alarm Watcher")
            .setContentText("Monitoring device alarms")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "alarm_watcher_channel"
        const val NOTIFICATION_ID = 1001
        const val PREWARN_MS = 30 * 60 * 1000L
    }
}

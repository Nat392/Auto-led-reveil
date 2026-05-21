package com.example.alarmwatcher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val CHANNEL_FALLBACK = "alarm_watcher_fallback"
    private const val ID_FALLBACK = 2001

    fun showFallbackNotification(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_FALLBACK, "Alarm Watcher Actions", NotificationManager.IMPORTANCE_HIGH))
        }

        val launch = context.packageManager.getLaunchIntentForPackage("com.zengge.blev2")
        val pi = if (launch != null) PendingIntent.getActivity(context, 0, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE) else null

        val n = NotificationCompat.Builder(context, CHANNEL_FALLBACK)
            .setContentTitle("Pré-alarme")
            .setContentText("Taper pour ouvrir l'app cible")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(ID_FALLBACK, n)
    }
}

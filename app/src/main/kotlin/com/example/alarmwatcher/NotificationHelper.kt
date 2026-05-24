package com.example.alarmwatcher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val TAG = "NotificationHelper"
    private const val CHANNEL_FALLBACK = "alarm_watcher_fallback"
    private const val ID_FALLBACK = 2001
    internal var notificationBuilderFactory: (Context, String) -> NotificationCompat.Builder = { context, channelId ->
        NotificationCompat.Builder(context, channelId)
    }

    fun showFallbackNotification(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_FALLBACK, "Alarm Watcher Actions", NotificationManager.IMPORTANCE_HIGH))
        }

        val launch = context.packageManager.getLaunchIntentForPackage("com.zengge.blev2")
        val pi = if (launch != null) {
            PendingIntent.getActivity(
                context,
                0,
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            Log.w(TAG, "Impossible de créer l'action de notification: com.zengge.blev2 n'est pas installée")
            null
        }

        val contentText = if (pi != null) {
            "Taper pour ouvrir l'app cible"
        } else {
            "App cible non installée"
        }

        val n = notificationBuilderFactory(context, CHANNEL_FALLBACK)
            .setContentTitle("Pré-alarme")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(ID_FALLBACK, n)
    }
}

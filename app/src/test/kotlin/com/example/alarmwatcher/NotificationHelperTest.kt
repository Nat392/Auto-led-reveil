package com.example.alarmwatcher

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotificationHelperTest {
    private val context = mockk<Context>()
    private val notificationManager = mockk<NotificationManager>(relaxed = true)
    private val packageManager = mockk<PackageManager>()
    private val launchIntent = mockk<Intent>(relaxed = true)
    private val pendingIntent = mockk<PendingIntent>(relaxed = true)
    private val notification = mockk<Notification>(relaxed = true)
    private val builder = mockk<NotificationCompat.Builder>(relaxed = true)
    private val defaultBuilderFactory = NotificationHelper.notificationBuilderFactory

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        mockkStatic(PendingIntent::class)

        every { Log.w(any(), any<String>()) } returns 0
        every { context.getSystemService(NotificationManager::class.java) } returns notificationManager
        every { context.packageManager } returns packageManager
        every { notificationManager.createNotificationChannel(any()) } returns Unit
        every { notificationManager.notify(any(), any()) } returns Unit
        every { launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } returns launchIntent
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns pendingIntent

        every { builder.setContentTitle(any<String>()) } returns builder
        every { builder.setContentText(any<String>()) } returns builder
        every { builder.setSmallIcon(any<Int>()) } returns builder
        every { builder.setContentIntent(pendingIntent) } returns builder
        every { builder.setContentIntent(null) } returns builder
        every { builder.setAutoCancel(any<Boolean>()) } returns builder
        every { builder.build() } returns notification

        NotificationHelper.notificationBuilderFactory = { _, _ -> builder }
    }

    @AfterEach
    fun tearDown() {
        NotificationHelper.notificationBuilderFactory = defaultBuilderFactory
        unmockkAll()
    }

    @Test
    fun `builds a fallback notification that opens the target app when it is installed`() {
        every { packageManager.getLaunchIntentForPackage("com.zengge.blev2") } returns launchIntent

        NotificationHelper.showFallbackNotification(context)

        verify(exactly = 1) { PendingIntent.getActivity(any(), 0, launchIntent, PendingIntent.FLAG_IMMUTABLE) }
        verify(exactly = 1) { builder.setContentTitle("Pré-alarme") }
        verify(exactly = 1) { builder.setContentText("Taper pour ouvrir l'app cible") }
        verify(exactly = 1) { builder.setAutoCancel(true) }
        verify(exactly = 1) { builder.build() }
        verify(exactly = 1) { notificationManager.notify(any(), any()) }
    }

    @Test
    fun `builds a fallback notification without a launch action when the target app is missing`() {
        every { packageManager.getLaunchIntentForPackage("com.zengge.blev2") } returns null

        NotificationHelper.showFallbackNotification(context)

        verify(exactly = 0) { PendingIntent.getActivity(any(), any(), any(), any()) }
        verify(exactly = 1) { builder.setContentTitle("Pré-alarme") }
        verify(exactly = 1) { builder.setContentText("App cible non installée") }
        verify(exactly = 1) { builder.setAutoCancel(true) }
        verify(exactly = 1) { builder.build() }
        verify(exactly = 1) { notificationManager.notify(any(), any()) }
    }
}

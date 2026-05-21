package com.example.alarmwatcher

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Process
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

class AlarmWatcherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashScreenshotStore.install(this)

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (fatalDispatchInProgress.compareAndSet(false, true)) {
                try {
                    DiscordCrashReporter.reportFatalBlocking(
                        context = this,
                        throwable = throwable,
                        threadName = thread.name
                    )
                } finally {
                    fatalDispatchInProgress.set(false)
                }
            }

            previousHandler?.uncaughtException(thread, throwable) ?: run {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    companion object {
        private val fatalDispatchInProgress = AtomicBoolean(false)
    }
}

object CrashScreenshotStore : Application.ActivityLifecycleCallbacks {
    @Volatile
    private var currentActivity: WeakReference<Activity>? = null

    @Volatile
    private var installed = false

    fun install(application: Application) {
        if (installed) return
        installed = true
        application.registerActivityLifecycleCallbacks(this)
    }

    fun captureLatestScreenshot(timeoutMs: Long = 800L): ByteArray? {
        val activity = currentActivity?.get() ?: return null
        return ScreenshotCapture.capture(activity, timeoutMs)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        currentActivity = WeakReference(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        currentActivity = WeakReference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity?.get() == activity) {
            currentActivity = null
        }
    }
}
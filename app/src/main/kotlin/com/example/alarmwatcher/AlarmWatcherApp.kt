package com.example.alarmwatcher

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Process
import android.util.Log
import com.example.alarmwatcher.settings.AppSettingsCache
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

class AlarmWatcherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashScreenshotStore.install(this)
        AppSettingsCache.start(this)

        // Au démarrage du processus, aucun NightFadeService ne peut être en cours d'exécution :
        // on efface les drapeaux "running" persistés pour permettre à AlarmMonitorRunner de
        // reprendre immédiatement un fondu nocturne déjà "fired" mais interrompu (ex : service
        // tué par une réinstallation pendant un fondu en cours).
        NightFadeRunningStore.clearAll(this)

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (fatalDispatchInProgress.compareAndSet(false, true)) {
                try {
                    DiscordCrashReporter.reportFatalBlocking(
                        context = this,
                        throwable = throwable,
                        threadName = thread.name,
                    )
                } catch (reportingError: Throwable) {
                    Log.e(TAG, "Erreur pendant l'envoi du crash fatal", reportingError)
                    Log.e(TAG, "Exception fatale d'origine", throwable)
                } finally {
                    fatalDispatchInProgress.set(false)
                }
            }

            previousHandler?.uncaughtException(thread, throwable) ?: run {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(FATAL_EXIT_CODE)
            }
        }
    }

    companion object {
        private const val TAG = "AlarmWatcherApp"
        private const val FATAL_EXIT_CODE = 10
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

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
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

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity?.get() == activity) {
            currentActivity = null
        }
    }
}

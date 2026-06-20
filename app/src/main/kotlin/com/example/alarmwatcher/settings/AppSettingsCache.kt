package com.example.alarmwatcher.settings

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Cache mémoire des [AppSettings], lu de façon synchrone par les composants qui ne peuvent pas
 * suspendre (objets singletons, `BroadcastReceiver`, planification d'alarmes). [start] doit être
 * appelé une fois au démarrage de l'application pour maintenir [current] à jour à partir de
 * [AppSettingsStore].
 */
internal object AppSettingsCache {
    @Volatile
    var current: AppSettings = AppSettings()

    fun start(context: Context) {
        val store = AppSettingsStore.create(context)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            store.settingsFlow.collect { current = it }
        }
    }
}

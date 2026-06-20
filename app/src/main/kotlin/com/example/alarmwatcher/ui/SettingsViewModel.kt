package com.example.alarmwatcher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarmwatcher.settings.AppSettings
import com.example.alarmwatcher.settings.AppSettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Expose les réglages persistés (Catégorie A) à l'écran Réglages et relaie les modifications
 * de l'utilisateur vers [AppSettingsStore].
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AppSettingsStore.create(application)

    val settings: StateFlow<AppSettings> =
        store.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { store.update(transform) }
    }
}

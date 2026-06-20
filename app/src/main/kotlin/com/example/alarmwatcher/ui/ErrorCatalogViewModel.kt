package com.example.alarmwatcher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarmwatcher.AppErrorLogStore
import com.example.alarmwatcher.DiscordCrashReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** État de l'envoi sélectif du catalogue d'erreurs vers Discord. */
enum class CatalogSendStatus { IDLE, SENDING, SUCCESS, FAILURE }

/**
 * Expose le catalogue persistant d'erreurs/avertissements ([AppErrorLogStore]) à l'écran Journaux,
 * avec sélection multiple et envoi à la demande vers Discord.
 */
class ErrorCatalogViewModel(application: Application) : AndroidViewModel(application) {
    private val _entries = MutableStateFlow<List<AppErrorLogStore.Entry>>(emptyList())
    val entries: StateFlow<List<AppErrorLogStore.Entry>> = _entries.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _sendStatus = MutableStateFlow(CatalogSendStatus.IDLE)
    val sendStatus: StateFlow<CatalogSendStatus> = _sendStatus.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _entries.value = AppErrorLogStore.all(getApplication())
    }

    fun toggleSelection(id: String) {
        _selectedIds.value =
            if (id in _selectedIds.value) {
                _selectedIds.value - id
            } else {
                _selectedIds.value + id
            }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun sendSelected() {
        if (_sendStatus.value == CatalogSendStatus.SENDING) return
        val selected = _entries.value.filter { it.id in _selectedIds.value }
        if (selected.isEmpty()) return

        _sendStatus.value = CatalogSendStatus.SENDING
        viewModelScope.launch {
            val success =
                withContext(Dispatchers.IO) {
                    DiscordCrashReporter.sendCatalogEntries(getApplication(), selected)
                }
            if (success) {
                AppErrorLogStore.markSent(getApplication(), selected.map { it.id }.toSet())
                refresh()
                _selectedIds.value = emptySet()
            }
            _sendStatus.value = if (success) CatalogSendStatus.SUCCESS else CatalogSendStatus.FAILURE
        }
    }

    fun acknowledgeSendResult() {
        _sendStatus.value = CatalogSendStatus.IDLE
    }

    fun clearCatalog() {
        AppErrorLogStore.clear(getApplication())
        refresh()
        _selectedIds.value = emptySet()
    }
}

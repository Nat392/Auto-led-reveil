package com.example.alarmwatcher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarmwatcher.DiscordCrashReporter
import com.example.alarmwatcher.LogcatExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** État de l'export des journaux vers Discord, utilisé par l'écran Journaux. */
enum class LogExportStatus { IDLE, EXPORTING, SUCCESS, FAILURE }

/**
 * Capture les journaux récents de l'application et les envoie au webhook Discord configuré.
 */
class LogExportViewModel(application: Application) : AndroidViewModel(application) {
    private val _status = MutableStateFlow(LogExportStatus.IDLE)
    val status: StateFlow<LogExportStatus> = _status.asStateFlow()

    fun exportLogs() {
        if (_status.value == LogExportStatus.EXPORTING) return
        _status.value = LogExportStatus.EXPORTING
        viewModelScope.launch {
            val success =
                withContext(Dispatchers.IO) {
                    val logText = LogcatExporter.captureRecentLogs()
                    DiscordCrashReporter.sendLogExport(getApplication(), logText)
                }
            _status.value = if (success) LogExportStatus.SUCCESS else LogExportStatus.FAILURE
        }
    }

    fun acknowledgeResult() {
        _status.value = LogExportStatus.IDLE
    }
}

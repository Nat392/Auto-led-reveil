package com.example.alarmwatcher

import java.io.IOException

/**
 * Capture les journaux récents de l'application via `logcat`. Android ne permet à une application
 * de lire que ses propres entrées de journal (pas celles du système ni des autres applications),
 * sans nécessiter de permission particulière.
 */
internal object LogcatExporter {
    private const val DEFAULT_LINE_COUNT = 2000

    fun captureRecentLogs(lineCount: Int = DEFAULT_LINE_COUNT): String {
        return try {
            val process =
                Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", lineCount.toString()))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output.ifBlank { "(journal vide)" }
        } catch (e: IOException) {
            "Impossible de récupérer les journaux : ${e.message}"
        }
    }
}

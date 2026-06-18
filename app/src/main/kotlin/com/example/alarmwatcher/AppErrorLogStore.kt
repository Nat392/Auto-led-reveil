package com.example.alarmwatcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Catalogue persistant (SharedPreferences) des erreurs et avertissements de l'application,
 * consultable depuis l'onglet Journaux même après que le logcat se soit vidé. Alimenté
 * automatiquement par [DiscordCrashReporter.reportNonFatal]/[DiscordCrashReporter.reportFatalBlocking],
 * et ponctuellement par des appels directs pour des cas volontairement non envoyés sur Discord.
 */
object AppErrorLogStore {
    private const val PREFS_NAME = "app_error_log"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 200
    private const val MAX_STACKTRACE_CHARS = 4000
    private const val TRUNCATION_SUFFIX = "\n… (tronqué)"

    // Sérialise toutes les opérations de lecture-modification-écriture pour éviter qu'un appel
    // concurrent (ex. deux zones BLE en échec simultané) n'écrase silencieusement l'autre.
    private val lock = Any()

    enum class Level { ERROR, WARNING }

    data class Entry(
        val id: String,
        val timestampMs: Long,
        val level: Level,
        val title: String,
        val source: String,
        val message: String,
        val stacktrace: String?,
        val sentToDiscord: Boolean = false,
    )

    fun record(
        context: Context,
        level: Level,
        title: String,
        source: String,
        message: String,
        stacktrace: String? = null,
    ): Entry {
        val appContext = context.applicationContext
        val entry =
            Entry(
                id = UUID.randomUUID().toString(),
                timestampMs = System.currentTimeMillis(),
                level = level,
                title = title,
                source = source,
                message = message,
                stacktrace = truncateStacktrace(stacktrace),
            )
        synchronized(lock) {
            val updated = (listOf(entry) + load(appContext)).take(MAX_ENTRIES)
            save(appContext, updated)
        }
        return entry
    }

    fun all(context: Context): List<Entry> = load(context.applicationContext)

    fun markSent(
        context: Context,
        ids: Set<String>,
    ) {
        val appContext = context.applicationContext
        synchronized(lock) {
            val updated = load(appContext).map { if (it.id in ids) it.copy(sentToDiscord = true) else it }
            save(appContext, updated)
        }
    }

    fun clear(context: Context) {
        synchronized(lock) { save(context.applicationContext, emptyList()) }
    }

    private fun truncateStacktrace(stacktrace: String?): String? {
        if (stacktrace == null || stacktrace.length <= MAX_STACKTRACE_CHARS) return stacktrace
        return stacktrace.take(MAX_STACKTRACE_CHARS) + TRUNCATION_SUFFIX
    }

    private fun load(appContext: Context): List<Entry> {
        val raw = prefs(appContext).getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index -> array.getJSONObject(index).toEntry() }
        }.getOrDefault(emptyList())
    }

    private fun save(
        appContext: Context,
        entries: List<Entry>,
    ) {
        val array = JSONArray()
        entries.forEach { array.put(it.toJson()) }
        prefs(appContext).edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private fun Entry.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("timestampMs", timestampMs)
            .put("level", level.name)
            .put("title", title)
            .put("source", source)
            .put("message", message)
            .put("stacktrace", stacktrace)
            .put("sentToDiscord", sentToDiscord)

    private fun JSONObject.toEntry(): Entry =
        Entry(
            id = getString("id"),
            timestampMs = getLong("timestampMs"),
            level = runCatching { Level.valueOf(getString("level")) }.getOrDefault(Level.ERROR),
            title = getString("title"),
            source = getString("source"),
            message = getString("message"),
            stacktrace = if (isNull("stacktrace")) null else getString("stacktrace"),
            sentToDiscord = optBoolean("sentToDiscord", false),
        )

    private fun prefs(appContext: Context) = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

package com.example.alarmwatcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object DiscordCrashReporter : CrashReporterApi {
    private const val TAG = "DiscordCrashReporter"
    private const val SCREENSHOT_FILENAME = "crash_screenshot.png"
    private const val STACKTRACE_FILENAME = "stacktrace.txt"
    private const val DEBUG_LOG_FILENAME = "debug_log.txt"
    private const val HTTP_TIMEOUT_MS = 10_000
    private const val FATAL_WAIT_TIMEOUT_MS = 4_500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun reportNonFatal(
        context: Context,
        throwable: Throwable,
        source: String?,
    ): Job =
        scope.launch {
            sendReport(
                context = context.applicationContext,
                throwable = throwable,
                source = source,
                fatal = false,
            )
        }

    override fun reportFatalBlocking(
        context: Context,
        throwable: Throwable,
        threadName: String?,
    ) {
        runBlocking(Dispatchers.IO) {
            withTimeout(FATAL_WAIT_TIMEOUT_MS) {
                sendReport(
                    context = context.applicationContext,
                    throwable = throwable,
                    source = threadName ?: Thread.currentThread().name,
                    fatal = true,
                )
            }
        }
    }

    override fun reportDebugBlocking(
        context: Context,
        source: String,
        details: String,
    ) {
        runBlocking(Dispatchers.IO) {
            withTimeout(FATAL_WAIT_TIMEOUT_MS) {
                sendDebugReport(
                    context = context.applicationContext,
                    source = source,
                    details = details,
                )
            }
        }
    }

    private suspend fun sendReport(
        context: Context,
        throwable: Throwable,
        source: String?,
        fatal: Boolean,
    ) {
        val webhookUrl = BuildConfig.DISCORD_WEBHOOK_URL.trim()
        if (webhookUrl.isBlank()) {
            Log.w(TAG, "DISCORD_WEBHOOK_URL is empty, skipping crash report")
            return
        }

        val screenshotBytes = captureScreenshotBestEffort()
        val stacktrace = throwable.stackTraceToString()
        val payload =
            buildPayload(
                context = context,
                stacktrace = stacktrace,
                source = source,
                fatal = fatal,
                hasScreenshot = screenshotBytes != null,
            )

        val attachments =
            buildList {
                if (screenshotBytes != null) {
                    add(
                        Attachment(
                            fieldName = "files[0]",
                            fileName = SCREENSHOT_FILENAME,
                            contentType = "image/png",
                            bytes = screenshotBytes,
                        ),
                    )
                    add(
                        Attachment(
                            fieldName = "files[1]",
                            fileName = STACKTRACE_FILENAME,
                            contentType = "text/plain; charset=UTF-8",
                            bytes = stacktrace.toByteArray(Charsets.UTF_8),
                        ),
                    )
                } else {
                    add(
                        Attachment(
                            fieldName = "files[0]",
                            fileName = STACKTRACE_FILENAME,
                            contentType = "text/plain; charset=UTF-8",
                            bytes = stacktrace.toByteArray(Charsets.UTF_8),
                        ),
                    )
                }
            }

        val success =
            postMultipart(
                webhookUrl = webhookUrl,
                payloadJson = payload,
                attachments = attachments,
            )

        if (!success) {
            Log.w(TAG, "Discord webhook returned a non-success response")
        }
    }

    private suspend fun sendDebugReport(
        context: Context,
        source: String,
        details: String,
    ) {
        val webhookUrl = BuildConfig.DISCORD_WEBHOOK_URL.trim()
        if (webhookUrl.isBlank()) {
            Log.w(TAG, "DISCORD_WEBHOOK_URL is empty, skipping debug report")
            return
        }

        val payload =
            buildDebugPayload(
                context = context,
                source = source,
                details = details,
            )

        val attachments =
            listOf(
                Attachment(
                    fieldName = "files[0]",
                    fileName = DEBUG_LOG_FILENAME,
                    contentType = "text/plain; charset=UTF-8",
                    bytes = details.toByteArray(Charsets.UTF_8),
                ),
            )

        val success =
            postMultipart(
                webhookUrl = webhookUrl,
                payloadJson = payload,
                attachments = attachments,
            )

        if (!success) {
            Log.w(TAG, "Discord webhook returned a non-success response for debug report")
        }
    }

    private fun captureScreenshotBestEffort(): ByteArray? {
        return try {
            CrashScreenshotStore.captureLatestScreenshot()
        } catch (e: Exception) {
            Log.w(TAG, "Screenshot capture failed", e)
            null
        }
    }

    internal fun buildPayload(
        context: Context,
        stacktrace: String,
        source: String?,
        fatal: Boolean,
        hasScreenshot: Boolean,
    ): JSONObject {
        val timestamp = isoTimestamp()
        val packageInfo =
            runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }.getOrNull()
        val appVersionName = packageInfo?.versionName ?: BuildConfig.VERSION_NAME

        val embedFields =
            JSONArray().apply {
                put(field("Date", timestamp, inline = true))
                put(field("App", appVersionName, inline = true))
                put(field("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", inline = true))
                put(field("Appareil", "${Build.MANUFACTURER} ${Build.MODEL}", inline = true))
                if (!source.isNullOrBlank()) {
                    put(field("Source", source, inline = true))
                }
                put(
                    field(
                        "Screenshot",
                        if (hasScreenshot) "Pièce jointe envoyée" else "Aucune capture disponible",
                        inline = true,
                    ),
                )
            }

        val maxFields = 25
        stacktrace.chunked(900).forEachIndexed { index, chunk ->
            if (embedFields.length() < maxFields) {
                embedFields.put(field("Stacktrace ${index + 1}", chunk, inline = false))
            }
        }

        val embed =
            JSONObject()
                .put("title", if (fatal) "Crash fatal détecté" else "Erreur non-fatale détectée")
                .put("color", if (fatal) 0xE74C3C else 0xF1C40F)
                .put("timestamp", timestamp)
                .put("fields", embedFields)

        if (hasScreenshot) {
            embed.put("image", JSONObject().put("url", "attachment://$SCREENSHOT_FILENAME"))
        }

        return JSONObject()
            .put(
                "content",
                if (fatal) {
                    "Une exception fatale a été capturée."
                } else {
                    "Une erreur non-fatale a été capturée."
                },
            )
            .put("embeds", JSONArray().put(embed))
    }

    internal fun buildDebugPayload(
        context: Context,
        source: String,
        details: String,
    ): JSONObject {
        val timestamp = isoTimestamp()
        val packageInfo =
            runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }.getOrNull()
        val appVersionName = packageInfo?.versionName ?: BuildConfig.VERSION_NAME

        val embedFields =
            JSONArray().apply {
                put(field("Date", timestamp, inline = true))
                put(field("App", appVersionName, inline = true))
                put(field("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", inline = true))
                put(field("Appareil", "${Build.MANUFACTURER} ${Build.MODEL}", inline = true))
                put(field("Source", source, inline = true))
            }

        details.chunked(900).take(20).forEachIndexed { index, chunk ->
            embedFields.put(field("Debug ${index + 1}", chunk, inline = false))
        }

        val embed =
            JSONObject()
                .put("title", "Debug launch log")
                .put("color", 0x3498DB)
                .put("timestamp", timestamp)
                .put("fields", embedFields)

        return JSONObject()
            .put("content", "Un log de debug au démarrage a été capturé.")
            .put("embeds", JSONArray().put(embed))
    }

    private fun field(
        name: String,
        value: String,
        inline: Boolean,
    ): JSONObject =
        JSONObject()
            .put("name", name)
            .put("value", if (value.isBlank()) "(vide)" else value)
            .put("inline", inline)

    private fun postMultipart(
        webhookUrl: String,
        payloadJson: JSONObject,
        attachments: List<Attachment>,
    ): Boolean {
        val boundary = "----AlarmWatcherBoundary${System.currentTimeMillis()}"
        val url = URL(webhookUrl + if (webhookUrl.contains("?")) "&wait=true" else "?wait=true")
        val connection =
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = HTTP_TIMEOUT_MS
                readTimeout = HTTP_TIMEOUT_MS
                doInput = true
                doOutput = true
                useCaches = false
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Accept", "application/json")
            }

        return try {
            DataOutputStream(connection.outputStream).use { output ->
                writeTextPart(
                    output = output,
                    boundary = boundary,
                    name = "payload_json",
                    value = payloadJson.toString(),
                    contentType = "application/json; charset=UTF-8",
                )

                attachments.forEach { attachment ->
                    writeFilePart(
                        output = output,
                        boundary = boundary,
                        fieldName = attachment.fieldName,
                        fileName = attachment.fileName,
                        contentType = attachment.contentType,
                        bytes = attachment.bytes,
                    )
                }

                output.writeBytes("--$boundary--\r\n")
                output.flush()
            }

            val code = connection.responseCode
            val success = code in 200..299
            if (!success) {
                val errorBody = connection.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) }
                Log.w(TAG, "Discord webhook responded with HTTP $code: ${errorBody.orEmpty()}")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Discord crash report", e)
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun writeTextPart(
        output: DataOutputStream,
        boundary: String,
        name: String,
        value: String,
        contentType: String,
    ) {
        output.writeBytes("--$boundary\r\n")
        output.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n")
        output.writeBytes("Content-Type: $contentType\r\n\r\n")
        output.write(value.toByteArray(Charsets.UTF_8))
        output.writeBytes("\r\n")
    }

    private fun writeFilePart(
        output: DataOutputStream,
        boundary: String,
        fieldName: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
    ) {
        output.writeBytes("--$boundary\r\n")
        output.writeBytes("Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"\r\n")
        output.writeBytes("Content-Type: $contentType\r\n\r\n")
        output.write(bytes)
        output.writeBytes("\r\n")
    }

    private fun isoTimestamp(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        formatter.timeZone = TimeZone.getDefault()
        return formatter.format(Date())
    }

    private data class Attachment(
        val fieldName: String,
        val fileName: String,
        val contentType: String,
        val bytes: ByteArray,
    )
}

object ScreenshotCapture {
    fun capture(
        activity: android.app.Activity,
        timeoutMs: Long,
    ): ByteArray? {
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            captureOnMainThread(activity)
        } else {
            captureViaMainThread(activity, timeoutMs)
        }
    }

    private fun captureViaMainThread(
        activity: android.app.Activity,
        timeoutMs: Long,
    ): ByteArray? {
        val latch = CountDownLatch(1)
        var bytes: ByteArray? = null
        Handler(Looper.getMainLooper()).post {
            bytes = captureOnMainThread(activity)
            latch.countDown()
        }

        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return bytes
    }

    private fun captureOnMainThread(activity: android.app.Activity): ByteArray? {
        return try {
            val decorView = activity.window?.decorView ?: return null
            if (decorView.width <= 0 || decorView.height <= 0) return null

            val bitmap = Bitmap.createBitmap(decorView.width, decorView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            decorView.draw(canvas)
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
        } catch (_: Exception) {
            null
        }
    }
}

package com.example.alarmwatcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Exécute un fondu progressif idempotent vers un rouge quasi-éteint (~1%).
 *
 * Reprendra exactement la bonne couleur en fonction de l'horloge absolue (startTimeMs),
 * même si l'application est tuée ou redémarrée.
 */
internal class NightFadeRunner(
    private val bulbController: BulbControllerApi,
    private val crashReporter: CrashReporterApi,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(
        context: Context,
        zone: SunriseBulbZone,
        startTimeMs: Long,
        endTimeMs: Long,
    ) {
        val totalDurationMs = (endTimeMs - startTimeMs).toFloat().coerceAtLeast(1f)

        try {
            while (true) {
                val now = nowMs()
                if (now >= endTimeMs) {
                    break
                }

                // Magie de l'idempotence : progress est calculé par rapport à l'horloge système
                val elapsedSinceOfficialStartMs = (now - startTimeMs).toFloat()
                val progress = (elapsedSinceOfficialStartMs / totalDurationMs).coerceIn(0f, 1f)
                applyFadeStep(context, zone, progress)

                val timeToSleep = (endTimeMs - nowMs()).coerceAtMost(UPDATE_INTERVAL_MS)
                if (timeToSleep > 0L) {
                    delay(timeToSleep)
                }
            }

            applyFinalScene(context, zone)
        } catch (e: CancellationException) {
            Log.i(TAG, "Fondu nocturne annulé pour ${zone.macAddress}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Erreur durant le fondu nocturne (${zone.macAddress})", e)
            crashReporter.reportNonFatal(
                context = context,
                throwable = e,
                source = "NightFadeRunner.run",
            )
        }
    }

    private suspend fun applyFadeStep(
        context: Context,
        zone: SunriseBulbZone,
        progress: Float,
    ) {
        // Interpolation absolue
        val red = lerp(zone.sunsetR, TARGET_R, progress)
        val green = lerp(zone.sunsetG, 0, progress)
        val blue = lerp(zone.sunsetB, 0, progress)

        val success =
            bulbController.applyScene(
                context = context,
                macAddress = zone.macAddress,
                red = red,
                green = green,
                blue = blue,
                white = 0,
                brightnessPercent = 100,
            )

        if (!success) {
            Log.w(
                TAG,
                "Échec BLE pour ${zone.macAddress} à progress=$progress. " +
                    "Reprise automatique au prochain cycle.",
            )
            crashReporter.reportNonFatal(
                context = context,
                throwable = Exception("Échec de l'envoi BLE pour ${zone.macAddress} à progress=$progress"),
                source = "NightFadeRunner.run",
            )
        }
    }

    private suspend fun applyFinalScene(
        context: Context,
        zone: SunriseBulbZone,
    ) {
        bulbController.applyScene(
            context = context,
            macAddress = zone.macAddress,
            red = TARGET_R,
            green = 0,
            blue = 0,
            white = 0,
            brightnessPercent = 100,
        )
        Log.i(TAG, "Fondu nocturne terminé pour ${zone.macAddress}")
    }

    private fun lerp(
        from: Int,
        to: Int,
        progress: Float,
    ): Int = (from + (to - from) * progress).toInt().coerceIn(0, MAX_COLOR_VALUE)

    private companion object {
        const val TAG = "NightFadeRunner"

        // Rouge très faible ≈ 1% de luminosité
        const val TARGET_R = 3

        // Polling interval
        const val UPDATE_INTERVAL_MS = 60_000L

        const val MAX_COLOR_VALUE = 255
    }
}

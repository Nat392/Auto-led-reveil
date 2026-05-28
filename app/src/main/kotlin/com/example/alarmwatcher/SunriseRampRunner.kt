package com.example.alarmwatcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

internal class SunriseRampRunner(
    private val bulbController: BulbControllerApi,
    private val crashReporter: CrashReporterApi = DiscordCrashReporter,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(
        context: Context,
        macAddress: String,
        targetR: Int,
        targetG: Int,
        targetB: Int,
        durationMs: Long,
        onProgress: (currentStep: Int, totalSteps: Int) -> Unit = { _, _ -> },
    ) {
        val steps = SunriseRampSupport.computeStepCount(durationMs)
        val session = bulbController.openSession(context, macAddress)
        if (session == null) {
            Log.w(TAG, "Impossible d'ouvrir une session BLE pour la rampe")
            val errorMessage =
                "Impossible d'ouvrir une session BLE pour la rampe" +
                    " macAddress=$macAddress - durationMs=$durationMs - " +
                    "steps=$steps - targetR=$targetR - " +
                    "targetG=$targetG - targetB=$targetB"
            crashReporter.reportNonFatal(
                context = context,
                throwable = IllegalStateException(errorMessage),
                source = TAG,
            )
            return
        }

        val rampStartMs = nowMs()
        val rampDeadlineMs = rampStartMs + max(0L, durationMs - FINAL_WRITE_RESERVE_MS)
        val finalDeadlineMs = rampStartMs + durationMs
        var lastAppliedStep = -1
        var shouldSendFinalScene = true

        try {
            while (true) {
                val currentMs = nowMs()
                if (currentMs >= rampDeadlineMs) {
                    break
                }

                val elapsedMs = (currentMs - rampStartMs).coerceAtLeast(0L)
                val timeStep =
                    ((elapsedMs.toDouble() / durationMs.coerceAtLeast(1L).toDouble()) * steps)
                        .toInt()
                        .coerceIn(0, steps - 1)

                if (timeStep <= lastAppliedStep) {
                    val nextStepTimeMs = rampStartMs + (((lastAppliedStep + 1L) * durationMs) / steps)
                    val sleepMs = (min(nextStepTimeMs, rampDeadlineMs) - nowMs()).coerceAtLeast(0L)
                    if (sleepMs > 0L) {
                        delay(sleepMs)
                    }
                    continue
                }

                val palette = SunriseRampSupport.computeSceneAtStep(timeStep, steps, targetR, targetG, targetB)
                if (
                    !session.applyScene(
                        red = palette.red,
                        green = palette.green,
                        blue = palette.blue,
                        white = 0,
                    )
                ) {
                    Log.w(TAG, "Echec d'ecriture BLE pendant la rampe, arret de la sequence")
                    shouldSendFinalScene = false
                    break
                }
                lastAppliedStep = timeStep
                onProgress(timeStep, steps)

                val nextStepTimeMs = rampStartMs + (((timeStep + 1L) * durationMs) / steps)
                val sleepMs = (min(nextStepTimeMs, rampDeadlineMs) - nowMs()).coerceAtLeast(0L)
                if (sleepMs > 0L) {
                    delay(sleepMs)
                }
            }

            if (shouldSendFinalScene) {
                val finalDelayMs = (finalDeadlineMs - nowMs()).coerceAtLeast(0L)
                if (finalDelayMs > 0L) {
                    delay(finalDelayMs)
                }

                val finalPalette =
                    SunriseRampSupport.Scene(
                        targetR.coerceIn(0, 255),
                        targetG.coerceIn(0, 255),
                        targetB.coerceIn(0, 255),
                    )
                if (
                    session.applyScene(
                        red = finalPalette.red,
                        green = finalPalette.green,
                        blue = finalPalette.blue,
                        white = 0,
                    )
                ) {
                    onProgress(steps, steps)
                } else {
                    Log.w(TAG, "Echec d'ecriture BLE finale pour la rampe")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Erreur durant la rampe de luminosité", e)
            throw e
        } finally {
            session.close()
        }
    }

    private companion object {
        const val TAG = "SunriseService"
        const val FINAL_WRITE_RESERVE_MS = 400L
    }
}

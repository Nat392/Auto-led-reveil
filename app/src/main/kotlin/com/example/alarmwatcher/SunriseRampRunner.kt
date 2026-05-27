package com.example.alarmwatcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.max

internal class SunriseRampRunner(
    private val bulbController: BulbControllerApi,
    private val crashReporter: CrashReporterApi = DiscordCrashReporter,
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
        val stepDelayMs = max(1L, durationMs / steps)
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

        try {
            for (step in 0..steps) {
                val palette = SunriseRampSupport.computeSceneAtStep(step, steps, targetR, targetG, targetB)
                if (
                    !session.applyScene(
                        red = palette.red,
                        green = palette.green,
                        blue = palette.blue,
                        white = 0,
                    )
                ) {
                    Log.w(TAG, "Echec d'ecriture BLE pendant la rampe, arret de la sequence")
                    break
                }
                onProgress(step, steps)
                if (step < steps) {
                    delay(stepDelayMs)
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
    }
}

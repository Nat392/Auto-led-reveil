package com.example.alarmwatcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

internal class SunriseRampRunner(
    private val bulbController: BulbControllerApi,
    private val crashReporter: CrashReporterApi
) {
    suspend fun run(context: Context, macAddress: String, durationMs: Long) {
        val steps = min(
            SunriseRampSupport.MAX_RAMP_STEPS,
            max(1L, durationMs / SunriseRampSupport.MIN_STEP_DELAY_MS).toInt()
        )
        val stepDelayMs = max(1L, durationMs / steps)
        val session = bulbController.openSession(context, macAddress)
        if (session == null) {
            Log.w(TAG, "Impossible d'ouvrir une session BLE pour la rampe")
            return
        }

        try {
            for (step in 0..steps) {
                val palette = SunriseRampSupport.reportSceneAtStep(step, steps)
                session.applyScene(
                    red = palette.red,
                    green = palette.green,
                    blue = palette.blue,
                    white = 0
                )
                if (step < steps) {
                    delay(stepDelayMs)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur durant la rampe de luminosité", e)
            crashReporter.reportNonFatal(
                context = context,
                throwable = e,
                source = "SunriseService.rampJob"
            )
        } finally {
            session.close()
        }
    }

    private companion object {
        const val TAG = "SunriseService"
    }
}
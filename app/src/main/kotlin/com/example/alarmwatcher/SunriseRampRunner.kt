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
        val request =
            RampRequest(
                context = context,
                macAddress = macAddress,
                durationMs = durationMs,
                steps = SunriseRampSupport.computeStepCount(durationMs),
                targetR = targetR,
                targetG = targetG,
                targetB = targetB,
                onProgress = onProgress,
            )
        val session = openSessionOrReportFailure(request) ?: return

        val rampTiming = RampTiming.from(nowMs(), durationMs)

        try {
            runTimedRamp(
                session = session,
                timing = rampTiming,
                request = request,
            )

            waitUntil(rampTiming.finalDeadlineMs)
            sendFinalScene(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Erreur durant la rampe de luminosité", e)
            throw e
        } finally {
            session.close()
        }
    }

    private suspend fun openSessionOrReportFailure(request: RampRequest): BulbSession? {
        val session = bulbController.openSession(request.context, request.macAddress)
        if (session != null) {
            return session
        }

        Log.w(TAG, "Impossible d'ouvrir une session BLE pour la rampe")
        val errorMessage =
            "Impossible d'ouvrir une session BLE pour la rampe" +
                " macAddress=${request.macAddress} - durationMs=${request.durationMs} - " +
                "steps=${request.steps} - targetR=${request.targetR} - " +
                "targetG=${request.targetG} - targetB=${request.targetB}"
        crashReporter.reportNonFatal(
            context = request.context,
            throwable = IllegalStateException(errorMessage),
            source = TAG,
        )
        return null
    }

    private suspend fun runTimedRamp(
        session: BulbSession,
        timing: RampTiming,
        request: RampRequest,
    ) {
        var lastAppliedStep = -1
        while (nowMs() < timing.rampDeadlineMs) {
            val timeStep = computeTimedStep(timing.rampStartMs, timing.durationMs, request.steps)
            val shouldApplyStep = timeStep > lastAppliedStep

            if (shouldApplyStep) {
                val palette =
                    SunriseRampSupport.computeSceneAtStep(
                        timeStep,
                        request.steps,
                        request.targetR,
                        request.targetG,
                        request.targetB,
                    )
                if (!applySceneWithRetry(session, palette.red, palette.green, palette.blue)) {
                    Log.w(TAG, "Echec d'ecriture BLE pendant la rampe, arret de la sequence")
                    val errorMessage =
                        "Echec d'ecriture BLE pendant la rampe, arret de la sequence" +
                            " macAddress=${request.macAddress} - step=$timeStep/${request.steps}"
                    crashReporter.reportNonFatal(
                        context = request.context,
                        throwable = IllegalStateException(errorMessage),
                        source = "$TAG.runTimedRamp",
                    )
                    return
                }
                lastAppliedStep = timeStep
                request.onProgress(timeStep, request.steps)
            }

            waitUntil(
                min(
                    nextStepTargetMs(
                        rampStartMs = timing.rampStartMs,
                        durationMs = timing.durationMs,
                        steps = request.steps,
                        lastAppliedStep = lastAppliedStep,
                    ),
                    timing.rampDeadlineMs,
                ),
            )
        }
    }

    private suspend fun sendFinalScene(request: RampRequest) {
        if (
            bulbController.applyScene(
                context = request.context,
                macAddress = request.macAddress,
                red = request.targetR.coerceIn(MIN_RGB_VALUE, MAX_RGB_VALUE),
                green = request.targetG.coerceIn(MIN_RGB_VALUE, MAX_RGB_VALUE),
                blue = request.targetB.coerceIn(MIN_RGB_VALUE, MAX_RGB_VALUE),
                white = 0,
                brightnessPercent = 100,
            )
        ) {
            request.onProgress(request.steps, request.steps)
        } else {
            Log.w(TAG, "Echec d'ecriture BLE finale pour la rampe")
            val errorMessage = "Echec d'ecriture BLE finale pour la rampe macAddress=${request.macAddress}"
            crashReporter.reportNonFatal(
                context = request.context,
                throwable = IllegalStateException(errorMessage),
                source = "$TAG.sendFinalScene",
            )
        }
    }

    private suspend fun applyScene(
        session: BulbSession,
        red: Int,
        green: Int,
        blue: Int,
    ): Boolean =
        session.applyScene(
            red = red,
            green = green,
            blue = blue,
            white = 0,
        )

    private suspend fun applySceneWithRetry(
        session: BulbSession,
        red: Int,
        green: Int,
        blue: Int,
    ): Boolean {
        repeat(STEP_WRITE_MAX_ATTEMPTS) { attempt ->
            if (applyScene(session, red, green, blue)) {
                return true
            }
            if (attempt < STEP_WRITE_MAX_ATTEMPTS - 1) {
                delay(STEP_WRITE_RETRY_DELAY_MS)
            }
        }
        return false
    }

    private suspend fun waitUntil(targetMs: Long) {
        val sleepMs = (targetMs - nowMs()).coerceAtLeast(0L)
        if (sleepMs > 0L) {
            delay(sleepMs)
        }
    }

    private fun computeTimedStep(
        rampStartMs: Long,
        durationMs: Long,
        steps: Int,
    ): Int {
        val elapsedMs = (nowMs() - rampStartMs).coerceAtLeast(0L)
        return ((elapsedMs.toDouble() / durationMs.coerceAtLeast(1L).toDouble()) * steps)
            .toInt()
            .coerceIn(0, steps - 1)
    }

    private fun nextStepTargetMs(
        rampStartMs: Long,
        durationMs: Long,
        steps: Int,
        lastAppliedStep: Int,
    ): Long = rampStartMs + (((lastAppliedStep + 1L) * durationMs) / steps.coerceAtLeast(1))

    private data class RampRequest(
        val context: Context,
        val macAddress: String,
        val durationMs: Long,
        val steps: Int,
        val targetR: Int,
        val targetG: Int,
        val targetB: Int,
        val onProgress: (currentStep: Int, totalSteps: Int) -> Unit,
    )

    private companion object {
        const val TAG = "SunriseService"
        const val FINAL_WRITE_RESERVE_MS = 400L
        const val MIN_RGB_VALUE = 0
        const val MAX_RGB_VALUE = 255

        // Les steps sont espacés de SunriseRampSupport.MIN_STEP_DELAY_MS (250ms) : un délai de
        // retry court permet d'absorber un glitch BLE transitoire sans décaler la rampe.
        const val STEP_WRITE_MAX_ATTEMPTS = 3
        const val STEP_WRITE_RETRY_DELAY_MS = 150L
    }

    private data class RampTiming(
        val rampStartMs: Long,
        val rampDeadlineMs: Long,
        val finalDeadlineMs: Long,
        val durationMs: Long,
    ) {
        companion object {
            fun from(
                rampStartMs: Long,
                durationMs: Long,
            ): RampTiming {
                val rampDeadlineMs = rampStartMs + max(0L, durationMs - FINAL_WRITE_RESERVE_MS)
                return RampTiming(
                    rampStartMs = rampStartMs,
                    rampDeadlineMs = rampDeadlineMs,
                    finalDeadlineMs = rampStartMs + durationMs,
                    durationMs = durationMs,
                )
            }
        }
    }
}

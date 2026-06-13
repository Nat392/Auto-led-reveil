package com.example.alarmwatcher

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AlarmMonitorRunnerTest {
    private val context = mockk<Context>()
    private val alarmScheduler = mockk<AlarmSchedulerApi>(relaxed = true)
    private val crashReporter = mockk<CrashReporterApi>(relaxed = true)
    private val runner = AlarmMonitorRunner(alarmScheduler, crashReporter)

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        mockkObject(SunriseZoneConfig)
        mockkObject(SunsetTimesStore)
        mockkObject(NightFadeScheduleStore)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any<Throwable>()) } returns 0
        // Par défaut, aucune zone n'est configurée : le mode nuit est annulé pour les trois zones.
        every { SunriseZoneConfig.bureau } returns unconfiguredZone("Bureau")
        every { SunriseZoneConfig.chambre } returns unconfiguredZone("Chambre")
        every { SunriseZoneConfig.cuisine } returns unconfiguredZone("Cuisine")
        every { SunsetTimesStore.getEveningStartMs(any(), any()) } returns null
        every { NightFadeScheduleStore.getAnchor(any(), any()) } returns null
        every { NightFadeScheduleStore.saveAnchor(any(), any(), any()) } just runs
        every { NightFadeScheduleStore.clearAnchor(any(), any()) } just runs
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        SunriseService.isRunning = false
    }

    @Test
    fun `skips rescan while sunrise service is already running`() {
        SunriseService.isRunning = true

        runner.scanNextAlarmAndSchedule(context)

        confirmVerified(alarmScheduler, crashReporter)
    }

    private fun unconfiguredZone(label: String) =
        SunriseBulbZone(
            label = label,
            macAddress = "",
            sunriseR = 255,
            sunriseG = 230,
            sunriseB = 210,
            sunsetR = 255,
            sunsetG = 71,
            sunsetB = 0,
        )

    private fun configuredZone(
        label: String,
        macAddress: String,
    ) = SunriseBulbZone(
        label = label,
        macAddress = macAddress,
        sunriseR = 255,
        sunriseG = 230,
        sunriseB = 210,
        sunsetR = 255,
        sunsetG = 71,
        sunsetB = 0,
    )

    private fun verifyNightFadeCancelledForUnconfiguredZones() {
        verify(exactly = 1) { alarmScheduler.cancelNightFade(context, SunsetAutomationScheduler.ZONE_BUREAU) }
        verify(exactly = 1) { alarmScheduler.cancelNightFade(context, SunsetAutomationScheduler.ZONE_CHAMBRE) }
        verify(exactly = 1) { alarmScheduler.cancelNightFade(context, SunsetAutomationScheduler.ZONE_CUISINE) }
    }

    private fun verifyChambreNightFadeAnchorSaved(
        eveningStartMs: Long,
        originalStartTimeMs: Long,
        targetEndTimeMs: Long,
        fired: Boolean,
    ) {
        verify(exactly = 1) {
            NightFadeScheduleStore.saveAnchor(
                context,
                SunsetAutomationScheduler.ZONE_CHAMBRE,
                NightFadeScheduleStore.Anchor(eveningStartMs, originalStartTimeMs, targetEndTimeMs, fired),
            )
        }
    }

    private fun setUpNextAlarmClock(triggerTime: Long) {
        val alarmManager = mockk<AlarmManager>()
        val alarmClock = mockk<AlarmManager.AlarmClockInfo>()
        every { alarmClock.showIntent } returns null
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.nextAlarmClock } returns alarmClock
        every { alarmClock.triggerTime } returns triggerTime
    }

    @Test
    fun `returns early when AlarmManager is unavailable`() {
        every { context.getSystemService(AlarmManager::class.java) } returns null

        runner.scanNextAlarmAndSchedule(context)

        confirmVerified(alarmScheduler, crashReporter)
    }

    @Test
    fun `cancels the prewarn alarm when no next alarm is available`() {
        val alarmManager = mockk<AlarmManager>()
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.nextAlarmClock } returns null

        runner.scanNextAlarmAndSchedule(context)

        verify(exactly = 1) { alarmScheduler.cancelPreWarn(context) }
        verifyNightFadeCancelledForUnconfiguredZones()
        confirmVerified(alarmScheduler, crashReporter)
    }

    @Test
    fun `cancels the prewarn alarm when the next alarm is already expired`() {
        val alarmManager = mockk<AlarmManager>()
        val alarmClock = mockk<AlarmManager.AlarmClockInfo>()
        every { alarmClock.showIntent } returns null
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.nextAlarmClock } returns alarmClock
        every { alarmClock.triggerTime } returns 1L

        runner.scanNextAlarmAndSchedule(context)

        verify(exactly = 1) { alarmScheduler.cancelPreWarn(context) }
        verifyNightFadeCancelledForUnconfiguredZones()
        confirmVerified(alarmScheduler, crashReporter)
    }

    @Test
    fun `cancels the prewarn alarm when the computed window is invalid`() {
        val alarmManager = mockk<AlarmManager>()
        val alarmClock = mockk<AlarmManager.AlarmClockInfo>()
        every { alarmClock.showIntent } returns null
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.nextAlarmClock } returns alarmClock

        // Use a future 8 AM time
        val triggerTime =
            java.time.LocalDate.now().plusDays(1)
                .atTime(8, 0).atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        every { alarmClock.triggerTime } returns triggerTime

        mockkObject(AlarmTimingSupport)
        every { AlarmTimingSupport.computePreWarnWindow(any(), any()) } returns null

        runner.scanNextAlarmAndSchedule(context)

        verify(exactly = 1) { alarmScheduler.cancelPreWarn(context) }
        verifyNightFadeCancelledForUnconfiguredZones()
        confirmVerified(alarmScheduler, crashReporter)
    }

    @Test
    fun `cancels the prewarn alarm when alarm is not in the morning window`() {
        val alarmManager = mockk<AlarmManager>()
        val alarmClock = mockk<AlarmManager.AlarmClockInfo>()
        every { alarmClock.showIntent } returns null
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.nextAlarmClock } returns alarmClock

        // 9 PM alarm (21:00) which should be skipped
        val triggerTime =
            java.time.LocalDate.now().plusDays(1)
                .atTime(21, 0).atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        every { alarmClock.triggerTime } returns triggerTime

        runner.scanNextAlarmAndSchedule(context)

        // It should skip and cancel pre-warn without computing window
        verify(exactly = 1) { alarmScheduler.cancelPreWarn(context) }
        verifyNightFadeCancelledForUnconfiguredZones()
        confirmVerified(alarmScheduler, crashReporter)
    }

    @Test
    fun `cancels the prewarn alarm when alarm is from unauthorized package`() {
        val alarmManager = mockk<AlarmManager>()
        val alarmClock = mockk<AlarmManager.AlarmClockInfo>()
        val pendingIntent = mockk<android.app.PendingIntent>()
        every { pendingIntent.creatorPackage } returns "com.google.android.calendar"
        every { alarmClock.showIntent } returns pendingIntent
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.nextAlarmClock } returns alarmClock

        val triggerTime =
            java.time.LocalDate.now().plusDays(1)
                .atTime(8, 0).atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        every { alarmClock.triggerTime } returns triggerTime

        runner.scanNextAlarmAndSchedule(context)

        verify(exactly = 1) { alarmScheduler.cancelPreWarn(context) }
        verifyNightFadeCancelledForUnconfiguredZones()
        confirmVerified(alarmScheduler, crashReporter)
    }

    @Test
    fun `schedules the prewarn alarm when a valid window is computed`() {
        val alarmManager = mockk<AlarmManager>()
        val alarmClock = mockk<AlarmManager.AlarmClockInfo>()

        val pendingIntent = mockk<android.app.PendingIntent>()
        every { pendingIntent.creatorPackage } returns "com.google.android.deskclock"
        every { alarmClock.showIntent } returns pendingIntent

        // 8 AM alarm
        val triggerTime =
            java.time.LocalDate.now().plusDays(1)
                .atTime(8, 0).atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()

        val window = AlarmPreWarnWindow(preWarnAt = 12_000L, scheduleAt = 13_000L, durationMs = 4_200L)

        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.nextAlarmClock } returns alarmClock
        every { alarmClock.triggerTime } returns triggerTime

        mockkObject(AlarmTimingSupport)
        every { AlarmTimingSupport.computePreWarnWindow(any(), any()) } returns window

        runner.scanNextAlarmAndSchedule(context)

        verify(exactly = 1) { alarmScheduler.schedulePreWarn(any(), any(), any(), any()) }
        verifyNightFadeCancelledForUnconfiguredZones()
        confirmVerified(alarmScheduler, crashReporter)
    }

    @Test
    fun `schedules night fade for a configured zone with a known evening start time`() {
        val alarmManager = mockk<AlarmManager>()
        val alarmClock = mockk<AlarmManager.AlarmClockInfo>()
        every { alarmClock.showIntent } returns null
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.nextAlarmClock } returns alarmClock

        // 8 AM alarm dans 2 jours (dans la fenêtre 7h-9h30)
        val triggerTime =
            java.time.LocalDate.now().plusDays(2)
                .atTime(8, 0).atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        every { alarmClock.triggerTime } returns triggerTime

        // Mode soirée chambre déclenché demain à 21h (dans le futur, avant la fin visée du mode nuit)
        val eveningStartMs =
            java.time.LocalDate.now().plusDays(1)
                .atTime(21, 0).atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        every {
            SunriseZoneConfig.chambre
        } returns configuredZone("Chambre", "08:65:F0:E9:1D:0B")
        every {
            SunsetTimesStore.getEveningStartMs(context, SunsetAutomationScheduler.ZONE_CHAMBRE)
        } returns eveningStartMs

        mockkObject(AlarmTimingSupport)
        every { AlarmTimingSupport.computePreWarnWindow(any(), any()) } returns null

        runner.scanNextAlarmAndSchedule(context)

        val targetEndMs = triggerTime - (8 * 60 + 35) * 60 * 1000L
        verify(exactly = 1) {
            alarmScheduler.scheduleNightFade(
                context,
                SunsetAutomationScheduler.ZONE_CHAMBRE,
                eveningStartMs,
                eveningStartMs,
                targetEndMs,
            )
        }
        verify(exactly = 1) { alarmScheduler.cancelNightFade(context, SunsetAutomationScheduler.ZONE_BUREAU) }
        verify(exactly = 1) { alarmScheduler.cancelNightFade(context, SunsetAutomationScheduler.ZONE_CUISINE) }
        verify(exactly = 1) { alarmScheduler.cancelPreWarn(context) }
        confirmVerified(alarmScheduler, crashReporter)
    }

    @Test
    fun `cancels night fade for a configured zone with no evening start time yet`() {
        val alarmManager = mockk<AlarmManager>()
        val alarmClock = mockk<AlarmManager.AlarmClockInfo>()
        every { alarmClock.showIntent } returns null
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.nextAlarmClock } returns alarmClock

        val triggerTime =
            java.time.LocalDate.now().plusDays(1)
                .atTime(8, 0).atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        every { alarmClock.triggerTime } returns triggerTime

        every {
            SunriseZoneConfig.chambre
        } returns configuredZone("Chambre", "08:65:F0:E9:1D:0B")
        // SunsetTimesStore.getEveningStartMs renvoie null par défaut (setUp)

        mockkObject(AlarmTimingSupport)
        every { AlarmTimingSupport.computePreWarnWindow(any(), any()) } returns null

        runner.scanNextAlarmAndSchedule(context)

        verify(exactly = 1) { alarmScheduler.cancelNightFade(context, SunsetAutomationScheduler.ZONE_BUREAU) }
        verify(exactly = 1) { alarmScheduler.cancelNightFade(context, SunsetAutomationScheduler.ZONE_CHAMBRE) }
        verify(exactly = 1) { alarmScheduler.cancelNightFade(context, SunsetAutomationScheduler.ZONE_CUISINE) }
        verify(exactly = 1) { alarmScheduler.cancelPreWarn(context) }
        confirmVerified(alarmScheduler, crashReporter)
    }

    @Test
    fun `starts a compressed fade from now when the evening start already passed without a prior commitment`() {
        // 8 AM alarm dans 2 jours (dans la fenêtre 7h-9h30)
        val triggerTime =
            java.time.LocalDate.now().plusDays(2)
                .atTime(8, 0).atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        setUpNextAlarmClock(triggerTime)

        // Mode soirée chambre déjà déclenché (passé), aucun fondu encore programmé
        val eveningStartMs = System.currentTimeMillis() - 60_000L
        every {
            SunriseZoneConfig.chambre
        } returns configuredZone("Chambre", "08:65:F0:E9:1D:0B")
        every {
            SunsetTimesStore.getEveningStartMs(context, SunsetAutomationScheduler.ZONE_CHAMBRE)
        } returns eveningStartMs

        mockkObject(AlarmTimingSupport)
        every { AlarmTimingSupport.computePreWarnWindow(any(), any()) } returns null

        val alarmTriggerAtMs = slot<Long>()
        val originalStartTimeMs = slot<Long>()
        val targetEndTimeMs = slot<Long>()
        every {
            alarmScheduler.scheduleNightFade(
                context,
                SunsetAutomationScheduler.ZONE_CHAMBRE,
                capture(alarmTriggerAtMs),
                capture(originalStartTimeMs),
                capture(targetEndTimeMs),
            )
        } returns Unit

        val before = System.currentTimeMillis()
        runner.scanNextAlarmAndSchedule(context)
        val after = System.currentTimeMillis()

        val expectedTargetEndMs = triggerTime - (8 * 60 + 35) * 60 * 1000L
        assertEquals(expectedTargetEndMs, targetEndTimeMs.captured)
        // Le fondu démarre "maintenant" (compressé), pas à l'heure du mode soirée déjà passée.
        assertEquals(originalStartTimeMs.captured, alarmTriggerAtMs.captured)
        assertTrue(originalStartTimeMs.captured in before..after)

        verify(exactly = 1) {
            alarmScheduler.scheduleNightFade(
                context,
                SunsetAutomationScheduler.ZONE_CHAMBRE,
                any(),
                any(),
                any(),
            )
        }
        verifyChambreNightFadeAnchorSaved(
            eveningStartMs,
            originalStartTimeMs.captured,
            targetEndTimeMs.captured,
            fired = true,
        )
        verify(exactly = 1) { alarmScheduler.cancelNightFade(context, SunsetAutomationScheduler.ZONE_BUREAU) }
        verify(exactly = 1) { alarmScheduler.cancelNightFade(context, SunsetAutomationScheduler.ZONE_CUISINE) }
        verify(exactly = 1) { alarmScheduler.cancelPreWarn(context) }
        confirmVerified(alarmScheduler, crashReporter)
    }

    @Test
    fun `reuses the committed start time and marks the night fade as fired once the evening start has passed`() {
        val triggerTime =
            java.time.LocalDate.now().plusDays(2)
                .atTime(8, 0).atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        setUpNextAlarmClock(triggerTime)
        val targetEndMs = triggerTime - (8 * 60 + 35) * 60 * 1000L

        // Le fondu avait été programmé à l'heure du mode soirée, mais cette heure est passée
        // sans que l'alarme initiale n'ait (re)déclenché le fondu.
        val eveningStartMs = System.currentTimeMillis() - 60_000L
        every { SunriseZoneConfig.chambre } returns configuredZone("Chambre", "08:65:F0:E9:1D:0B")
        every {
            SunsetTimesStore.getEveningStartMs(context, SunsetAutomationScheduler.ZONE_CHAMBRE)
        } returns eveningStartMs
        every {
            NightFadeScheduleStore.getAnchor(context, SunsetAutomationScheduler.ZONE_CHAMBRE)
        } returns
            NightFadeScheduleStore.Anchor(
                eveningStartMs = eveningStartMs,
                originalStartTimeMs = eveningStartMs,
                targetEndTimeMs = targetEndMs,
                fired = false,
            )

        mockkObject(AlarmTimingSupport)
        every { AlarmTimingSupport.computePreWarnWindow(any(), any()) } returns null

        val alarmTriggerAtMs = slot<Long>()
        every {
            alarmScheduler.scheduleNightFade(
                context,
                SunsetAutomationScheduler.ZONE_CHAMBRE,
                capture(alarmTriggerAtMs),
                eveningStartMs,
                targetEndMs,
            )
        } returns Unit

        runner.scanNextAlarmAndSchedule(context)

        // L'heure de début "logique" reste celle du mode soirée, l'alarme se déclenche maintenant.
        assertTrue(alarmTriggerAtMs.captured >= eveningStartMs)
        verify(exactly = 1) {
            alarmScheduler.scheduleNightFade(
                context,
                SunsetAutomationScheduler.ZONE_CHAMBRE,
                alarmTriggerAtMs.captured,
                eveningStartMs,
                targetEndMs,
            )
        }
        verifyChambreNightFadeAnchorSaved(eveningStartMs, eveningStartMs, targetEndMs, fired = true)
        verify(exactly = 1) { alarmScheduler.cancelNightFade(context, SunsetAutomationScheduler.ZONE_BUREAU) }
        verify(exactly = 1) { alarmScheduler.cancelNightFade(context, SunsetAutomationScheduler.ZONE_CUISINE) }
        verify(exactly = 1) { alarmScheduler.cancelPreWarn(context) }
        confirmVerified(alarmScheduler, crashReporter)
    }

    @Test
    fun `retargets an already fired night fade when the alarm time changes`() {
        val triggerTime =
            java.time.LocalDate.now().plusDays(2)
                .atTime(8, 0).atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        setUpNextAlarmClock(triggerTime)
        val newTargetEndMs = triggerTime - (8 * 60 + 35) * 60 * 1000L

        // Le fondu avait déjà démarré avec une cible calculée à partir d'une ancienne heure d'alarme.
        val eveningStartMs = System.currentTimeMillis() - 60_000L
        val oldTargetEndMs = newTargetEndMs + 30 * 60 * 1000L
        every { SunriseZoneConfig.chambre } returns configuredZone("Chambre", "08:65:F0:E9:1D:0B")
        every {
            SunsetTimesStore.getEveningStartMs(context, SunsetAutomationScheduler.ZONE_CHAMBRE)
        } returns eveningStartMs
        every {
            NightFadeScheduleStore.getAnchor(context, SunsetAutomationScheduler.ZONE_CHAMBRE)
        } returns
            NightFadeScheduleStore.Anchor(
                eveningStartMs = eveningStartMs,
                originalStartTimeMs = eveningStartMs,
                targetEndTimeMs = oldTargetEndMs,
                fired = true,
            )
        mockkObject(AlarmTimingSupport)
        every { AlarmTimingSupport.computePreWarnWindow(any(), any()) } returns null

        val alarmTriggerAtMs = slot<Long>()
        every {
            alarmScheduler.scheduleNightFade(
                context,
                SunsetAutomationScheduler.ZONE_CHAMBRE,
                capture(alarmTriggerAtMs),
                eveningStartMs,
                newTargetEndMs,
            )
        } returns Unit
        runner.scanNextAlarmAndSchedule(context)

        // Le fondu en cours est relancé immédiatement avec la nouvelle cible.
        assertTrue(alarmTriggerAtMs.captured >= eveningStartMs)
        verify(exactly = 1) {
            alarmScheduler.scheduleNightFade(
                context,
                SunsetAutomationScheduler.ZONE_CHAMBRE,
                alarmTriggerAtMs.captured,
                eveningStartMs,
                newTargetEndMs,
            )
        }
        verifyChambreNightFadeAnchorSaved(eveningStartMs, eveningStartMs, newTargetEndMs, fired = true)
        verify(exactly = 1) { alarmScheduler.cancelNightFade(context, SunsetAutomationScheduler.ZONE_BUREAU) }
        verify(exactly = 1) { alarmScheduler.cancelNightFade(context, SunsetAutomationScheduler.ZONE_CUISINE) }
        verify(exactly = 1) { alarmScheduler.cancelPreWarn(context) }
        confirmVerified(alarmScheduler, crashReporter)
    }

    @Test
    fun `skips rescheduling once the night fade has already fired for this evening start`() {
        val alarmManager = mockk<AlarmManager>()
        val alarmClock = mockk<AlarmManager.AlarmClockInfo>()
        every { alarmClock.showIntent } returns null
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.nextAlarmClock } returns alarmClock

        val triggerTime =
            java.time.LocalDate.now().plusDays(2)
                .atTime(8, 0).atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        every { alarmClock.triggerTime } returns triggerTime

        val eveningStartMs = System.currentTimeMillis() - 60_000L
        every {
            SunriseZoneConfig.chambre
        } returns configuredZone("Chambre", "08:65:F0:E9:1D:0B")
        every {
            SunsetTimesStore.getEveningStartMs(context, SunsetAutomationScheduler.ZONE_CHAMBRE)
        } returns eveningStartMs
        every {
            NightFadeScheduleStore.getAnchor(context, SunsetAutomationScheduler.ZONE_CHAMBRE)
        } returns
            NightFadeScheduleStore.Anchor(
                eveningStartMs = eveningStartMs,
                originalStartTimeMs = eveningStartMs,
                targetEndTimeMs = triggerTime - (8 * 60 + 35) * 60 * 1000L,
                fired = true,
            )

        mockkObject(AlarmTimingSupport)
        every { AlarmTimingSupport.computePreWarnWindow(any(), any()) } returns null

        runner.scanNextAlarmAndSchedule(context)

        verify(exactly = 0) {
            alarmScheduler.scheduleNightFade(any(), eq(SunsetAutomationScheduler.ZONE_CHAMBRE), any(), any(), any())
        }
        verify(exactly = 0) {
            NightFadeScheduleStore.saveAnchor(any(), eq(SunsetAutomationScheduler.ZONE_CHAMBRE), any())
        }
        verify(exactly = 0) { alarmScheduler.cancelNightFade(any(), eq(SunsetAutomationScheduler.ZONE_CHAMBRE)) }
        verify(exactly = 1) { alarmScheduler.cancelNightFade(context, SunsetAutomationScheduler.ZONE_BUREAU) }
        verify(exactly = 1) { alarmScheduler.cancelNightFade(context, SunsetAutomationScheduler.ZONE_CUISINE) }
        verify(exactly = 1) { alarmScheduler.cancelPreWarn(context) }
        confirmVerified(alarmScheduler, crashReporter)
    }

    @Test
    fun `reports unexpected failures as non fatal errors`() {
        val alarmManager = mockk<AlarmManager>()
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.nextAlarmClock } throws IllegalStateException("boom")

        runner.scanNextAlarmAndSchedule(context)

        verify(exactly = 1) {
            crashReporter.reportNonFatal(
                context = context,
                throwable = any(),
                source = "AlarmMonitor.scanNextAlarmAndSchedule",
            )
        }
    }

    @Test
    fun `reports security exceptions while reading the alarm clock as non fatal`() {
        every { context.getSystemService(AlarmManager::class.java) } throws SecurityException("denied")

        runner.scanNextAlarmAndSchedule(context)

        verify(exactly = 1) {
            crashReporter.reportNonFatal(
                context = context,
                throwable = any<SecurityException>(),
                source = "AlarmMonitor.scanNextAlarmAndSchedule",
            )
        }
        confirmVerified(alarmScheduler)
    }
}

package com.example.alarmwatcher.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Implémentation en mémoire de [DataStore] utilisée pour tester [AppSettingsStore] sans dépendre
 * du stockage fichier (et de ses particularités de verrouillage selon la plateforme).
 */
private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

class AppSettingsStoreTest {
    @Test
    fun `settingsFlow returns defaults matching the previously hardcoded values when nothing was saved`() =
        runTest {
            val store = AppSettingsStore(InMemoryPreferencesDataStore())

            val settings = store.settingsFlow.first()

            assertEquals(AppSettings(), settings)
            assertEquals(30, settings.preWarnMinutes)
            assertEquals(RgbColor(220, 240, 255), settings.bureauSunriseColor)
            assertEquals(RgbColor(255, 140, 0), settings.bureauSunsetColor)
            assertEquals(RgbColor(255, 230, 210), settings.chambreSunriseColor)
            assertEquals(RgbColor(255, 71, 0), settings.chambreSunsetColor)
            assertEquals(RgbColor(220, 240, 255), settings.cuisineSunriseColor)
            assertEquals(RgbColor(255, 140, 0), settings.cuisineSunsetColor)
            assertEquals(60, settings.bureauSunsetOffsetMinutes)
            assertEquals(30, settings.chambreSunsetOffsetMinutes)
            assertEquals(60, settings.cuisineSunsetOffsetMinutes)
            assertEquals(30, settings.snoozeToleranceMinutes)
            assertEquals(8 * 60 + 35, settings.nightFadeLeadTimeMinutes)
            assertEquals(7 * 60, settings.nightFadeMorningStartMinutes)
            assertEquals(9 * 60 + 30, settings.nightFadeMorningEndMinutes)
            assertEquals(15, settings.daylightIntervalMinutes)
            assertEquals(14, settings.daylightFadeDurationMinutes)
            assertEquals(400.0, settings.chambreSolarThresholdWm2)
            assertEquals(150.0, settings.cuisineSolarThresholdWm2)
            assertEquals(46.6644, settings.latitude)
            assertEquals(5.5619, settings.longitude)
            assertEquals(0, settings.sunsetRefreshHour)
            assertEquals(5, settings.sunsetRefreshMinute)
            assertEquals("", settings.bureauMacAddress)
            assertEquals("", settings.chambreMacAddress)
            assertEquals("", settings.cuisineMacAddress)
            assertEquals(3, settings.sunsetSceneRetryAttempts)
            assertEquals(1.5, settings.sunsetSceneRetryDelaySeconds)
            assertEquals(12, settings.bleConnectTimeoutSeconds)
            assertEquals(5, settings.bleOperationTimeoutSeconds)
        }

    @Test
    fun `update persists a single changed field while preserving the rest`() =
        runTest {
            val store = AppSettingsStore(InMemoryPreferencesDataStore())

            store.update { current -> current.copy(preWarnMinutes = 45) }
            store.update { current -> current.copy(latitude = 48.8566, longitude = 2.3522) }

            val settings = store.settingsFlow.first()

            assertEquals(45, settings.preWarnMinutes)
            assertEquals(48.8566, settings.latitude)
            assertEquals(2.3522, settings.longitude)
            assertEquals(RgbColor(220, 240, 255), settings.bureauSunriseColor)
        }

    @Test
    fun `update persists color changes per zone`() =
        runTest {
            val store = AppSettingsStore(InMemoryPreferencesDataStore())

            store.update { current ->
                current.copy(
                    chambreSunriseColor = RgbColor(10, 20, 30),
                    chambreSunsetColor = RgbColor(40, 50, 60),
                )
            }

            val settings = store.settingsFlow.first()

            assertEquals(RgbColor(10, 20, 30), settings.chambreSunriseColor)
            assertEquals(RgbColor(40, 50, 60), settings.chambreSunsetColor)
            assertEquals(RgbColor(220, 240, 255), settings.bureauSunriseColor)
        }

    @Test
    fun `settings survive being reloaded from a new store instance backed by the same underlying data`() =
        runTest {
            val backingDataStore = InMemoryPreferencesDataStore()
            val firstStore = AppSettingsStore(backingDataStore)
            firstStore.update { current -> current.copy(snoozeToleranceMinutes = 45) }

            val secondStore = AppSettingsStore(backingDataStore)
            val settings = secondStore.settingsFlow.first()

            assertEquals(45, settings.snoozeToleranceMinutes)
        }
}

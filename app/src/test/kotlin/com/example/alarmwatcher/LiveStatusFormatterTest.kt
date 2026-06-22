package com.example.alarmwatcher

import android.content.Context
import android.content.SharedPreferences
import com.example.alarmwatcher.settings.AppSettings
import com.example.alarmwatcher.settings.AppSettingsCache
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LiveStatusFormatterTest {
    private val context = mockk<Context>()
    private val solarPrefs = mockk<SharedPreferences>()
    private val sunsetPrefs = mockk<SharedPreferences>()
    private val chambrePrefs = mockk<SharedPreferences>()
    private val cuisinePrefs = mockk<SharedPreferences>()

    @BeforeEach
    fun setUp() {
        AppSettingsCache.current = AppSettings()
        every { context.applicationContext } returns context
        every { context.getSharedPreferences("solar_conditions", Context.MODE_PRIVATE) } returns solarPrefs
        every { context.getSharedPreferences("sunset_times", Context.MODE_PRIVATE) } returns sunsetPrefs
        every { context.getSharedPreferences("daylight_harvesting_chambre", Context.MODE_PRIVATE) } returns chambrePrefs
        every { context.getSharedPreferences("daylight_harvesting_cuisine", Context.MODE_PRIVATE) } returns cuisinePrefs

        every { solarPrefs.getLong("read_at_ms", -1L) } returns -1L
        every { sunsetPrefs.getLong("sunset_instant_ms", -1L) } returns -1L
        every { sunsetPrefs.getLong("bureau_evening_start_ms", -1L) } returns -1L
        every { sunsetPrefs.getLong("chambre_evening_start_ms", -1L) } returns -1L
        every { sunsetPrefs.getLong("cuisine_evening_start_ms", -1L) } returns -1L
        every { chambrePrefs.getBoolean("active", false) } returns false
        every { chambrePrefs.getInt("current_r", 220) } returns 220
        every { chambrePrefs.getInt("current_g", 240) } returns 240
        every { chambrePrefs.getInt("current_b", 255) } returns 255
        every { cuisinePrefs.getBoolean("active", false) } returns false
        every { cuisinePrefs.getInt("current_r", 220) } returns 220
        every { cuisinePrefs.getInt("current_g", 240) } returns 240
        every { cuisinePrefs.getInt("current_b", 255) } returns 255
    }

    @Test
    fun `build reports no recent reading and no sunset time when nothing was computed yet`() {
        val text = LiveStatusFormatter.build(context)

        assertTrue(text.contains("aucune mesure récente"))
        assertTrue(text.contains("pas encore calculé"))
        assertTrue(text.contains("Chambre bascule en mode soirée à —"))
        assertTrue(text.contains("Chambre : inactif, RGB(220, 240, 255), seuil 400 W/m²"))
        assertTrue(text.contains("Cuisine : inactif, RGB(220, 240, 255), seuil 150 W/m²"))
    }

    @Test
    fun `build formats the solar reading and the active harvesting state per zone`() {
        every { solarPrefs.getLong("read_at_ms", -1L) } returns 1_700_000_000_000L
        every { solarPrefs.getFloat("shortwave_radiation_wm2", 0f) } returns 412.5f
        every { chambrePrefs.getBoolean("active", false) } returns true
        every { chambrePrefs.getInt("current_r", 220) } returns 10
        every { chambrePrefs.getInt("current_g", 240) } returns 20
        every { chambrePrefs.getInt("current_b", 255) } returns 30

        val text = LiveStatusFormatter.build(context)

        assertTrue(text.contains("412 W/m²"))
        assertTrue(text.contains("Chambre : actif, RGB(10, 20, 30), seuil 400 W/m²"))
        assertTrue(text.contains("Cuisine : inactif, RGB(220, 240, 255), seuil 150 W/m²"))
    }
}

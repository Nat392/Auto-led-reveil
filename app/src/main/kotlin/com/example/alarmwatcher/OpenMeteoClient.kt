package com.example.alarmwatcher

import android.util.Log
import com.example.alarmwatcher.settings.AppSettings
import com.example.alarmwatcher.settings.AppSettingsCache
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Source de données pour le Daylight Harvesting : radiation solaire actuelle via Open-Meteo, aux
 * mêmes coordonnées que [SunsetAutomationScheduler] ([AppSettings.latitude]/[AppSettings.longitude]).
 */
internal object OpenMeteoClient {
    private const val TAG = "OpenMeteoClient"

    private fun openMeteoUrl(settings: AppSettings): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${settings.latitude}&longitude=${settings.longitude}" +
            "&current=shortwave_radiation&timezone=auto"

    data class CurrentConditions(
        val shortwaveRadiation: Double,
    )

    internal var openMeteoConnectionFactory: (String) -> HttpURLConnection = { urlString ->
        URL(urlString).openConnection() as HttpURLConnection
    }

    suspend fun fetchCurrentConditions(): CurrentConditions? {
        return try {
            val connection =
                openMeteoConnectionFactory(openMeteoUrl(AppSettingsCache.current)).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "application/json")
                }

            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    Log.w(TAG, "Open-Meteo API returned HTTP $responseCode")
                    return null
                }

                val body =
                    BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                        reader.readText()
                    }
                val shortwaveRadiation =
                    JSONObject(body)
                        .getJSONObject("current")
                        .getDouble("shortwave_radiation")
                CurrentConditions(shortwaveRadiation)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch current conditions from Open-Meteo", e)
            null
        }
    }
}

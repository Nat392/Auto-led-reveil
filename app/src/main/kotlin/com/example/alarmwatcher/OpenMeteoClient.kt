package com.example.alarmwatcher

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Source de données pour le Daylight Harvesting : radiation solaire actuelle via Open-Meteo, aux
 * mêmes coordonnées que [SunsetAutomationScheduler] (lat=46.6644, lng=5.5619).
 */
internal object OpenMeteoClient {
    private const val TAG = "OpenMeteoClient"
    private const val OPEN_METEO_URL =
        "https://api.open-meteo.com/v1/forecast?latitude=46.6644&longitude=5.5619" +
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
                openMeteoConnectionFactory(OPEN_METEO_URL).apply {
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

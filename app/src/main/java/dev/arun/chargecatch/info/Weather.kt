package dev.arun.chargecatch.info

import android.content.Context
import dev.arun.chargecatch.data.SavedLocation
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class Weather(val temperatureC: Int, val condition: String, val fetchedAt: Long) {
    val label: String get() = "$temperatureC° · $condition"
}

/**
 * Current weather from Open-Meteo (free, no API key) for the saved location. Results are cached on
 * the phone for 30 minutes so the screen saver doesn't fetch on every start.
 */
object WeatherRepository {
    private const val CACHE_MS = 30 * 60_000L
    private const val PREFS = "weather_cache"
    private val json = Json { ignoreUnknownKeys = true }

    fun cached(context: Context): Weather? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains("temp")) return null
        return Weather(prefs.getInt("temp", 0), prefs.getString("condition", "") ?: "", prefs.getLong("at", 0L))
    }

    /** Returns cached weather when it is fresh enough, otherwise fetches; null when offline. */
    suspend fun current(context: Context, location: SavedLocation): Weather? = withContext(Dispatchers.IO) {
        val cached = cached(context)
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < CACHE_MS) return@withContext cached
        val fetched = runCatching { fetch(location) }.getOrNull() ?: return@withContext cached
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("temp", fetched.temperatureC)
            .putString("condition", fetched.condition)
            .putLong("at", fetched.fetchedAt)
            .apply()
        fetched
    }

    private fun fetch(location: SavedLocation): Weather? {
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code,is_day",
                location.latitude,
                location.longitude
            )
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            if (connection.responseCode != 200) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val current = json.parseToJsonElement(body).jsonObject["current"] as? JsonObject ?: return null
            val temp = current["temperature_2m"]?.jsonPrimitive?.doubleOrNull ?: return null
            val code = current["weather_code"]?.jsonPrimitive?.intOrNull ?: return null
            val isDay = (current["is_day"]?.jsonPrimitive?.intOrNull ?: 1) == 1
            return Weather(temp.roundToInt(), describe(code, isDay), System.currentTimeMillis())
        } finally {
            connection.disconnect()
        }
    }

    /** WMO weather codes, as Open-Meteo returns them. */
    private fun describe(code: Int, isDay: Boolean): String = when (code) {
        0 -> if (isDay) "Sunny" else "Clear"
        1 -> if (isDay) "Mostly sunny" else "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 66 -> "Rain"
        65, 67 -> "Heavy rain"
        71, 73, 75, 77 -> "Snow"
        80, 81 -> "Showers"
        82 -> "Heavy showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm, hail"
        else -> "—"
    }
}

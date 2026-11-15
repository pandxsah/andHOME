package app.andhome.launcher.features

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class WeatherCity(val name: String, val latitude: Double, val longitude: Double, val timeZone: String = "UTC")
data class WeatherSnapshot(val temperature: Int, val description: String, val tomorrow: String, val updated: Long)

class WeatherRepository(context: Context) {
    private val prefs = context.getSharedPreferences("weather.cache", Context.MODE_PRIVATE)
    suspend fun search(query: String): List<WeatherCity> = withContext(Dispatchers.IO) {
        require(query.trim().length in 2..120) { "Enter at least two characters" }
        val json = request("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query.trim(), "UTF-8")}&count=5&language=en&format=json")
        val results = json.optJSONArray("results") ?: return@withContext emptyList()
        (0 until results.length()).map { i -> results.getJSONObject(i).let {
            WeatherCity(listOf(it.getString("name"), it.optString("admin1"), it.optString("country"))
                .filter(String::isNotBlank).distinct().joinToString(", "), it.getDouble("latitude"), it.getDouble("longitude"), it.optString("timezone", "UTC"))
        } }
    }
    fun cached(latitude: Double, longitude: Double): WeatherSnapshot? = runCatching {
        val j = JSONObject(prefs.getString("$latitude,$longitude", "")!!)
        WeatherSnapshot(j.getInt("temperature"), j.getString("description"), j.getString("tomorrow"), j.getLong("updated"))
    }.getOrNull()
    suspend fun forecast(latitude: Double, longitude: Double, force: Boolean = false): WeatherSnapshot = withContext(Dispatchers.IO) {
        val cached = cached(latitude, longitude)
        if (!force && cached != null && System.currentTimeMillis() - cached.updated < 900_000) return@withContext cached
        val data = request("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=2&timezone=auto")
        val current = data.getJSONObject("current"); val daily = data.getJSONObject("daily")
        val snapshot = WeatherSnapshot(kotlin.math.round(current.getDouble("temperature_2m")).toInt(),
            describe(current.getInt("weather_code")),
            "${describe(daily.getJSONArray("weather_code").getInt(1))} · ${daily.getJSONArray("temperature_2m_max").getDouble(1).toInt()}° / ${daily.getJSONArray("temperature_2m_min").getDouble(1).toInt()}°",
            System.currentTimeMillis())
        prefs.edit().putString("$latitude,$longitude", JSONObject().apply {
            put("temperature", snapshot.temperature); put("description", snapshot.description)
            put("tomorrow", snapshot.tomorrow); put("updated", snapshot.updated)
        }.toString()).apply()
        snapshot
    }
    private fun request(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000; connection.readTimeout = 10_000
        return try {
            require(connection.responseCode in 200..299) { "Weather service unavailable" }
            connection.inputStream.bufferedReader().use { reader ->
                val text = StringBuilder(); val buffer = CharArray(4096)
                while (true) { val n = reader.read(buffer); if (n < 0) break; require(text.length + n <= 1_048_576); text.append(buffer, 0, n) }
                JSONObject(text.toString())
            }
        } finally { connection.disconnect() }
    }
    private fun describe(code: Int) = when (code) {
        0 -> "Clear"; 1, 2 -> "Partly cloudy"; 3 -> "Cloudy"; 45, 48 -> "Fog"
        in 51..67 -> "Rain"; in 71..77 -> "Snow"; in 80..82 -> "Showers"; 85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorms"; else -> "Conditions unavailable"
    }
}

package app.andhome.launcher.features

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Small opt-in adapters matching tvHOME's football and prayer providers. */
class FeedRepository(context: Context) {
    private val prefs = context.getSharedPreferences("feed.cache", Context.MODE_PRIVATE)
    data class Card(val lines: List<String>, val updated: Long, val stale: Boolean = false)
    suspend fun football(league: String): Card = withContext(Dispatchers.IO) {
        require(league in listOf("eng.1", "esp.1", "ger.1", "ita.1", "uefa.champions"))
        val raw = fetch("football:$league", "https://site.api.espn.com/apis/site/v2/sports/soccer/$league/scoreboard", 300_000)
        val events = raw.first.optJSONArray("events")
        val lines = if (events == null || events.length() == 0) listOf("No matches scheduled in this feed") else (0 until minOf(events.length(), 5)).map { i ->
            val event = events.getJSONObject(i)
            val competitors = event.getJSONArray("competitions").getJSONObject(0).getJSONArray("competitors")
            val teams = (0 until competitors.length()).map { n -> competitors.getJSONObject(n).let { "${it.getJSONObject("team").optString("abbreviation", "Team")} ${it.optString("score", "–")}" } }
            "${teams.joinToString("  ·  ")}\n${event.getJSONObject("status").getJSONObject("type").optString("shortDetail", "Upcoming") }"
        }
        Card(lines, raw.second, raw.third)
    }
    suspend fun prayers(latitude: Double, longitude: Double, timeZone: String): Card = withContext(Dispatchers.IO) {
        val date = java.time.LocalDate.now(java.time.ZoneId.of(timeZone)).format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val raw = fetch("prayer:$latitude,$longitude:$date", "https://api.aladhan.com/v1/timings/$date?latitude=$latitude&longitude=$longitude&method=3", 3_600_000)
        val times = raw.first.getJSONObject("data").getJSONObject("timings")
        Card(listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha").map { "$it   ${times.getString(it)}" }, raw.second, raw.third)
    }
    private fun fetch(key: String, url: String, ttl: Long): Triple<JSONObject, Long, Boolean> {
        val old = runCatching { JSONObject(prefs.getString(key, "")!!) }.getOrNull()
        val timestamp = prefs.getLong("$key:time", 0)
        if (old != null && System.currentTimeMillis() - timestamp < ttl) return Triple(old, timestamp, false)
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000; connection.readTimeout = 10_000
            val json = try {
                require(connection.responseCode in 200..299)
                connection.inputStream.use { JSONObject(app.andhome.launcher.readBounded(it, 1_048_576).toString(Charsets.UTF_8)) }
            } finally { connection.disconnect() }
            val now = System.currentTimeMillis(); prefs.edit().putString(key, json.toString()).putLong("$key:time", now).apply()
            Triple(json, now, false)
        } catch (e: Exception) { if (old != null) Triple(old, timestamp, true) else throw e }
    }
}

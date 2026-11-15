package app.andhome.launcher.features

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class ReleaseInfo(val version: String, val notes: String, val url: String)
object UpdateChecker {
    suspend fun latest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val connection = URL("https://api.github.com/repos/pandxsah/andHOME/releases/latest").openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000; connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (connection.responseCode == 404) return@withContext null
            require(connection.responseCode in 200..299)
            val json = connection.inputStream.use { JSONObject(app.andhome.launcher.readBounded(it, 1_048_576).toString(Charsets.UTF_8)) }
            val url = json.getString("html_url")
            require(url.startsWith("https://github.com/pandxsah/andHOME/releases/"))
            ReleaseInfo(json.getString("tag_name").take(80), json.optString("body").take(3000), url)
        } finally { connection.disconnect() }
    }
}

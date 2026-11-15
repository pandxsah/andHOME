package app.andhome.launcher.data

import android.content.Context
import org.json.JSONArray

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("andhome.v1", Context.MODE_PRIVATE)
    fun read(): HomeLayout {
        fun ids(key: String): List<String> = runCatching {
            val array = JSONArray(prefs.getString(key, "[]"))
            (0 until array.length()).map { array.getString(it) }.filter { it.isNotBlank() }.distinct()
        }.getOrDefault(emptyList())
        val dock = ids("dock").take(HomeLayout.DOCK_LIMIT)
        return HomeLayout(
            home = ids("home").filterNot { it in dock }, dock = dock,
            theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
            columns = prefs.getInt("columns", 4).coerceIn(3, 5),
            initialized = prefs.getBoolean("initialized", false),
        )
    }
    // SharedPreferences serializes writes and applies in-memory changes immediately.
    fun write(layout: HomeLayout) {
        prefs.edit().putString("home", JSONArray(layout.home).toString())
            .putString("dock", JSONArray(layout.dock).toString())
            .putString("theme", layout.theme.name).putInt("columns", layout.columns)
            .putBoolean("initialized", layout.initialized).apply()
    }
}

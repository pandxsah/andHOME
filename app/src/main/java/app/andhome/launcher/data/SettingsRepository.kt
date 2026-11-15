package app.andhome.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class HomeProfile(val id: String, val name: String, val layout: HomeLayout)

object LayoutCodec {
    val scenes = listOf("night", "golden", "water", "fog", "dawn")
    fun encode(layout: HomeLayout): JSONObject = JSONObject().apply {
        put("home", JSONArray(layout.home)); put("dock", JSONArray(layout.dock))
        put("theme", layout.theme.name); put("columns", layout.columns); put("initialized", layout.initialized)
        put("look", layout.look.name); put("scene", layout.scene); put("wallpaper", layout.wallpaper)
        put("widgets", JSONArray(layout.widgets)); put("hidden", JSONArray(layout.hidden.toList()))
        put("labels", JSONObject(layout.labels)); put("recent", JSONArray(layout.recent))
        put("dockLimit", layout.dockLimit); put("motion", layout.motion); put("city", layout.city)
        put("latitude", layout.latitude); put("longitude", layout.longitude)
        put("sportsLeague", layout.sportsLeague)
        put("icons", JSONObject(layout.icons))
        put("cityTimeZone", layout.cityTimeZone)
    }
    fun decode(json: JSONObject): HomeLayout {
        fun ids(key: String): List<String> {
            val a = json.optJSONArray(key) ?: return emptyList()
            require(a.length() <= 500) { "Too many entries" }
            return (0 until a.length()).map { a.getString(it).also { value -> require(value.length <= 512) } }
                .filter(String::isNotBlank).distinct()
        }
        val limit = json.optInt("dockLimit", 5).coerceIn(3, 7)
        val dock = ids("dock").take(limit)
        val names = json.optJSONObject("labels") ?: JSONObject(); require(names.length() <= 500)
        val labels = names.keys().asSequence().associateWith { names.getString(it).take(80) }
        val iconsObject = json.optJSONObject("icons") ?: JSONObject(); require(iconsObject.length() <= 500)
        val icons = iconsObject.keys().asSequence().associateWith { iconsObject.getString(it) }
            .filterValues { it.matches(Regex("icon-[a-zA-Z0-9_-]+\\.img")) }
        val lat = json.optDouble("latitude", 0.0); val lon = json.optDouble("longitude", 0.0)
        require(lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0)
        return HomeLayout(home = ids("home").filterNot { it in dock }, dock = dock,
            theme = runCatching { ThemeMode.valueOf(json.optString("theme", "DARK")) }.getOrDefault(ThemeMode.DARK),
            columns = json.optInt("columns", 4).coerceIn(3, 5), initialized = json.optBoolean("initialized", true),
            look = runCatching { HomeLook.valueOf(json.optString("look", "SCENES")) }.getOrDefault(HomeLook.SCENES),
            scene = json.optString("scene", "night").takeIf { it in scenes } ?: "night",
            wallpaper = json.optString("wallpaper").take(200),
            widgets = if (json.has("widgets")) ids("widgets").filter { it in listOf("weather", "music", "battery", "recent", "sports", "prayer") }
                else listOf("weather", "music"), hidden = ids("hidden").toSet(), labels = labels,
            recent = ids("recent").take(8), dockLimit = limit, motion = json.optBoolean("motion"),
            city = json.optString("city").take(120), latitude = lat, longitude = lon,
            sportsLeague = json.optString("sportsLeague", "eng.1").takeIf { it in listOf("eng.1", "esp.1", "ger.1", "ita.1", "uefa.champions") } ?: "eng.1", icons = icons,
            cityTimeZone = runCatching { java.time.ZoneId.of(json.optString("cityTimeZone", "UTC")).id }.getOrDefault("UTC"))
    }
}

class SettingsRepository(context: Context, preferenceName: String = "andhome.v1") {
    private val prefs = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
    private var active = prefs.getString("activeProfile", "default") ?: "default"
    private var profiles = load()
    val activeId get() = active
    fun profiles(): List<HomeProfile> = profiles.toList()
    fun read(): HomeLayout = profiles.first { it.id == active }.layout
    private fun load(): MutableList<HomeProfile> {
        val loaded = runCatching { parseProfiles(JSONObject(prefs.getString("profiles.v2", "")!!), false) }.getOrNull()
        val result = loaded ?: mutableListOf(HomeProfile("default", "Personal", legacy()))
        if (result.none { it.id == active }) active = result.first().id
        return result
    }
    private fun legacy(): HomeLayout = runCatching {
        LayoutCodec.decode(JSONObject().apply {
            put("home", JSONArray(prefs.getString("home", "[]"))); put("dock", JSONArray(prefs.getString("dock", "[]")))
            put("theme", prefs.getString("theme", "DARK")); put("columns", prefs.getInt("columns", 4))
            put("initialized", prefs.getBoolean("initialized", false))
        })
    }.getOrDefault(HomeLayout())
    private fun persist() {
        prefs.edit().putString("profiles.v2", envelope(false).toString()).putString("activeProfile", active).apply()
    }
    fun write(layout: HomeLayout) {
        profiles = profiles.map { if (it.id == active) it.copy(layout = layout) else it }.toMutableList(); persist()
    }
    fun select(id: String) { require(profiles.any { it.id == id }); active = id; persist() }
    fun add(name: String) {
        require(profiles.size < 8) { "Maximum eight profiles" }; require(name.isNotBlank())
        val profile = HomeProfile(UUID.randomUUID().toString(), name.trim().take(40), read().copy(wallpaper = ""))
        profiles.add(profile); active = profile.id; persist()
    }
    fun rename(name: String) {
        require(name.isNotBlank()); profiles = profiles.map { if (it.id == active) it.copy(name = name.trim().take(40)) else it }.toMutableList(); persist()
    }
    fun deleteActive() {
        require(profiles.size > 1) { "Keep at least one profile" }
        profiles.removeAll { it.id == active }; active = profiles.first().id; persist()
    }
    private fun envelope(portable: Boolean) = JSONObject().apply {
        put("schemaVersion", 2); put("activeProfile", active)
        put("profiles", JSONArray(profiles.map { p -> JSONObject().apply {
            put("id", p.id); put("name", p.name)
            put("layout", LayoutCodec.encode(if (portable) p.layout.copy(wallpaper = "", recent = emptyList(), icons = emptyMap()) else p.layout))
        } }))
    }
    fun export(): String = envelope(true).toString(2)
    fun restore(text: String) {
        require(text.length <= 262_144) { "Backup exceeds 256 KB" }
        val document = JSONObject(text); val validated = parseProfiles(document, true)
        val selected = document.optString("activeProfile")
        // Validate the entire import before replacing any existing profile.
        profiles = validated; active = selected.takeIf { id -> validated.any { it.id == id } } ?: validated.first().id
        persist()
    }
    private fun parseProfiles(document: JSONObject, portable: Boolean): MutableList<HomeProfile> {
        require(document.getInt("schemaVersion") == 2) { "Unsupported backup version" }
        val array = document.getJSONArray("profiles"); require(array.length() in 1..8)
        val result = (0 until array.length()).map { index ->
            val json = array.getJSONObject(index)
            val id = json.getString("id"); require(id.matches(Regex("[a-zA-Z0-9_-]{1,64}")))
            val name = json.getString("name"); require(name.isNotBlank() && name.length <= 40)
            val layout = LayoutCodec.decode(json.getJSONObject("layout"))
            require((layout.home + layout.dock + layout.hidden).all { it.contains('/') && it.length <= 512 })
            HomeProfile(id, name, if (portable) layout.copy(wallpaper = "", recent = emptyList(), icons = emptyMap()) else
                layout.copy(wallpaper = layout.wallpaper.takeIf { it.matches(Regex("wallpaper-[a-zA-Z0-9_-]+\\.img")) } ?: ""))
        }
        require(result.map { it.id }.distinct().size == result.size) { "Duplicate profile IDs" }
        return result.toMutableList()
    }
}

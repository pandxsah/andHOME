package app.andhome.launcher.data

enum class ThemeMode { SYSTEM, DARK, LIGHT }
enum class Placement { HOME, DOCK }
enum class HomeLook { SCENES, CLASSIC }

/** Component names are stable across label changes and distinguish multiple app entries. */
data class HomeLayout(
    val home: List<String> = emptyList(),
    val dock: List<String> = emptyList(),
    val theme: ThemeMode = ThemeMode.DARK,
    val columns: Int = 4,
    val initialized: Boolean = false,
    val look: HomeLook = HomeLook.SCENES,
    val scene: String = "night",
    val wallpaper: String = "",
    val widgets: List<String> = listOf("weather", "music"),
    val hidden: Set<String> = emptySet(),
    val labels: Map<String, String> = emptyMap(),
    val recent: List<String> = emptyList(),
    val dockLimit: Int = DOCK_LIMIT,
    val motion: Boolean = false,
    val city: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val sportsLeague: String = "eng.1",
    val icons: Map<String, String> = emptyMap(),
    val cityTimeZone: String = "UTC",
) {
    fun pin(id: String, destination: Placement): HomeLayout {
        if (id.isBlank()) return this
        if (destination == Placement.DOCK && id !in dock && dock.size >= dockLimit) return this
        val clean = remove(id)
        return when (destination) {
            Placement.HOME -> clean.copy(home = clean.home + id)
            Placement.DOCK -> clean.copy(dock = clean.dock + id)
        }
    }
    fun remove(id: String) = copy(home = home - id, dock = dock - id)
    fun hide(id: String) = remove(id).copy(hidden = hidden + id)
    fun withDockLimit(limit: Int): HomeLayout {
        val size = limit.coerceIn(3, 7)
        return copy(dockLimit = size, dock = dock.take(size), home = (home + dock.drop(size)).distinct())
    }
    fun move(id: String, delta: Int): HomeLayout {
        fun shifted(list: List<String>): List<String> {
            val index = list.indexOf(id)
            if (index < 0) return list
            val target = (index + delta).coerceIn(0, list.lastIndex)
            return list.toMutableList().apply { removeAt(index); add(target, id) }
        }
        return copy(home = shifted(home), dock = shifted(dock))
    }
    companion object { const val DOCK_LIMIT = 5 }
}

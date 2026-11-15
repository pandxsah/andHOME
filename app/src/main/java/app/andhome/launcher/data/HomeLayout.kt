package app.andhome.launcher.data

enum class ThemeMode { SYSTEM, DARK, LIGHT }
enum class Placement { HOME, DOCK }

/** Component names are stable across label changes and distinguish multiple app entries. */
data class HomeLayout(
    val home: List<String> = emptyList(),
    val dock: List<String> = emptyList(),
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val columns: Int = 4,
    val initialized: Boolean = false,
) {
    fun pin(id: String, destination: Placement): HomeLayout {
        if (id.isBlank()) return this
        if (destination == Placement.DOCK && id !in dock && dock.size >= DOCK_LIMIT) return this
        val clean = remove(id)
        return when (destination) {
            Placement.HOME -> clean.copy(home = clean.home + id)
            Placement.DOCK -> clean.copy(dock = clean.dock + id)
        }
    }
    fun remove(id: String) = copy(home = home - id, dock = dock - id)
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

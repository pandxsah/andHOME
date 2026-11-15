package app.andhome.launcher

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import app.andhome.launcher.data.*
import org.junit.Assert.*
import org.junit.Test

class SettingsRepositoryTest {
    private fun isolated(test: (SettingsRepository, Context, String) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "andhome.test.${java.util.UUID.randomUUID()}"
        try { test(SettingsRepository(context, name), context, name) }
        finally { context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
    }
    @Test fun layoutAndThemeSurviveRepositoryRecreation() = isolated { repository, context, name ->
        val expected = HomeLayout(listOf("pkg/.Home"), listOf("pkg/.Dock"), ThemeMode.DARK, 5, true,
            scene = "fog", widgets = listOf("music", "weather"), hidden = setOf("pkg/.Hidden"), labels = mapOf("pkg/.Home" to "My app"))
        repository.write(expected)
        assertEquals(expected, SettingsRepository(context, name).read())
    }
    @Test fun profilesKeepSeparateLayoutsAndRestoreRoundTrips() = isolated { repository, _, _ ->
        repository.write(HomeLayout(home = listOf("pkg/.Personal"), initialized = true))
        val first = repository.activeId
        repository.add("Work")
        repository.write(repository.read().copy(home = listOf("pkg/.Work"), scene = "dawn"))
        val backup = repository.export()
        repository.select(first)
        assertEquals(listOf("pkg/.Personal"), repository.read().home)
        repository.restore(backup)
        assertEquals(2, repository.profiles().size)
        assertEquals("dawn", repository.read().scene)
        assertEquals(listOf("pkg/.Work"), repository.read().home)
    }
    @Test fun invalidImportNeverReplacesExistingProfiles() = isolated { repository, _, _ ->
        val before = repository.export()
        for (bad in listOf("not json", "{\"schemaVersion\":999,\"profiles\":[]}", " ".repeat(262_145))) {
            assertTrue(runCatching { repository.restore(bad) }.isFailure)
            assertEquals(before, repository.export())
        }
    }
    @Test fun portableBackupOmitsPhotosAndAppHistory() = isolated { repository, _, _ ->
        repository.write(HomeLayout(wallpaper = "wallpaper-default.img", recent = listOf("pkg/.Private"), initialized = true))
        val backup = repository.export()
        assertFalse(backup.contains("wallpaper-default.img")); assertFalse(backup.contains("pkg/.Private"))
    }
    @Test fun originalMvpPreferencesMigrate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext; val name = "andhome.migration.test"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            prefs.edit().clear().putString("home", "[\"pkg/.Main\"]").putString("theme", "DARK").putBoolean("initialized", true).commit()
            val migrated = SettingsRepository(context, name).read()
            assertEquals(listOf("pkg/.Main"), migrated.home); assertEquals(ThemeMode.DARK, migrated.theme)
            assertEquals(HomeLook.SCENES, migrated.look)
        } finally { prefs.edit().clear().commit() }
    }
}

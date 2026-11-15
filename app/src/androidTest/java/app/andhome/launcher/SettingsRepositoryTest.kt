package app.andhome.launcher

import androidx.test.platform.app.InstrumentationRegistry
import app.andhome.launcher.data.*
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryTest {
    @Test fun layoutAndThemeSurviveRepositoryRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = SettingsRepository(context)
        val original = repository.read()
        try {
            val expected = HomeLayout(listOf("pkg/.Home"), listOf("pkg/.Dock"), ThemeMode.DARK, 5, true)
            repository.write(expected)
            assertEquals(expected, SettingsRepository(context).read())
        } finally { repository.write(original) }
    }
}

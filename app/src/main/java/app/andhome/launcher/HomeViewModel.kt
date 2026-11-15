package app.andhome.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.andhome.launcher.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeState(val layout: HomeLayout = HomeLayout(), val apps: List<InstalledApp> = emptyList(),
    val loading: Boolean = true, val error: String? = null)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsRepository(application)
    private val repository = AppsRepository(application)
    private val mutable = MutableStateFlow(HomeState(layout = settings.read()))
    val state = mutable.asStateFlow()
    private var refreshing = false
    fun refresh() {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) { repository.load() }
                var layout = mutable.value.layout
                if (!layout.initialized && apps.isNotEmpty()) {
                    layout = layout.copy(home = apps.drop(4).take(8).map { it.id },
                        dock = apps.take(4).map { it.id }, initialized = true)
                    settings.write(layout)
                }
                // Keep unavailable pins: disabled or temporarily unavailable apps can return later.
                mutable.value = HomeState(layout, apps, false)
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(loading = false, error = "Could not load apps. Tap Retry.")
            } finally { refreshing = false }
        }
    }
    fun update(change: (HomeLayout) -> HomeLayout) {
        val layout = change(mutable.value.layout)
        settings.write(layout)
        mutable.value = mutable.value.copy(layout = layout)
    }
    fun launch(id: String) = repository.launch(id)
}

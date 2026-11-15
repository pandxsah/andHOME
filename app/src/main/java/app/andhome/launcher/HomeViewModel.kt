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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import app.andhome.launcher.features.*

data class HomeState(val layout: HomeLayout = HomeLayout(), val apps: List<InstalledApp> = emptyList(),
    val loading: Boolean = true, val error: String? = null,
    val profiles: List<HomeProfile> = emptyList(), val activeProfile: String = "default",
    val weather: WeatherSnapshot? = null, val weatherError: String? = null, val weatherLoading: Boolean = false)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsRepository(application)
    private val repository = AppsRepository(application)
    private val weatherRepository = WeatherRepository(application)
    private var weatherJob: Job? = null
    private val mutable = MutableStateFlow(HomeState(layout = settings.read(), profiles = settings.profiles(), activeProfile = settings.activeId))
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
                    layout = layout.copy(home = emptyList(),
                        dock = apps.take(4).map { it.id }, initialized = true)
                    settings.write(layout)
                }
                // Keep unavailable pins: disabled or temporarily unavailable apps can return later.
                mutable.value = mutable.value.copy(layout = layout, apps = apps, loading = false, error = null,
                    profiles = settings.profiles(), activeProfile = settings.activeId)
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(loading = false, error = "Could not load apps. Tap Retry.")
            } finally { refreshing = false }
        }
    }
    fun update(change: (HomeLayout) -> HomeLayout) {
        val layout = change(mutable.value.layout)
        settings.write(layout)
        mutable.value = mutable.value.copy(layout = layout, profiles = settings.profiles())
    }
    fun launch(id: String): Boolean {
        val launched = repository.launch(id)
        if (launched) update { it.copy(recent = (listOf(id) + it.recent).distinct().take(8)) }
        return launched
    }
    fun selectProfile(id: String) { settings.select(id); profileChanged() }
    fun addProfile(name: String): Result<Unit> = runCatching { settings.add(name); profileChanged() }
    fun renameProfile(name: String): Result<Unit> = runCatching { settings.rename(name); profileChanged() }
    fun deleteProfile(): Result<Unit> = runCatching { settings.deleteActive(); profileChanged() }
    private fun profileChanged() {
        weatherJob?.cancel()
        mutable.value = mutable.value.copy(layout = settings.read(), profiles = settings.profiles(), activeProfile = settings.activeId,
            weather = null, weatherError = null, weatherLoading = false)
    }
    fun exportBackup() = settings.export()
    fun restoreBackup(text: String): Result<Unit> = runCatching { settings.restore(text); profileChanged() }
    suspend fun searchCities(query: String) = weatherRepository.search(query)
    fun selectCity(city: WeatherCity) { update { it.copy(city = city.name, latitude = city.latitude, longitude = city.longitude, cityTimeZone = city.timeZone) }; refreshWeather(true) }
    fun refreshWeather(force: Boolean = false) {
        weatherJob?.cancel()
        val layout = mutable.value.layout
        if (layout.city.isBlank() || "weather" !in layout.widgets) return
        weatherJob = viewModelScope.launch {
            mutable.value = mutable.value.copy(weather = weatherRepository.cached(layout.latitude, layout.longitude), weatherLoading = true, weatherError = null)
            try {
                val weather = weatherRepository.forecast(layout.latitude, layout.longitude, force)
                mutable.value = mutable.value.copy(weather = weather, weatherLoading = false)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(weatherLoading = false, weatherError = "Offline or weather unavailable. Showing saved data if available.") }
        }
    }
}

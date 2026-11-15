package app.andhome.launcher.features

import app.andhome.launcher.data.HomeLayout
import kotlinx.coroutines.flow.Flow

/** Future features have explicit state and permission boundaries; none are enabled by this MVP. */
sealed interface Availability {
    data object Ready : Availability
    data class NeedsConsent(val explanation: String) : Availability
    data class Unavailable(val reason: String) : Availability
}
data class WidgetCard(val id: String, val title: String, val body: String, val updatedAtMillis: Long)
interface WidgetProvider {
    val id: String
    val availability: Flow<Availability>
    val cards: Flow<List<WidgetCard>>
    suspend fun refresh()
}
interface MusicProvider : WidgetProvider { suspend fun playPause(); suspend fun next() }
interface WeatherProvider : WidgetProvider { suspend fun setLocation(cityId: String) }
interface SportsProvider : WidgetProvider { suspend fun follow(teamIds: Set<String>) }
data class LauncherProfile(val id: String, val name: String, val layout: HomeLayout)
interface ProfileRepository { val profiles: Flow<List<LauncherProfile>>; suspend fun activate(id: String) }
enum class Control { INTERNET_PANEL, BLUETOOTH_SETTINGS, VOLUME, BRIGHTNESS }
interface ControlCenterGateway { fun availability(control: Control): Availability; fun open(control: Control) }
enum class WallpaperQuality { STATIC, BALANCED, CINEMATIC }
interface WallpaperController { suspend fun apply(uri: String, quality: WallpaperQuality): Availability }
data class BackupEnvelope(val schemaVersion: Int = 1, val profiles: List<LauncherProfile>)
interface BackupService {
    suspend fun export(): BackupEnvelope
    /** Validate version, sizes, component syntax and duplicate IDs before an atomic replacement. */
    suspend fun restore(backup: BackupEnvelope): Result<Unit>
}
data class CompanionDevice(val id: String, val displayName: String)
interface TvHomeCompanion {
    val pairedDevices: Flow<List<CompanionDevice>>
    suspend fun pair(oneTimeCode: String): Result<CompanionDevice>
    suspend fun send(deviceId: String, action: String): Result<Unit>
    suspend fun unpair(deviceId: String)
}

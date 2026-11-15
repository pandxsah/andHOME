package app.andhome.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.andhome.launcher.data.InstalledApp
import kotlin.math.roundToInt

@Composable
internal fun ColumnScope.ControlCenter(state: HomeState, launch: (InstalledApp) -> Unit) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    val activity = context as? Activity
    var brightness by remember { mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: .5f) }
    fun open(action: String) {
        runCatching { context.startActivity(Intent(action)) }.onFailure {
            Toast.makeText(context, "This setting is unavailable on this device", Toast.LENGTH_SHORT).show()
        }
    }
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item { GlassPanel {
            Text("CONNECTIONS", style = MaterialTheme.typography.labelMedium)
            Row { TextButton(onClick = { open(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) }) { Text("Internet") }
                TextButton(onClick = { open(Settings.ACTION_BLUETOOTH_SETTINGS) }) { Text("Bluetooth") } }
            Row { TextButton(onClick = { open(Settings.ACTION_AIRPLANE_MODE_SETTINGS) }) { Text("Airplane mode") }
                TextButton(onClick = { open(Settings.ACTION_SOUND_SETTINGS) }) { Text("Sound") } }
            Text("Protected controls open Android's own settings panels.", style = MaterialTheme.typography.bodySmall)
        } }
        item { GlassPanel {
            Text("Media volume · ${volume.roundToInt()} / $maxVolume")
            Slider(volume, { volume = it; runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, it.roundToInt(), 0) } }, valueRange = 0f..maxVolume.toFloat())
            Text("Launcher brightness · ${(brightness * 100).roundToInt()}%")
            Slider(brightness, { value -> brightness = value; activity?.window?.let { window -> window.attributes = window.attributes.apply { screenBrightness = value } } }, valueRange = .1f..1f)
            TextButton(onClick = { brightness = .5f; activity?.window?.let { it.attributes = it.attributes.apply { screenBrightness = -1f } } }) { Text("Use system brightness") }
            TextButton(onClick = { open(Settings.ACTION_DISPLAY_SETTINGS) }) { Text("Device display settings") }
            Text("The brightness slider affects andHOME only. Device brightness stays under Android's control.", style = MaterialTheme.typography.bodySmall)
        } }
        item { GlassPanel {
            Text("RECENTLY OPENED", style = MaterialTheme.typography.labelMedium)
            val apps = state.layout.recent.filterNot { it in state.layout.hidden }.mapNotNull { id -> state.apps.find { it.id == id } }
            if (apps.isEmpty()) Text("Open apps from andHOME to build your recent list.")
            LazyRow { items(apps, key = { it.id }) { app -> AppTile(app, state.layout.labels[app.id] ?: app.label,
                Modifier.width(68.dp), click = { launch(app) }, hold = {}, iconPath = state.layout.icons[app.id]) } }
        } }
    }
}

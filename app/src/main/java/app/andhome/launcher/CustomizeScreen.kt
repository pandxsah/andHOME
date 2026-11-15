package app.andhome.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.andhome.launcher.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import app.andhome.launcher.features.ReleaseInfo
import app.andhome.launcher.features.UpdateChecker
import kotlinx.coroutines.CancellationException

@Composable
internal fun ColumnScope.CustomizeScreen(model: HomeViewModel, state: HomeState, requestHome: () -> Unit, profiles: () -> Unit, weather: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val layout = state.layout
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var hidden by rememberSaveable { mutableStateOf(false) }
    var wallpaperProfile by rememberSaveable { mutableStateOf("default") }
    var exporting by remember { mutableStateOf("") }
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }
    var updateStatus by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    fun message(text: String) { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val snapshot = exporting
            val result = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(snapshot) } } }
            message(if (result.isSuccess) "Backup saved" else "Could not write backup")
        }
    }
    val loadBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)!!.use { readBounded(it, 262_144).toString(Charsets.UTF_8) } } }
            result.onSuccess { pendingRestore = it }.onFailure { message("Could not read backup: ${it.message}") }
        }
    }
    val chooseWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val id = wallpaperProfile
            val result = withContext(Dispatchers.IO) { runCatching { copyWallpaper(context, uri, id) } }
            result.onSuccess { file ->
                if (state.activeProfile == id) model.update { it.copy(wallpaper = file) }
                else message("Profile changed. Select the wallpaper again.")
            }.onFailure { message("Could not use image: ${it.message}") }
        }
    }
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text("Make yourself at home.", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 12.dp)) }
        item { GlassPanel {
            Text("LOOK & SCENES", style = MaterialTheme.typography.labelMedium)
            Row { HomeLook.entries.forEach { look -> FilterChip(layout.look == look, { model.update { it.copy(look = look) } },
                label = { Text(if (look == HomeLook.SCENES) "Night Scenes" else "Classic") }, modifier = Modifier.padding(end = 8.dp)) } }
            LayoutCodec.scenes.forEach { scene ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(layout.scene == scene && layout.wallpaper.isEmpty(), onClick = { model.update { it.copy(scene = scene, wallpaper = "") } })
                    TextButton(onClick = { model.update { it.copy(scene = scene, wallpaper = "") } }) {
                        Text(when (scene) { "golden" -> "Golden hour"; "water" -> "Waterline"; "fog" -> "Fog"; "dawn" -> "Rainforest dawn"; else -> "Night" })
                    }
                }
            }
            TextButton(onClick = { wallpaperProfile = state.activeProfile; chooseWallpaper.launch(arrayOf("image/*")) }) { Text("Choose your own wallpaper") }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Slow cinematic motion", Modifier.weight(1f))
                Switch(layout.motion, { enabled -> model.update { it.copy(motion = enabled) } }) }
            Text("Motion pauses when the launcher is off screen, in battery saver or when system animations are disabled.", style = MaterialTheme.typography.bodySmall)
        } }
        item { GlassPanel {
            Text("APPEARANCE", style = MaterialTheme.typography.labelMedium)
            ThemeMode.entries.forEach { mode -> Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(layout.theme == mode, onClick = { model.update { it.copy(theme = mode) } })
                TextButton(onClick = { model.update { it.copy(theme = mode) } }) { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) }
            } }
            Text("Home and app drawer columns")
            Row { (3..5).forEach { n -> FilterChip(layout.columns == n, { model.update { it.copy(columns = n) } }, label = { Text("$n") }, modifier = Modifier.padding(end = 10.dp)) } }
            Text("Dock capacity · ${layout.dockLimit} apps")
            Row { (3..7).forEach { n -> FilterChip(layout.dockLimit == n, { model.update { it.withDockLimit(n) } }, label = { Text("$n") }, modifier = Modifier.padding(end = 4.dp)) } }
            Text("Extra pins move to home when shrinking the dock. The dock scrolls horizontally.", style = MaterialTheme.typography.bodySmall)
        } }
        item { GlassPanel {
            Text("HOME WIDGETS", style = MaterialTheme.typography.labelMedium)
            listOf("weather" to "Weather", "music" to "Music player", "battery" to "Battery", "recent" to "Recently opened", "sports" to "Football scores", "prayer" to "Prayer times").forEach { (id, name) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f)); Switch(id in layout.widgets, { enabled -> model.update { it.copy(widgets = if (enabled) it.widgets + id else it.widgets - id) } })
                }
                if (id in layout.widgets) Row {
                    TextButton(onClick = { model.update { it.copy(widgets = moveWidget(it.widgets, id, -1)) } }) { Text("Earlier") }
                    TextButton(onClick = { model.update { it.copy(widgets = moveWidget(it.widgets, id, 1)) } }) { Text("Later") }
                }
            }
            TextButton(onClick = weather) { Text("Weather location") }
            Text("Hold a home card to remove it. Pin any app to home from All apps.", style = MaterialTheme.typography.bodySmall)
        } }
        item { GlassPanel {
            Text("PROFILES & APPS", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = profiles) { Text("Manage profiles") }
            TextButton(onClick = { hidden = true }) { Text("Hidden apps (${layout.hidden.size})") }
            TextButton(onClick = requestHome) { Text("Default home settings") }
        } }
        item { GlassPanel {
            Text("BACKUP & RESTORE", style = MaterialTheme.typography.labelMedium)
            Text("Export all profiles, layouts, names, widgets and scene settings. Custom photos, icon images and recent app history are excluded.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { exporting = model.exportBackup(); saveBackup.launch("andHOME-backup.json") }) { Text("Export backup") }
            TextButton(onClick = { loadBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Restore backup") }
        } }
        item { GlassPanel {
            Text("UPDATES", style = MaterialTheme.typography.labelMedium)
            TextButton(enabled = !checking, onClick = { scope.launch {
                checking = true; updateStatus = ""; release = null
                try { release = UpdateChecker.latest(); updateStatus = release?.let { "Latest published version: ${it.version} · Installed: 0.2.0" } ?: "No published releases yet. You're running development version 0.2.0." }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { updateStatus = "Could not check releases. Try again later." }
                finally { checking = false }
            } }) { Text(if (checking) "Checking…" else "Check GitHub releases") }
            if (updateStatus.isNotBlank()) Text(updateStatus, style = MaterialTheme.typography.bodySmall)
            release?.let { result ->
                Text(result.notes, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.url))) } }) { Text("Open release and download") }
            }
        } }
        item { Text("andHOME 0.2 · tvHOME, made for your phone\nScene assets from TV Home / tvOS Launcher, Apache 2.0.\nWeather by Open-Meteo. No advertising or analytics.", style = MaterialTheme.typography.bodySmall) }
    }
    if (hidden) AlertDialog(onDismissRequest = { hidden = false }, title = { Text("Hidden apps") }, text = {
        LazyColumn { if (layout.hidden.isEmpty()) item { Text("No hidden apps") }
            items(layout.hidden.toList(), key = { it }) { id -> TextButton(onClick = { model.update { it.copy(hidden = it.hidden - id) } }) {
                Text("Show ${state.apps.find { it.id == id }?.label ?: id.substringBefore('/')}")
            } } }
    }, confirmButton = { TextButton(onClick = { hidden = false }) { Text("Done") } })
    pendingRestore?.let { backup -> AlertDialog(onDismissRequest = { pendingRestore = null }, title = { Text("Replace all launcher profiles?") },
        text = { Text("This replaces your current layouts and settings with the selected backup. Export your current setup first if you want to keep it. Invalid backups leave your settings unchanged.") },
        confirmButton = { TextButton(onClick = {
            model.restoreBackup(backup).onSuccess { message("Backup restored") }.onFailure { message("Backup rejected: ${it.message}") }
            pendingRestore = null
        }) { Text("Restore and replace") } }, dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } }) }
}

internal fun moveWidget(widgets: List<String>, id: String, delta: Int): List<String> {
    val index = widgets.indexOf(id); if (index < 0) return widgets
    return widgets.toMutableList().apply { removeAt(index); add((index + delta).coerceIn(0, size), id) }
}

@Composable
internal fun ProfilesDialog(state: HomeState, model: HomeViewModel, close: () -> Unit) {
    var name by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = close, title = { Text("Who's home?") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            state.profiles.forEach { profile -> TextButton(onClick = { model.selectProfile(profile.id); close() }) {
                Text(if (profile.id == state.activeProfile) "${profile.name} · active" else profile.name)
            } }
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Profile name") }, singleLine = true)
            TextButton(enabled = name.isNotBlank() && state.profiles.size < 8, onClick = {
                model.addProfile(name).onSuccess { close() }.onFailure { error = it.message }
            }) { Text("Create from current layout") }
            TextButton(enabled = name.isNotBlank(), onClick = { model.renameProfile(name).onSuccess { close() }.onFailure { error = it.message } }) { Text("Rename active profile") }
            TextButton(enabled = state.profiles.size > 1, onClick = { deleting = true }) { Text("Delete active profile") }
            Text("Profiles change launcher preferences, not your Android user or app data.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = close) { Text("Done") } })
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete this launcher profile?") },
        confirmButton = { TextButton(onClick = { model.deleteProfile(); close() }) { Text("Delete profile") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
}

internal fun readBounded(input: InputStream, limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val n = input.read(buffer); if (n < 0) break; require(output.size() + n <= limit) { "File is too large" }; output.write(buffer, 0, n) }
    return output.toByteArray()
}

private fun copyWallpaper(context: Context, uri: Uri, profileId: String) = copyImage(context, uri, "wallpaper-$profileId")

internal fun copyImage(context: Context, uri: Uri, prefix: String): String {
    require(prefix.matches(Regex("[a-zA-Z0-9_-]{1,80}")))
    val bytes = context.contentResolver.openInputStream(uri)!!.use { readBounded(it, 20 * 1024 * 1024) }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
    val target = if (prefix == "icon") 256 else 2048
    var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > target) sample *= 2
    val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }))
    val name = "$prefix-${java.util.UUID.randomUUID()}.img"; val temp = File(context.filesDir, "$name.tmp")
    try {
        temp.outputStream().use { require(bitmap.compress(if (prefix == "icon") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it)) }
        java.nio.file.Files.move(temp.toPath(), File(context.filesDir, name).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    } finally { bitmap.recycle(); temp.delete() }
    return name
}

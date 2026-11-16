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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
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
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    val profileName = state.profiles.find { it.id == state.activeProfile }?.name ?: "Personal"
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)) {
        item {
            Text("Wallpaper", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.padding(bottom = 22.dp)) {
                items(LayoutCodec.scenes, key = { it }) { scene ->
                    SceneThumb(scene, sceneName(scene), layout.look == HomeLook.SCENES && layout.scene == scene && layout.wallpaper.isEmpty()) {
                        model.update { it.copy(look = HomeLook.SCENES, scene = scene, wallpaper = "") }
                    }
                }
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(width = 84.dp, height = 150.dp).clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(if (layout.wallpaper.isNotEmpty()) 2.5.dp else 0.dp, if (layout.wallpaper.isNotEmpty()) Accent else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickable { wallpaperProfile = state.activeProfile; chooseWallpaper.launch(arrayOf("image/*")) },
                            contentAlignment = Alignment.Center) { Icon(Icons.Default.AddPhotoAlternate, "Choose photo") }
                        Text("Photo", fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        item { SettingsGroup("Appearance") {
            SegmentedRow("Theme") { Segmented(ThemeMode.entries, layout.theme, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { mode -> model.update { it.copy(theme = mode) } } }
            SettingsSwitch("Classic look (no scene)", layout.look == HomeLook.CLASSIC, Icons.Default.Palette, Color(0xFFAF52DE)) { on ->
                model.update { it.copy(look = if (on) HomeLook.CLASSIC else HomeLook.SCENES) } }
            SettingsSwitch("Slow cinematic motion", layout.motion, Icons.Default.Animation, Color(0xFFFF9F0A), divider = false) { on -> model.update { it.copy(motion = on) } }
        } }
        item { SettingsGroup("Home screen", footer = "Extra dock apps move to the home grid when you shrink the dock.") {
            SegmentedRow("App grid columns") { Segmented(listOf(3, 4, 5), layout.columns, { "$it" }) { n -> model.update { it.copy(columns = n) } } }
            SegmentedRow("Dock size") { Segmented(listOf(3, 4, 5, 6, 7), layout.dockLimit, { "$it" }) { n -> model.update { it.withDockLimit(n) } } }
            SettingsRow("Hidden apps", Icons.Default.VisibilityOff, Color(0xFF8E8E93), value = "${layout.hidden.size}", divider = false) { hidden = true }
        } }
        item { SettingsGroup("Widgets", footer = "Swipe sideways on home to see each widget. Use the arrows to reorder.") {
            val all = listOf(
                Triple("weather", "Weather", Icons.Default.WbSunny to Color(0xFF0A84FF)),
                Triple("music", "Now Playing", Icons.Default.MusicNote to Color(0xFFFF2D55)),
                Triple("battery", "Battery", Icons.Default.BatteryFull to Color(0xFF34C759)),
                Triple("recent", "Recently opened", Icons.Default.History to Color(0xFF5E5CE6)),
                Triple("sports", "Football scores", Icons.Default.SportsSoccer to Color(0xFF30B0C7)),
                Triple("prayer", "Prayer times", Icons.Default.Schedule to Color(0xFFFF9F0A)))
            all.forEach { (id, name, look) ->
                val on = id in layout.widgets
                SettingsRow(name, look.first, look.second, onClick = { model.update { it.copy(widgets = if (on) it.widgets - id else it.widgets + id) } }) {
                    if (on) {
                        IconButton(onClick = { model.update { it.copy(widgets = moveWidget(it.widgets, id, -1)) } }, Modifier.size(34.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { model.update { it.copy(widgets = moveWidget(it.widgets, id, 1)) } }, Modifier.size(34.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                    }
                    Switch(on, { enabled -> model.update { it.copy(widgets = if (enabled) it.widgets + id else it.widgets - id) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759), checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent))
                }
            }
            SettingsRow("Weather location", Icons.Default.LocationOn, Color(0xFF0A84FF), value = layout.city.ifBlank { "Not set" }, divider = false, onClick = weather)
        } }
        item { SettingsGroup("General") {
            SettingsRow("Profiles", Icons.Default.Person, Color(0xFF5E5CE6), value = profileName, onClick = profiles)
            SettingsRow("Default home app", Icons.Default.Home, Color(0xFF34C759), divider = false, onClick = requestHome)
        } }
        item { SettingsGroup("Backup", footer = "Profiles, layouts, names, widgets and scenes. Photos and custom icons aren't included.") {
            SettingsRow("Export backup", Icons.Default.Upload, Color(0xFF0A84FF)) { exporting = model.exportBackup(); saveBackup.launch("andHOME-backup.json") }
            SettingsRow("Restore backup", Icons.Default.Download, Color(0xFF0A84FF), divider = false) {
                loadBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        } }
        item { SettingsGroup("About", footer = updateStatus.ifBlank { null }) {
            SettingsRow("Version", Icons.Default.Info, Color(0xFF8E8E93), value = version)
            SettingsRow(if (checking) "Checking…" else "Check for updates", Icons.Default.SystemUpdate, Color(0xFF34C759),
                divider = release != null, onClick = { if (!checking) scope.launch {
                    checking = true; updateStatus = ""; release = null
                    try { release = UpdateChecker.latest(); updateStatus = release?.let { if (it.version.trimStart('v') == version) "You're up to date." else "Version ${it.version} is available." } ?: "No published releases yet." }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { updateStatus = "Could not check for updates. Try again later." }
                    finally { checking = false }
                } })
            release?.let { result -> SettingsRow("Download ${result.version}", Icons.Default.Download, Accent, divider = false) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.url))) } } }
        } }
        item { Text("Scenes from TV Home / tvOS Launcher (Apache 2.0) · Weather by Open-Meteo · No ads or analytics",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .45f), modifier = Modifier.padding(horizontal = 16.dp)) }
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

private fun sceneName(scene: String) = when (scene) { "golden" -> "Golden hour"; "water" -> "Waterline"; "fog" -> "Fog"; "dawn" -> "Dawn"; else -> "Night" }

@Composable
private fun SceneThumb(scene: String, name: String, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, scene) {
        value = withContext(Dispatchers.IO) { runCatching {
            context.assets.open("scenes/scene-$scene.jpg").use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap() }
        }.getOrNull() }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 84.dp, height = 150.dp).clip(RoundedCornerShape(14.dp)).background(Color.DarkGray)
            .border(if (selected) 2.5.dp else 0.dp, if (selected) Accent else Color.Transparent, RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
            thumb?.let { Image(it, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Text(name, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
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

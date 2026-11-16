@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package app.andhome.launcher

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.andhome.launcher.data.*
import app.andhome.launcher.features.MediaBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.time.LocalDateTime

private enum class Page { HOME, APPS, SETTINGS, CONTROLS }

@Composable
fun Launcher(model: HomeViewModel, homeSignal: Int, isDefault: Boolean, requestHome: () -> Unit, systemBars: (Boolean) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var profiles by rememberSaveable { mutableStateOf(false) }
    var weatherSetup by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val layout = state.layout
    val dark = when (layout.theme) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
    val scene = layout.look == HomeLook.SCENES
    val context = LocalContext.current
    var crash by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        crash = withContext(Dispatchers.IO) { runCatching { java.io.File(context.filesDir, "last-crash.txt").takeIf { it.exists() }?.readText()?.take(16_384) }.getOrNull() }
    }
    val glass by produceState<ImageBitmap?>(null, layout.scene, layout.look, layout.wallpaper) {
        value = if (!scene || layout.wallpaper.isNotBlank()) null else withContext(Dispatchers.IO) {
            runCatching { context.assets.open("scenes/scene-${layout.scene}-glass.jpg").use { BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull()
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (isActive) { now = LocalDateTime.now(); MediaBridge.refresh(context); delay(2000) }
    } }
    LaunchedEffect(lifecycle, state.activeProfile, layout.city, layout.widgets) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { model.refreshWeather(); delay(900_000) }
        }
    }
    SideEffect { systemBars(if (scene && page == Page.HOME) true else dark) }
    LaunchedEffect(homeSignal) { page = Page.HOME; editing = false; query = ""; selected = null; profiles = false; weatherSetup = false }
    BackHandler {
        if (selected != null) selected = null else if (editing) editing = false
        else if (page != Page.HOME) { page = Page.HOME; query = "" }
    }
    val colors = if (dark) darkColorScheme(primary = Color(0xFFD2E6F7), background = Color(0xFF111A24), surface = Color(0xFF202D3A))
        else lightColorScheme(primary = Color(0xFF284B63), background = Color(0xFFE8EEF3), surface = Color(0xFFF0F4F7))
    fun label(app: InstalledApp) = layout.labels[app.id] ?: app.label
    fun launch(app: InstalledApp) { if (!model.launch(app.id)) Toast.makeText(context, "App unavailable. Try refreshing All apps.", Toast.LENGTH_SHORT).show() }
    val name = state.profiles.find { it.id == state.activeProfile }?.name ?: "Personal"
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalSceneGlass provides glass, LocalContentColor provides colors.onBackground) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            if (scene) SceneBackdrop(layout) {
                if (page == Page.HOME) Column(Modifier.safeDrawingPadding().padding(top = 58.dp)) { HeroClock(now, name, true) }
            }
            if (scene && page == Page.CONTROLS) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)))
            else if (scene && page != Page.HOME) Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = .94f)))
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 18.dp)) {
                if (page != Page.HOME) Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { page = Page.HOME; query = "" }) { Icon(Icons.Default.ArrowBack, "Back to home") }
                    Text(when (page) { Page.APPS -> "All apps"; Page.SETTINGS -> "Settings"; else -> "Control Center" },
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (page != Page.SETTINGS) IconButton(onClick = { page = Page.SETTINGS }) { Icon(Icons.Default.Settings, "Customize home") }
                }
                when (page) {
                    Page.HOME -> {
                        // The whole home page is one gesture surface: swipe up for apps, down for Control Center,
                        // hold empty space to customize. Taps still reach widgets and apps.
                        Column(Modifier.weight(1f).fillMaxWidth()
                            .pointerInput(Unit) {
                                var total = 0f
                                detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                                    if (total < -60) page = Page.APPS else if (total > 60) page = Page.CONTROLS
                                }) { change, amount -> total += amount; change.consume() }
                            }
                            .combinedClickable(interactionSource = null, indication = null, onClick = {}, onLongClick = { page = Page.SETTINGS })) {
                            Box(Modifier.fillMaxWidth().height(if (scene) 232.dp else 250.dp)) { if (!scene) HeroClock(now, name, false) }
                            if (!isDefault) TextButton(onClick = requestHome, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                Text("Set as default home", color = if (scene) Color.White else colors.primary)
                            }
                            if (editing) GlassPanel(Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Tap an app to move, rename or hide it", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = { editing = false }) { Text("Done") }
                                }
                            }
                            if (layout.widgets.isNotEmpty()) BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                val cardWidth = if (maxWidth >= 600.dp) (maxWidth - 12.dp) / 2 else maxWidth
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(layout.widgets, key = { it }) { widget -> Box(Modifier.width(cardWidth)) {
                                        WidgetCard(widget, state, model, { weatherSetup = true }, { model.update { it.copy(widgets = it.widgets - widget) } }, ::launch)
                                    } }
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            val home = layout.home.filterNot { it in layout.hidden }.mapNotNull { id -> state.apps.find { it.id == id } }
                            home.chunked(layout.columns).forEach { row -> Row(Modifier.padding(bottom = 14.dp)) {
                                row.forEach { app -> AppTile(app, label(app), Modifier.weight(1f), editing,
                                    { if (editing) selected = app.id else launch(app) }, { selected = app.id }, iconPath = layout.icons[app.id]) }
                                repeat(layout.columns - row.size) { Spacer(Modifier.weight(1f)) }
                            } }
                            state.error?.let { error -> GlassPanel(Modifier.fillMaxWidth()) { Text(error); TextButton(onClick = model::refresh) { Text("Retry") } } }
                            Icon(Icons.Default.KeyboardArrowUp, "All apps", Modifier.align(Alignment.CenterHorizontally).size(22.dp),
                                tint = if (scene) Color.White.copy(alpha = .7f) else colors.onBackground.copy(alpha = .5f))
                        }
                        val dock = layout.dock.filterNot { it in layout.hidden }.mapNotNull { id -> state.apps.find { it.id == id } }
                        if (dock.isNotEmpty()) GlassPanel(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                dock.forEach { app -> AppTile(app, "", Modifier.weight(1f), editing,
                                    { if (editing) selected = app.id else launch(app) }, { selected = app.id }, iconPath = layout.icons[app.id]) }
                            }
                        }
                    }
                    Page.APPS -> {
                        val apps = remember(state.apps, layout.hidden, layout.labels, query) {
                            state.apps.filter { it.id !in layout.hidden && (layout.labels[it.id] ?: it.label).contains(query.trim(), true) }
                                .sortedBy { (layout.labels[it.id] ?: it.label).lowercase() }
                        }
                        val keyboard = LocalSoftwareKeyboardController.current
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search apps") },
                            singleLine = true, trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("Clear") } },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = {
                                if (apps.size == 1) { launch(apps.single()); keyboard?.hide() }
                            }))
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("${apps.size} apps · hold to customize", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = model::refresh) { Text("Refresh") } }
                        if (apps.isEmpty() && !state.loading) Text("No apps found", Modifier.padding(20.dp))
                        // Pulling down past the top of the list closes the drawer, like a phone launcher.
                        val pullToClose = remember { object : NestedScrollConnection {
                            var pulled = 0f
                            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                                    pulled += available.y
                                    if (pulled > 120f) { pulled = 0f; keyboard?.hide(); page = Page.HOME; query = "" }
                                } else if (available.y <= 0f) pulled = 0f
                                return Offset.Zero
                            }
                        } }
                        LazyVerticalGrid(GridCells.Fixed(layout.columns), Modifier.weight(1f).nestedScroll(pullToClose)) {
                            items(apps, key = { it.id }) { app -> AppTile(app, label(app), click = { launch(app) }, hold = { selected = app.id }, iconPath = layout.icons[app.id]) }
                        }
                    }
                    Page.SETTINGS -> CustomizeScreen(model, state, requestHome, { profiles = true }, { weatherSetup = true })
                    Page.CONTROLS -> ControlCenter(state, ::launch)
                }
            }
            val app = state.apps.find { it.id == selected }
            if (app != null) AppMenu(app, layout, model) { selected = null }
            if (profiles) ProfilesDialog(state, model) { profiles = false }
            if (weatherSetup) WeatherLocationDialog(model) { weatherSetup = false }
            crash?.let { report -> AlertDialog(onDismissRequest = { java.io.File(context.filesDir, "last-crash.txt").delete(); crash = null },
                title = { Text("Previous launcher crash") }, text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("This report stays on your device. You can copy it when reporting the problem.")
                        Text(report, style = MaterialTheme.typography.bodySmall)
                    }
                }, confirmButton = { TextButton(onClick = { java.io.File(context.filesDir, "last-crash.txt").delete(); crash = null }) { Text("Dismiss") } },
                dismissButton = { TextButton(onClick = {
                    context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("andHOME crash report", report))
                }) { Text("Copy report") } }) }
        }
        }
    }
}

@Composable
private fun AppMenu(app: InstalledApp, layout: HomeLayout, model: HomeViewModel, close: () -> Unit) {
    val context = LocalContext.current
    var name by remember(app.id) { mutableStateOf(layout.labels[app.id] ?: app.label) }
    val scope = rememberCoroutineScope()
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { copyImage(context, uri, "icon") } }
            result.onSuccess { icon -> model.update { it.copy(icons = it.icons + (app.id to icon)) }; close() }
                .onFailure { Toast.makeText(context, "Could not use icon image", Toast.LENGTH_SHORT).show() }
        }
    }
    AlertDialog(onDismissRequest = close, title = { Text(app.label) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("App name") }, singleLine = true)
            TextButton(onClick = { model.update { it.copy(labels = if (name.isBlank()) it.labels - app.id else it.labels + (app.id to name.trim())) }; close() }) { Text("Save name") }
            TextButton(onClick = { iconPicker.launch(arrayOf("image/*")) }) { Text("Choose custom icon") }
            if (app.id in layout.icons) TextButton(onClick = { model.update { it.copy(icons = it.icons - app.id) }; close() }) { Text("Restore original icon") }
            TextButton(onClick = { model.update { it.copy(hidden = it.hidden - app.id).pin(app.id, Placement.HOME) }; close() }) { Text("Pin to home") }
            val full = layout.dock.size >= layout.dockLimit && app.id !in layout.dock
            TextButton(enabled = !full, onClick = { model.update { it.copy(hidden = it.hidden - app.id).pin(app.id, Placement.DOCK) }; close() }) {
                Text(if (full) "Dock full (${layout.dockLimit} apps)" else "Pin to dock")
            }
            if (app.id in layout.home || app.id in layout.dock) {
                Row { TextButton(onClick = { model.update { it.move(app.id, -1) } }) { Text("Move earlier") }
                    TextButton(onClick = { model.update { it.move(app.id, 1) } }) { Text("Move later") } }
                TextButton(onClick = { model.update { it.remove(app.id) }; close() }) { Text("Remove from layout") }
            }
            TextButton(onClick = { model.update { it.hide(app.id) }; close() }) { Text("Hide app") }
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.id.substringBefore('/')}"))) }
                close()
            }) { Text("App info") }
        }
    }, confirmButton = { TextButton(onClick = close) { Text("Done") } })
}

@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package app.andhome.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.andhome.launcher.data.*
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class Page { HOME, APPS, SETTINGS }

@Composable
fun Launcher(model: HomeViewModel, homeSignal: Int, isDefault: Boolean, requestHome: () -> Unit,
    systemBars: (Boolean) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val dark = when (state.layout.theme) {
        ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    SideEffect { systemBars(dark) }
    LaunchedEffect(homeSignal) { page = Page.HOME; editing = false; query = ""; selectedId = null }
    BackHandler {
        if (selectedId != null) selectedId = null
        else if (editing) editing = false
        else if (page != Page.HOME) { page = Page.HOME; query = "" }
    }
    val context = LocalContext.current
    fun launch(app: InstalledApp) {
        if (!model.launch(app.id)) Toast.makeText(context, "This app is unavailable. Refreshing apps.", Toast.LENGTH_SHORT).show()
    }
    val colors = if (dark) darkColorScheme(primary = Color(0xFFB8E4D0), background = Color(0xFF101B24),
        surface = Color(0xFF1C2C37), secondaryContainer = Color(0xFF2C454A))
    else lightColorScheme(primary = Color(0xFF24634E), background = Color(0xFFF0F5EF),
        surface = Color(0xFFE1EBE5), secondaryContainer = Color(0xFFCEE9DC))
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.background, colors.surface)))
                .safeDrawingPadding().imePadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("andHOME", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { page = if (page == Page.SETTINGS) Page.HOME else Page.SETTINGS; selectedId = null }) {
                        Text(if (page == Page.SETTINGS) "Home" else "Settings")
                    }
                }
                when (page) {
                    Page.HOME -> {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                            .combinedClickable(onClick = {}, onLongClick = { editing = true })) {
                            Clock()
                            if (!isDefault) OutlinedButton(onClick = requestHome, modifier = Modifier.fillMaxWidth()) {
                                Text("Set as default home")
                            }
                            OutlinedButton(onClick = { page = Page.APPS }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                Text("Search your apps", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (editing) "MAKE IT YOURS" else "YOUR SPACE", fontSize = 12.sp,
                                    color = colors.primary, modifier = Modifier.weight(1f))
                                TextButton(onClick = { editing = !editing }) { Text(if (editing) "Done" else "Edit") }
                            }
                            if (editing) Text("Tap an app to move or remove it. Add apps from All apps.", style = MaterialTheme.typography.bodySmall)
                            val home = state.layout.home.mapNotNull { id -> state.apps.find { it.id == id } }
                            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (!state.loading && home.isEmpty()) Text("A little room to breathe. Add your favorites from All apps.",
                                modifier = Modifier.padding(vertical = 28.dp))
                            home.chunked(state.layout.columns).forEach { row ->
                                Row(Modifier.fillMaxWidth()) {
                                    row.forEach { app -> AppTile(app, Modifier.weight(1f), editing,
                                        { if (editing) selectedId = app.id else launch(app) }, { selectedId = app.id; editing = true }) }
                                    repeat(state.layout.columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        Surface(shape = RoundedCornerShape(28.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                val dock = state.layout.dock.mapNotNull { id -> state.apps.find { it.id == id } }
                                if (dock.isEmpty()) Text("Your dock · pin apps from All apps", Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall)
                                dock.forEach { app -> AppTile(app, Modifier.weight(1f), editing,
                                    { if (editing) selectedId = app.id else launch(app) }, { selectedId = app.id; editing = true }) }
                            }
                        }
                        TextButton(onClick = { page = Page.APPS }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("All apps  ↑") }
                    }
                    Page.APPS -> {
                        Text("All apps", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(vertical = 16.dp))
                        val filtered = remember(state.apps, query) {
                            state.apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
                        }
                        val keyboard = LocalSoftwareKeyboardController.current
                        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("Search apps") }, trailingIcon = {
                                if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("Clear") }
                            }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (filtered.size == 1) { launch(filtered.single()); keyboard?.hide() }
                            }))
                        Text("${filtered.size} apps · hold an app to pin it", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 12.dp))
                        state.error?.let { Text(it); TextButton(onClick = model::refresh) { Text("Retry") } }
                        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (!state.loading && filtered.isEmpty()) Text("No apps found", Modifier.padding(vertical = 24.dp))
                        LazyVerticalGrid(GridCells.Fixed(state.layout.columns), Modifier.weight(1f)) {
                            items(filtered, key = { it.id }) { app -> AppTile(app, Modifier, false,
                                { launch(app) }, { selectedId = app.id }) }
                        }
                        TextButton(onClick = { page = Page.HOME; query = "" }) { Text("Back to home") }
                    }
                    Page.SETTINGS -> {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            Text("Make yourself at home.", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(vertical = 24.dp))
                            Text("APPEARANCE", color = colors.primary, style = MaterialTheme.typography.labelMedium)
                            ThemeMode.entries.forEach { mode ->
                                Row(Modifier.fillMaxWidth().combinedClickable(role = Role.RadioButton,
                                    onClick = { model.update { it.copy(theme = mode) } }).padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(state.layout.theme == mode, onClick = { model.update { it.copy(theme = mode) } })
                                    Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                                }
                            }
                            Text("Home and app drawer columns", Modifier.padding(top = 16.dp))
                            Row { (3..5).forEach { count ->
                                FilterChip(state.layout.columns == count, { model.update { it.copy(columns = count) } },
                                    label = { Text("$count") }, modifier = Modifier.padding(end = 12.dp))
                            } }
                            HorizontalDivider(Modifier.padding(vertical = 24.dp))
                            Text(if (isDefault) "andHOME is your default launcher" else "Choose andHOME as your default launcher")
                            Button(onClick = requestHome) { Text("Default home settings") }
                            Text("andHOME 0.1\nPrivate by design. Your layout and appearance stay on this device.",
                                modifier = Modifier.padding(vertical = 24.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            val selected = state.apps.find { it.id == selectedId }
            if (selected != null) AlertDialog(onDismissRequest = { selectedId = null },
                title = { Text(selected.label) }, text = {
                    Column {
                        TextButton(onClick = { model.update { it.pin(selected.id, Placement.HOME) }; selectedId = null }) { Text("Pin to home") }
                        val dockFull = state.layout.dock.size >= HomeLayout.DOCK_LIMIT && selected.id !in state.layout.dock
                        TextButton(enabled = !dockFull, onClick = { model.update { it.pin(selected.id, Placement.DOCK) }; selectedId = null }) {
                            Text(if (dockFull) "Dock full (5 apps)" else "Pin to dock")
                        }
                        if (selected.id in state.layout.home || selected.id in state.layout.dock) {
                            Row {
                                TextButton(onClick = { model.update { it.move(selected.id, -1) } }) { Text("Move earlier") }
                                TextButton(onClick = { model.update { it.move(selected.id, 1) } }) { Text("Move later") }
                            }
                            TextButton(onClick = { model.update { it.remove(selected.id) }; selectedId = null }) { Text("Remove from layout") }
                        }
                    }
                }, confirmButton = { TextButton(onClick = { selectedId = null }) { Text("Done") } })
        }
    }
}

@Composable
private fun Clock() {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(1000) } }
    Column(Modifier.padding(top = 28.dp, bottom = 16.dp)) {
        val context = LocalContext.current
        val time = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        Text(now.format(DateTimeFormatter.ofPattern(time)), fontSize = 64.sp, fontWeight = FontWeight.Light)
        Text(now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AppTile(app: InstalledApp, modifier: Modifier, editing: Boolean, click: () -> Unit, hold: () -> Unit) {
    val bitmap = remember(app.icon) {
        Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also {
            app.icon.setBounds(0, 0, 96, 96); app.icon.draw(Canvas(it))
        }.asImageBitmap()
    }
    Column(modifier.clip(RoundedCornerShape(16.dp)).combinedClickable(role = Role.Button,
        onClick = click, onLongClick = hold, onLongClickLabel = "Edit ${app.label}")
        .padding(horizontal = 3.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(48.dp))
        Text(app.label, modifier = Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, fontSize = 12.sp, lineHeight = 15.sp)
        if (editing) Text("•••", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
    }
}

@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package app.andhome.launcher

import android.content.*
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.andhome.launcher.data.InstalledApp
import app.andhome.launcher.features.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.text.DateFormat
import java.util.Date

@Composable
internal fun WidgetCard(id: String, state: HomeState, model: HomeViewModel, city: () -> Unit, remove: () -> Unit, launch: (InstalledApp) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    GlassPanel(Modifier.fillMaxWidth().combinedClickable(onClick = { if (id == "weather") city() }, onLongClick = { menu = true }, onLongClickLabel = "Remove widget")) {
        Row { Text(when (id) { "music" -> "NOW PLAYING"; "battery" -> "BATTERY"; "recent" -> "RECENTLY OPENED"; "sports" -> "FOOTBALL"; "prayer" -> "PRAYER TIMES"; else -> "WEATHER" },
            Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { menu = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(24.dp)) { Text("•••") }
        }
        when (id) {
            "sports", "prayer" -> FeedWidget(id, state, model, city)
            "weather" -> {
                if (state.layout.city.isBlank()) {
                    Text("Your forecast, at a glance", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = city) { Text("Choose a city") }
                } else {
                    Text(state.layout.city, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    state.weather?.let { weather ->
                        Text("${weather.temperature}°  ${weather.description}", fontSize = 27.sp, modifier = Modifier.padding(vertical = 6.dp))
                        Text("Tomorrow: ${weather.tomorrow}", style = MaterialTheme.typography.bodySmall)
                        Text("Updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(weather.updated))}", style = MaterialTheme.typography.labelSmall)
                    }
                    if (state.weatherLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                    state.weatherError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Row { TextButton(onClick = { model.refreshWeather(true) }, enabled = !state.weatherLoading) { Text("Refresh") }
                        TextButton(onClick = city) { Text("Change city") } }
                }
                Text("Weather by Open-Meteo · GeoNames", fontSize = 10.sp)
            }
            "music" -> {
                val media by MediaBridge.state.collectAsStateWithLifecycle()
                val context = LocalContext.current
                if (!media.access) {
                    Text("Bring your music home", style = MaterialTheme.typography.titleMedium)
                    Text("Enable notification access to show media sessions. Notification messages are not read or saved.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } }) { Text("Enable music access") }
                } else {
                    Row(Modifier.padding(top = 6.dp)) {
                        media.artwork?.let { Image(it.asImageBitmap(), null, Modifier.size(58.dp).padding(end = 10.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(media.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            Text(media.artist.ifBlank { "Start playback in your music app" }, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                    Row {
                        TextButton(onClick = { MediaBridge.previous() }, enabled = media.actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L) { Text("Previous") }
                        val supported = media.actions and (PlaybackState.ACTION_PLAY_PAUSE or if (media.playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY) != 0L
                        TextButton(onClick = { MediaBridge.toggle() }, enabled = supported) { Text(if (media.playing) "Pause" else "Play") }
                        TextButton(onClick = { MediaBridge.next() }, enabled = media.actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) { Text("Next") }
                    }
                    state.apps.firstOrNull { it.id.substringBefore('/') == media.packageName }?.let { app ->
                        TextButton(onClick = { launch(app) }) { Text("Open ${app.label}") }
                    }
                }
            }
            "battery" -> {
                val context = LocalContext.current
                var level by remember { mutableStateOf("Checking battery") }
                DisposableEffect(context) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(c: Context?, intent: Intent?) {
                            val charge = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                            level = if (charge < 0 || scale <= 0) "Battery unavailable" else "${charge * 100 / scale}% · ${if (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0) "Charging" else "On battery"}"
                        }
                    }
                    val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                    if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                    else context.registerReceiver(receiver, filter)
                    onDispose { context.unregisterReceiver(receiver) }
                }
                Text(level, style = MaterialTheme.typography.headlineSmall)
            }
            "recent" -> {
                val apps = state.layout.recent.filterNot { it in state.layout.hidden }.mapNotNull { id -> state.apps.find { it.id == id } }
                if (apps.isEmpty()) Text("Apps you open from andHOME will appear here.", style = MaterialTheme.typography.bodySmall)
                LazyRow { items(apps, key = { it.id }) { app ->
                    AppTile(app, state.layout.labels[app.id] ?: app.label, Modifier.width(65.dp), click = { launch(app) }, hold = {}, iconPath = state.layout.icons[app.id])
                } }
            }
        }
    }
    if (menu) AlertDialog(onDismissRequest = { menu = false }, title = { Text("Home widget") }, text = { Text("Remove this card? You can add it again in Customize home.") },
        confirmButton = { TextButton(onClick = { remove(); menu = false }) { Text("Remove widget") } }, dismissButton = { TextButton(onClick = { menu = false }) { Text("Keep") } })
}

@Composable
private fun FeedWidget(id: String, state: HomeState, model: HomeViewModel, city: () -> Unit) {
    val context = LocalContext.current; val repository = remember { FeedRepository(context) }
    var card by remember(id) { mutableStateOf<FeedRepository.Card?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(id, state.layout.city, state.layout.sportsLeague, refresh, lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                card = null; error = null
                if (id == "prayer" && state.layout.city.isBlank()) break
                busy = true
                try { card = if (id == "sports") repository.football(state.layout.sportsLeague) else repository.prayers(state.layout.latitude, state.layout.longitude, state.layout.cityTimeZone) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = "Feed unavailable. Try again later." }
                finally { busy = false }
                kotlinx.coroutines.delay(if (id == "sports") 300_000 else 3_600_000)
            }
        }
    }
    if (id == "prayer" && state.layout.city.isBlank()) TextButton(onClick = city) { Text("Choose a city for prayer times") }
    if (id == "sports") {
        var expanded by remember { mutableStateOf(false) }
        val leagues = listOf("eng.1" to "Premier League", "esp.1" to "La Liga", "ger.1" to "Bundesliga", "ita.1" to "Serie A", "uefa.champions" to "Champions League")
        Box { TextButton(onClick = { expanded = true }) { Text(leagues.find { it.first == state.layout.sportsLeague }?.second ?: "Choose league") }
            DropdownMenu(expanded, { expanded = false }) { leagues.forEach { (key, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { model.update { it.copy(sportsLeague = key) }; expanded = false }) } }
        }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    card?.let { data -> data.lines.forEach { Text(it, Modifier.padding(vertical = 5.dp), style = MaterialTheme.typography.bodyMedium) }
        Text("${if (data.stale) "Saved data · " else ""}Updated ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(data.updated))}", style = MaterialTheme.typography.labelSmall) }
    error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Row { TextButton(onClick = { refresh++ }, enabled = !busy) { Text("Refresh feed") }
        if (id == "prayer") TextButton(onClick = city) { Text("Change city") } }
    Text(if (id == "sports") "ESPN public feed · availability may vary" else "AlAdhan · Muslim World League method · ${state.layout.city}", style = MaterialTheme.typography.labelSmall)
}

@Composable
internal fun WeatherLocationDialog(model: HomeViewModel, close: () -> Unit) {
    var query by remember { mutableStateOf("") }; var results by remember { mutableStateOf(emptyList<WeatherCity>()) }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = close, title = { Text("Weather location") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("City search is sent to Open-Meteo. No GPS permission is needed.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(query, { query = it.take(120) }, label = { Text("City or postal code") }, singleLine = true)
            TextButton(enabled = !busy && query.trim().length >= 2, onClick = { scope.launch {
                busy = true; error = null; results = emptyList()
                try { results = model.searchCities(query); if (results.isEmpty()) error = "No cities found" }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = "Could not search. Check your connection and try again." }
                finally { busy = false }
            } }) { Text(if (busy) "Searching…" else "Search cities") }
            results.forEach { city -> TextButton(onClick = { model.selectCity(city); close() }) { Text(city.name) } }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = close) { Text("Done") } })
}

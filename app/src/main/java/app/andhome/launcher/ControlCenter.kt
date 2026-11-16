package app.andhome.launcher

import android.app.Activity
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.andhome.launcher.data.InstalledApp
import app.andhome.launcher.features.MediaBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

private val TileShape = RoundedCornerShape(26.dp)
private val TileColor = Color.Black.copy(alpha = .38f)

/** iOS-style Control Center: connectivity block, Now Playing, quick toggles, tall sliders and recent apps. */
@Composable
internal fun ColumnScope.ControlCenter(state: HomeState, launch: (InstalledApp) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val notifications = remember { context.getSystemService(NotificationManager::class.java) }
    val camera = remember { context.getSystemService(CameraManager::class.java) }
    val torchId = remember { runCatching { camera.cameraIdList.firstOrNull {
        camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } }.getOrNull() }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume.toFloat()) }
    var brightness by remember { mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: .5f) }
    var wifi by remember { mutableStateOf(false) }
    var bluetooth by remember { mutableStateOf(false) }
    var airplane by remember { mutableStateOf(false) }
    var dnd by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var battery by remember { mutableIntStateOf(0) }
    var charging by remember { mutableStateOf(false) }

    DisposableEffect(torchId) {
        val cb = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, enabled: Boolean) { if (id == torchId) torch = enabled }
        }
        runCatching { camera.registerTorchCallback(cb, null) }
        onDispose { runCatching { camera.unregisterTorchCallback(cb) } }
    }
    // System states can change in Android's panels, so re-read them while Control Center is open.
    LaunchedEffect(Unit) {
        while (isActive) {
            wifi = runCatching { context.getSystemService(WifiManager::class.java).isWifiEnabled }.getOrDefault(false)
            bluetooth = runCatching { context.getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true }.getOrDefault(false)
            airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            dnd = notifications.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume.toFloat()
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { b ->
                val level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0); val scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                battery = level * 100 / scale
                charging = b.getIntExtra(BatteryManager.EXTRA_STATUS, 0).let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
            }
            delay(1500)
        }
    }
    fun open(action: String) {
        runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure {
            Toast.makeText(context, "This setting is unavailable on this device", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Row 1: connectivity block + Now Playing
        Row(Modifier.fillMaxWidth().height(176.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().clip(TileShape).background(TileColor).padding(14.dp)) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RoundToggle(Icons.Default.AirplanemodeActive, airplane, Color(0xFFFF9F0A), "Airplane mode") { open(Settings.ACTION_AIRPLANE_MODE_SETTINGS) }
                        RoundToggle(Icons.Default.SignalCellularAlt, !airplane, Color(0xFF34C759), "Mobile data") { open(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RoundToggle(Icons.Default.Wifi, wifi, Accent, "Wi-Fi") { open(Settings.Panel.ACTION_WIFI) }
                        RoundToggle(Icons.Default.Bluetooth, bluetooth, Accent, "Bluetooth") { open(Settings.ACTION_BLUETOOTH_SETTINGS) }
                    }
                }
            }
            NowPlayingTile(Modifier.weight(1f).fillMaxHeight())
        }
        // Row 2: quick toggles
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            QuickTile(Icons.Default.FlashlightOn, "Torch", torch, Color.White, Modifier.weight(1f), dark = true) {
                if (torchId == null) Toast.makeText(context, "No flashlight on this device", Toast.LENGTH_SHORT).show()
                else runCatching { camera.setTorchMode(torchId, !torch) }
            }
            QuickTile(Icons.Default.DarkMode, "Focus", dnd, Color(0xFF5E5CE6), Modifier.weight(1f)) {
                if (notifications.isNotificationPolicyAccessGranted) {
                    notifications.setInterruptionFilter(if (dnd) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    dnd = !dnd
                } else {
                    Toast.makeText(context, "Allow andHOME to control Do Not Disturb", Toast.LENGTH_LONG).show()
                    open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                }
            }
            QuickTile(if (charging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull, "$battery%", false, Color.White,
                Modifier.weight(1f)) { open(Intent.ACTION_POWER_USAGE_SUMMARY) }
            QuickTile(Icons.Default.Settings, "Settings", false, Color.White, Modifier.weight(1f)) { open(Settings.ACTION_SETTINGS) }
        }
        // Row 3: tall sliders
        Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TallSlider(brightness, Icons.Default.LightMode, "Brightness", Modifier.weight(1f)) { value ->
                brightness = value.coerceIn(.05f, 1f)
                activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = brightness } }
            }
            TallSlider(volume, Icons.AutoMirrored.Filled.VolumeUp, "Volume", Modifier.weight(1f)) { value ->
                volume = value
                runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (value * maxVolume).roundToInt(), 0) }
            }
        }
        Text("Brightness here applies to andHOME only.",
            fontSize = 12.sp, color = Color.White.copy(alpha = .6f), modifier = Modifier.padding(horizontal = 6.dp))
        // Row 4: recent apps
        val apps = state.layout.recent.filterNot { it in state.layout.hidden }.mapNotNull { id -> state.apps.find { it.id == id } }.take(8)
        if (apps.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(TileShape).background(TileColor).padding(14.dp)) {
            Text("Recent", color = Color.White.copy(alpha = .75f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            apps.chunked(4).forEach { row -> Row(Modifier.fillMaxWidth()) {
                row.forEach { app -> AppTile(app, state.layout.labels[app.id] ?: app.label, Modifier.weight(1f),
                    click = { launch(app) }, hold = {}, iconPath = state.layout.icons[app.id]) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            } }
        }
    }
}

@Composable
private fun NowPlayingTile(modifier: Modifier) {
    val context = LocalContext.current
    val media by MediaBridge.state.collectAsStateWithLifecycle()
    Column(modifier.clip(TileShape).background(TileColor).padding(14.dp)) {
        if (!media.access) {
            Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(alpha = .8f))
            Spacer(Modifier.weight(1f))
            Text("Now Playing", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("Tap to allow media access", color = Color.White.copy(alpha = .6f), fontSize = 12.sp,
                modifier = Modifier.clickable { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } })
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val art = media.artwork
            if (art != null) Image(art.asImageBitmap(), null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            else Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = .15f)), Alignment.Center) {
                Icon(Icons.Default.MusicNote, null, tint = Color.White)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(media.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(media.artist, color = Color.White.copy(alpha = .6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { MediaBridge.previous() }) { Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White) }
            IconButton(onClick = { MediaBridge.toggle() }) { Icon(if (media.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", tint = Color.White) }
            IconButton(onClick = { MediaBridge.next() }) { Icon(Icons.Default.SkipNext, "Next", tint = Color.White) }
        }
    }
}

@Composable
private fun QuickTile(icon: ImageVector, label: String, on: Boolean, onColor: Color, modifier: Modifier, dark: Boolean = false, onClick: () -> Unit) {
    Column(modifier.aspectRatio(1f).clip(RoundedCornerShape(22.dp)).background(if (on) onColor else TileColor).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val tint = if (on && dark) Color.Black else Color.White
        Icon(icon, label, Modifier.size(26.dp), tint = tint)
        Text(label, color = tint.copy(alpha = .85f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
    }
}

/** iOS-style tall slider: drag or tap anywhere to set the level. */
@Composable
private fun TallSlider(value: Float, icon: ImageVector, description: String, modifier: Modifier, onChange: (Float) -> Unit) {
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    BoxWithConstraints(modifier.fillMaxHeight().clip(TileShape).background(TileColor)
        .pointerInput(Unit) {
            detectTapGestures { change((1f - it.y / size.height).coerceIn(0f, 1f)) }
        }
        .pointerInput(Unit) {
            detectVerticalDragGestures { c, amount -> c.consume(); change((current - amount / size.height).coerceIn(0f, 1f)) }
        }) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(value.coerceIn(0f, 1f)).background(Color.White.copy(alpha = .9f)))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Icon(icon, description, tint = if (value > .2f) Color(0xFF1C1C1E) else Color.White)
            Text("${(value * 100).roundToInt()}%", fontSize = 12.sp, color = if (value > .12f) Color(0xFF1C1C1E) else Color.White)
        }
    }
}

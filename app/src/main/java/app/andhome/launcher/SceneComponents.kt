@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package app.andhome.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.andhome.launcher.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal val LocalSceneGlass = staticCompositionLocalOf<ImageBitmap?> { null }

@Composable
internal fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val dark = MaterialTheme.colorScheme.background.red < .4f
    val glass = LocalSceneGlass.current
    Surface(modifier, shape = RoundedCornerShape(26.dp),
        color = if (dark) Color(0xFF1C2934).copy(alpha = .60f) else Color.White.copy(alpha = .72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = .30f))) {
        Box {
            glass?.let { Image(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alpha = if (dark) .23f else .12f) }
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
internal fun SceneBackdrop(layout: HomeLayout, modifier: Modifier = Modifier, clock: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val layers by produceState<Pair<Bitmap?, Bitmap?>>(null to null, layout.scene, layout.wallpaper) {
        value = withContext(Dispatchers.IO) {
            fun asset(name: String): Bitmap? = runCatching { context.assets.open("scenes/$name").use { BitmapFactory.decodeStream(it) } }.getOrNull()
            val custom = layout.wallpaper.takeIf { it.matches(Regex("wallpaper-[a-zA-Z0-9_-]+\\.img")) }
            val background = custom?.let { BitmapFactory.decodeFile(File(context.filesDir, it).path) } ?: asset("scene-${layout.scene}.jpg")
            background to if (custom == null && layout.scene != "water") asset("scene-${layout.scene}-fg.webp") else null
        }
    }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val allowedMotion = layout.motion && lifecycle.isAtLeast(Lifecycle.State.STARTED) && !context.getSystemService(PowerManager::class.java).isPowerSaveMode &&
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    Box(modifier.fillMaxSize().background(Color(0xFF111B26))) {
        val movement = if (allowedMotion) {
            val transition = rememberInfiniteTransition(label = "Scene drift")
            val x by transition.animateFloat(-12f, 12f, infiniteRepeatable(tween(25_000, easing = LinearEasing), RepeatMode.Reverse), label = "Pan")
            x
        } else 0f
        layers.first?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().graphicsLayer {
            scaleX = 1.06f; scaleY = 1.06f; translationX = movement
        }, contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .22f), Color.Transparent, Color.Black.copy(alpha = .62f)))))
        clock()
        layers.second?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().graphicsLayer {
            scaleX = 1.06f; scaleY = 1.06f; translationX = movement
        }, contentScale = ContentScale.Crop) }
    }
}

@Composable
internal fun HeroClock(now: LocalDateTime, name: String, scene: Boolean) {
    val context = LocalContext.current
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val color = if (scene) Color.White else MaterialTheme.colorScheme.onBackground
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${when (now.hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }}, $name",
            color = color.copy(alpha = .92f), style = MaterialTheme.typography.bodyMedium)
        Text(now.format(DateTimeFormatter.ofPattern("EEEE d MMMM")), color = color.copy(alpha = .85f), fontSize = 13.sp)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val size = (maxWidth.value * .31f).coerceIn(72f, 130f)
            Text(now.format(DateTimeFormatter.ofPattern(pattern)), Modifier.fillMaxWidth(), color = color.copy(alpha = .88f),
                fontSize = size.sp, fontWeight = FontWeight.ExtraLight, textAlign = TextAlign.Center,
                letterSpacing = (-5).sp, style = MaterialTheme.typography.displayLarge.copy(shadow = Shadow(Color.Black.copy(alpha = .20f), blurRadius = 14f)))
        }
    }
}

@Composable
internal fun AppTile(app: InstalledApp, label: String, modifier: Modifier = Modifier, editing: Boolean = false,
    click: () -> Unit, hold: () -> Unit, iconPath: String? = null) {
    val context = LocalContext.current
    val custom by produceState<ImageBitmap?>(null, iconPath) {
        value = withContext(Dispatchers.IO) {
            iconPath?.takeIf { it.matches(Regex("icon-[a-zA-Z0-9_-]+\\.img")) }?.let { path ->
                runCatching { BitmapFactory.decodeFile(File(context.filesDir, path).path)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    val bitmap = remember(app.icon) {
        Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { app.icon.setBounds(0, 0, 96, 96); app.icon.draw(Canvas(it)) }.asImageBitmap()
    }
    Column(modifier.widthIn(min = 60.dp).clip(RoundedCornerShape(18.dp)).combinedClickable(role = Role.Button,
        onClick = click, onLongClick = hold, onLongClickLabel = "Customize $label").padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Image(custom ?: bitmap, null, Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)))
        Text(label, Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, fontSize = 11.sp, lineHeight = 14.sp)
        if (editing) Text("•••", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
    }
}

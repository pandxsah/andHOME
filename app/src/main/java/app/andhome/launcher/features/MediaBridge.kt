package app.andhome.launcher.features

import android.content.ComponentName
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(val access: Boolean = false, val title: String = "Nothing playing", val artist: String = "",
    val artwork: Bitmap? = null, val playing: Boolean = false, val actions: Long = 0, val packageName: String = "")

/** Only reads media-session metadata; notification bodies are never read or stored. */
object MediaBridge {
    private val mutable = MutableStateFlow(NowPlaying())
    val state = mutable.asStateFlow()
    private var controller: MediaController? = null
    private var manager: MediaSessionManager? = null
    private var app: Application? = null
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) { publish() }
        override fun onPlaybackStateChanged(state: PlaybackState?) { app?.let(::refresh) }
        override fun onSessionDestroyed() { app?.let(::refresh) }
    }
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { app?.let(::refresh) }
    fun connect(context: Context) {
        disconnect()
        app = context.applicationContext as Application
        manager = app?.getSystemService(MediaSessionManager::class.java)
        runCatching { manager?.addOnActiveSessionsChangedListener(sessionsChanged, ComponentName(context, MediaAccessService::class.java)) }
        refresh(context)
    }
    fun disconnect() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsChanged) }
        controller?.unregisterCallback(callback); controller = null; manager = null; app = null
        mutable.value = NowPlaying()
    }
    fun refresh(context: Context) {
        val allowed = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        if (!allowed) { disconnect(); return }
        val controllers = runCatching {
            context.getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(context, MediaAccessService::class.java))
        }.getOrDefault(emptyList())
        val selected = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: controllers.firstOrNull()
        if (controller?.sessionToken != selected?.sessionToken) {
            controller?.unregisterCallback(callback); controller = selected; selected?.registerCallback(callback)
        }
        publish(allowed)
    }
    private fun publish(access: Boolean = true) {
        val active = controller
        val metadata = active?.metadata
        mutable.value = NowPlaying(access, metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Nothing playing",
            metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: active?.packageName.orEmpty(),
            metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            active?.playbackState?.state == PlaybackState.STATE_PLAYING, active?.playbackState?.actions ?: 0L,
            active?.packageName.orEmpty())
    }
    fun toggle() = runCatching {
        if (mutable.value.playing) controller?.transportControls?.pause() else controller?.transportControls?.play()
    }
    fun next() = runCatching { controller?.transportControls?.skipToNext() }
    fun previous() = runCatching { controller?.transportControls?.skipToPrevious() }
}

class MediaAccessService : NotificationListenerService() {
    override fun onListenerConnected() { MediaBridge.connect(this) }
    override fun onListenerDisconnected() { MediaBridge.disconnect() }
    override fun onDestroy() { MediaBridge.disconnect(); super.onDestroy() }
}

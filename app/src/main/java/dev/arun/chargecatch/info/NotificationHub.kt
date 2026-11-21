package dev.arun.chargecatch.info

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(val title: String, val artist: String?, val appLabel: String?)

/** One app with notifications waiting: its package and its small (status bar) icon. */
data class AppIcon(val packageName: String, val icon: Bitmap)

/**
 * Shared state from the notification listener: what's playing and which apps have notifications.
 * The listener and the screen saver run in the same app process, so the screen saver reads these
 * flows directly.
 */
object NotificationHub {
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _appIcons = MutableStateFlow<List<AppIcon>>(emptyList())
    val appIcons: StateFlow<List<AppIcon>> = _appIcons.asStateFlow()

    private val _connected = MutableStateFlow(false)
    /** True while Android has the listener connected and is sending it notifications. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /**
     * Asks Android to reconnect the listener. It can stay disconnected after the app is
     * reinstalled from Android Studio, even though access is still granted.
     */
    fun rebind(context: Context) {
        if (!hasAccess(context) || _connected.value) return
        runCatching {
            NotificationListenerService.requestRebind(ComponentName(context, NowPlayingListener::class.java))
        }
    }

    internal fun publishConnected(value: Boolean) {
        _connected.value = value
    }

    internal fun publishNowPlaying(value: NowPlaying?) {
        _nowPlaying.value = value
    }

    internal fun publishIcons(value: List<AppIcon>) {
        _appIcons.value = value
    }
}

/**
 * Notification access ("Settings > Notifications > Device & app notifications"). Reads only:
 * which apps have notifications (their icons, never the text) and the active media session.
 */
class NowPlayingListener : NotificationListenerService() {
    private var sessionManager: MediaSessionManager? = null
    private val watched = mutableListOf<MediaController>()

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refreshNowPlaying()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshNowPlaying()
        override fun onSessionDestroyed() = refreshSessions()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { refreshSessions() }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationHub.publishConnected(true)
        sessionManager = getSystemService(MediaSessionManager::class.java)
        runCatching {
            sessionManager?.addOnActiveSessionsChangedListener(sessionsListener, ComponentName(this, NowPlayingListener::class.java))
        }
        refreshSessions()
        refreshIcons()
    }

    override fun onListenerDisconnected() {
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        unwatchAll()
        NotificationHub.publishNowPlaying(null)
        NotificationHub.publishIcons(emptyList())
        NotificationHub.publishConnected(false)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refreshIcons()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refreshIcons()

    private fun refreshSessions() {
        unwatchAll()
        val controllers = runCatching {
            sessionManager?.getActiveSessions(ComponentName(this, NowPlayingListener::class.java))
        }.getOrNull().orEmpty()
        controllers.forEach { controller ->
            controller.registerCallback(controllerCallback)
            watched += controller
        }
        refreshNowPlaying()
    }

    private fun unwatchAll() {
        watched.forEach { runCatching { it.unregisterCallback(controllerCallback) } }
        watched.clear()
    }

    private fun refreshNowPlaying() {
        val playing = watched.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val metadata = playing?.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (playing == null || title.isNullOrBlank()) {
            NotificationHub.publishNowPlaying(null)
            return
        }
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val appLabel = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(playing.packageName, 0)).toString()
        }.getOrNull()
        NotificationHub.publishNowPlaying(NowPlaying(title, artist, appLabel))
    }

    private fun refreshIcons() {
        val notifications = runCatching { activeNotifications }.getOrNull().orEmpty()
        val icons = notifications
            .asSequence()
            .filter { it.packageName != packageName }
            .filter { it.isClearable && !it.isOngoing }
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .filter { it.notification.category != Notification.CATEGORY_TRANSPORT }
            .sortedByDescending { it.postTime }
            .distinctBy { it.packageName }
            .take(MAX_ICONS)
            .mapNotNull { sbn -> iconBitmap(sbn.notification.smallIcon, sbn.packageName)?.let { AppIcon(sbn.packageName, it) } }
            .toList()
        NotificationHub.publishIcons(icons)
    }

    private fun iconBitmap(icon: Icon?, packageName: String): Bitmap? = runCatching {
        val drawable = icon?.loadDrawable(this) ?: packageManager.getApplicationIcon(packageName)
        drawable.toBitmap(ICON_PX, ICON_PX)
    }.getOrNull()

    private companion object {
        const val MAX_ICONS = 6
        const val ICON_PX = 64
    }
}

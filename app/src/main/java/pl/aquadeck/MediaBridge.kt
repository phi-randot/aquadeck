package pl.aquadeck

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONObject

/** Follows whichever app is playing and forwards track info / state to the page. */
class MediaBridge(private val ctx: Context, private val js: (String) -> Unit) {
    private val msm = ctx.getSystemService(MediaSessionManager::class.java)
    private val comp = ComponentName(ctx, MediaListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var ctrl: MediaController? = null
    private var sessions: List<MediaController> = emptyList()
    private var pinned: String? = null
    private var started = false

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = pushTrack()
        override fun onPlaybackStateChanged(state: PlaybackState?) = pushState()
        override fun onSessionDestroyed() { refresh() }
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        update(list ?: emptyList())
    }
    private val tick = object : Runnable {
        override fun run() { pushState(); handler.postDelayed(this, 1000) }
    }

    fun hasAccess(): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.contains(ctx.packageName)
    }

    fun start() {
        if (started) { pushAll(); return }
        if (!hasAccess()) {
            js("HeadUnit.notice('DEMO - press OPEN and allow media access')")
            return
        }
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, comp, handler)
            update(msm.getActiveSessions(comp))
            started = true
            handler.removeCallbacks(tick); handler.post(tick)
            if (ctrl == null) js("HeadUnit.notice('DEMO - start music in Tidal or Spotify')")
        } catch (e: SecurityException) {
            started = false
        }
    }

    fun stop() {
        handler.removeCallbacks(tick)
        try { msm.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) {}
        ctrl?.unregisterCallback(callback)
        ctrl = null
        started = false
    }

    private fun refresh() {
        try { update(msm.getActiveSessions(comp)) } catch (_: SecurityException) {}
    }

    private fun update(list: List<MediaController>) {
        sessions = list
        val pick = list.firstOrNull { it.packageName == pinned && pinned != null }
            ?: list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull()
        setController(pick)
    }

    private fun setController(c: MediaController?) {
        if (c != null && c.sessionToken == ctrl?.sessionToken) return
        ctrl?.unregisterCallback(callback)
        ctrl = c
        c?.registerCallback(callback, handler)
        pushAll()
    }

    fun hasSession(): Boolean = ctrl != null

    fun pushAll() { pushTrack(); pushState() }

    private fun label(pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().uppercase()
    } catch (_: Exception) { pkg.substringAfterLast('.').uppercase() }

    private fun position(st: PlaybackState?): Long {
        if (st == null) return 0
        var p = st.position
        if (st.state == PlaybackState.STATE_PLAYING && st.lastPositionUpdateTime > 0) {
            p += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        }
        return p.coerceAtLeast(0)
    }

    private fun duration(c: MediaController): Long =
        c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0

    fun pushTrack() {
        val c = ctrl ?: return
        val m = c.metadata
        val o = JSONObject()
        o.put("title", m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "")
        o.put("artist", m?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "")
        o.put("album", m?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "")
        o.put("source", label(c.packageName))
        o.put("duration", duration(c))
        o.put("position", position(c.playbackState))
        val tn = m?.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER) ?: 0
        if (tn > 0) o.put("track", tn)
        js("HeadUnit.setTrack($o)")
    }

    fun pushState() {
        val c = ctrl ?: return
        val st = c.playbackState
        js("HeadUnit.setPlaying(${st?.state == PlaybackState.STATE_PLAYING})")
        js("HeadUnit.setPosition(${position(st)},${duration(c)})")
    }

    fun prev() { ctrl?.transportControls?.skipToPrevious() }
    fun next() { ctrl?.transportControls?.skipToNext() }
    fun playPause() {
        val c = ctrl ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    /** SRC button: switch between apps that currently have a media session. */
    fun nextSource() {
        if (sessions.size < 2) return
        val i = sessions.indexOfFirst { it.sessionToken == ctrl?.sessionToken }
        val n = sessions[(i + 1).mod(sessions.size)]
        pinned = n.packageName
        setController(n)
    }
}

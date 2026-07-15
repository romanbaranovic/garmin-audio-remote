package sk.baranovic.audioremote

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

object MediaSessionHelper {

    /** Picks which app's media session the Edge widget should control. */
    fun findTargetController(context: Context): MediaController? {
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(context, NotificationListener::class.java)
        val sessions = try {
            msm.getActiveSessions(component)
        } catch (_: SecurityException) {
            // Notification access not granted yet.
            return null
        }

        val playingWhitelisted = sessions.firstOrNull {
            it.packageName in SUPPORTED_PACKAGES && it.playbackState?.state == PlaybackState.STATE_PLAYING
        }
        if (playingWhitelisted != null) return playingWhitelisted

        val whitelisted = sessions.firstOrNull { it.packageName in SUPPORTED_PACKAGES }
        if (whitelisted != null) return whitelisted

        // Fall back to any actively playing session so the widget isn't
        // useless with other media apps, even if unofficially supported.
        return sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
    }
}

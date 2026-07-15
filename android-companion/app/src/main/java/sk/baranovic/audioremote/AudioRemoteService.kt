package sk.baranovic.audioremote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException

/**
 * Long-running foreground service that:
 *  - keeps a Connect IQ connection to the Edge 830 widget alive
 *  - polls the active whitelisted media session every few seconds and
 *    pushes title/position/duration/playing state to the watch
 *  - executes play/pause/seek commands received from the watch
 *
 * Polling (instead of wiring every MediaController/MediaSessionManager
 * callback) keeps this simple and robust — worst case the watch's progress
 * bar is a few seconds stale if you pause from the phone's own UI.
 */
class AudioRemoteService : Service() {

    private val connectIQ: ConnectIQ by lazy {
        ConnectIQ.getInstance(applicationContext, ConnectIQ.IQConnectType.WIRELESS)
    }
    private val watchApp = IQApp(WATCH_APP_ID)
    private var device: IQDevice? = null

    private val handler = Handler(Looper.getMainLooper())
    private val pollIntervalMs = 3000L
    private var lastPushedSignature: String? = null

    private val pollRunnable = object : Runnable {
        override fun run() {
            pushNowPlaying()
            handler.postDelayed(this, pollIntervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification())
        connectIQ.initialize(applicationContext, true, object : ConnectIQ.ConnectIQListener {
            override fun onSdkReady() {
                pickDevice()
            }
            override fun onInitializeError(errStatus: ConnectIQ.IQSdkErrorStatus) {
                Log.e(TAG, "Connect IQ init error: $errStatus")
            }
            override fun onSdkShutDown() {}
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollRunnable)
        try {
            device?.let { connectIQ.unregisterForApplicationEvents(it, watchApp) }
            connectIQ.shutdown(applicationContext)
        } catch (_: InvalidStateException) {
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun pickDevice() {
        try {
            val devices = connectIQ.knownDevices ?: emptyList()
            device = devices.firstOrNull()
            device?.let { d ->
                connectIQ.registerForAppEvents(d, watchApp) { _, _, message, _ ->
                    val cmd = message?.firstOrNull() as? String
                    if (cmd != null) handleCommand(cmd)
                }
            }
            handler.removeCallbacks(pollRunnable)
            handler.post(pollRunnable)
        } catch (e: InvalidStateException) {
            Log.e(TAG, "pickDevice: invalid state", e)
        } catch (e: ServiceUnavailableException) {
            Log.e(TAG, "pickDevice: Garmin Connect Mobile not available", e)
        }
    }

    private fun handleCommand(cmd: String) {
        val controller = MediaSessionHelper.findTargetController(applicationContext)
        when (cmd) {
            "play_pause" -> {
                val playing = controller?.playbackState?.state == PlaybackState.STATE_PLAYING
                if (playing) controller?.transportControls?.pause() else controller?.transportControls?.play()
            }
            "seek_back" -> seekBy(controller, -10_000)
            "seek_fwd" -> seekBy(controller, 10_000)
            "next_queue" -> skipToNextQueueItem(controller)
            "refresh" -> {} // falls through to pushNowPlaying() below
        }
        // Push immediately so the watch doesn't wait for the next poll tick,
        // then again once the player has had time to actually apply the
        // command — many apps update their MediaSession state asynchronously,
        // so an immediate read right after issuing play/pause/skip can still
        // report the pre-command state.
        handler.post { pushNowPlaying(force = true) }
        handler.postDelayed({ pushNowPlaying(force = true) }, 400)
    }

    private fun skipToNextQueueItem(controller: MediaController?) {
        if (controller == null) return
        val actions = controller.playbackState?.actions ?: 0L
        if (actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L) {
            controller.transportControls.skipToNext()
            return
        }
        // Some players (e.g. Pocket Casts) don't implement ACTION_SKIP_TO_NEXT
        // and only expose ACTION_SKIP_TO_QUEUE_ITEM with an explicit "Up Next"
        // queue that doesn't include the currently playing item — so the next
        // item to play is simply the first entry in that queue.
        val nextItem = controller.queue?.firstOrNull()
        if (nextItem != null) {
            controller.transportControls.skipToQueueItem(nextItem.queueId)
        }
    }

    private fun seekBy(controller: MediaController?, deltaMs: Long) {
        val state = controller?.playbackState ?: return

        // Some players (e.g. Smart AudioBook Player) don't implement
        // ACTION_SEEK_TO at all and only expose fixed-length skips as
        // custom actions ("Rewind" / "Fast forward"). Prefer those when
        // present; fall back to absolute seekTo for players that support it.
        val customName = if (deltaMs < 0) "rewind" else "forward"
        val customAction = state.customActions?.firstOrNull {
            it.name?.toString()?.lowercase()?.contains(customName) == true
        }
        if (customAction != null) {
            controller.transportControls.sendCustomAction(customAction.action, customAction.extras)
            return
        }

        val pos = state.position
        val duration = controller.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: Long.MAX_VALUE
        val target = (pos + deltaMs).coerceIn(0, duration)
        controller.transportControls.seekTo(target)
    }

    private fun pushNowPlaying(force: Boolean = false) {
        val d = device ?: return
        val controller = MediaSessionHelper.findTargetController(applicationContext)

        val title: String
        val duration: Long
        val position: Long
        val playing: Boolean

        if (controller == null) {
            title = "Nespárované"
            duration = 0
            position = 0
            playing = false
        } else {
            title = controller.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "?"
            duration = controller.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0
            position = controller.playbackState?.position ?: 0
            playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        }

        val signature = "$title|$duration|$position|$playing"
        if (!force && signature == lastPushedSignature) return
        lastPushedSignature = signature

        val payload = mapOf(
            "title" to title,
            "duration" to duration.toInt(),
            "position" to position.toInt(),
            "playing" to playing
        )
        try {
            connectIQ.sendMessage(d, watchApp, payload) { _, _, _ -> }
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage failed", e)
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Audio Remote",
                NotificationManager.IMPORTANCE_MIN
            )
        )
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Audio Remote beží")
            .setContentText("Ovládanie audiokníh z Edge 830")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    companion object {
        private const val TAG = "AudioRemoteService"
    }
}

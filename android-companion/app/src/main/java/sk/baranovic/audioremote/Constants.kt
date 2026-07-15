package sk.baranovic.audioremote

// Must match the widget's manifest.xml <iq:application id="..."> UUID,
// but WITHOUT the dashes — that's the format Connect IQ's IQApp expects.
// Widget manifest id: 7ce36164-617d-483b-8c29-c1eff82bc95c
const val WATCH_APP_ID = "7ce36164617d483b8c29c1eff82bc95c"

const val NOTIFICATION_CHANNEL_ID = "audio_remote_service"
const val FOREGROUND_NOTIFICATION_ID = 1

// Packages we actively recognize; MediaSessionHelper falls back to any
// other actively-playing session if none of these are running.
val SUPPORTED_PACKAGES = listOf(
    "ak.alizandro.smartaudiobookplayer", // Smart AudioBook Player
    "au.com.shiftyjelly.pocketcasts"     // Pocket Casts
)

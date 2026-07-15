package sk.baranovic.audioremote

import android.service.notification.NotificationListenerService

/**
 * We don't care about notifications themselves — this service's only job is
 * to exist so its ComponentName can be passed to
 * MediaSessionManager.getActiveSessions(), which requires notification
 * access to be granted (Settings > Apps > Special access > Notification access).
 */
class NotificationListener : NotificationListenerService()

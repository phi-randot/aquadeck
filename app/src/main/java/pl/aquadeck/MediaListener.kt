package pl.aquadeck

import android.service.notification.NotificationListenerService

/** Exists only so Android lets AquaDeck read the active media sessions (Tidal, Spotify…). */
class MediaListener : NotificationListenerService()

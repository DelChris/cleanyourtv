package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Lets Home see which app is playing music (MediaSessionManager.getActiveSessions needs an approved
 * notification listener). It reads no notification. Enable with
 * {@code cmd notification allow_listener com.gathin.tvhome/.NowPlayingListener}.
 */
public class NowPlayingListener extends NotificationListenerService {
}

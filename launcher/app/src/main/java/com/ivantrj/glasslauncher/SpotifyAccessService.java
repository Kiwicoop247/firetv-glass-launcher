package com.ivantrj.glasslauncher;

import android.content.Intent;
import android.service.notification.NotificationListenerService;

/** Access token for the media-session API. Notification contents are never read. */
public class SpotifyAccessService extends NotificationListenerService {
    static final String ACCESS_CHANGED = "com.ivantrj.glasslauncher.SPOTIFY_ACCESS_CHANGED";
    @Override public void onListenerConnected() { announce(); }
    @Override public void onListenerDisconnected() { announce(); }
    private void announce() {
        sendBroadcast(new Intent(ACCESS_CHANGED).setPackage(getPackageName()));
    }
}

package com.ivantrj.glasslauncher;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;

/** Event-driven, foreground-only Spotify media observer. No notification parsing or polling. */
final class SpotifyPlayback {
    static final String PACKAGE = "com.spotify.tv.android";
    interface Listener { void changed(Track track, PendingIntent openSpotify); }
    static final class Track {
        final String title, artist, album;
        final Bitmap artwork;
        final long duration;
        final PlaybackState state;
        Track(MediaMetadata metadata, PlaybackState state) {
            String value = text(metadata, MediaMetadata.METADATA_KEY_TITLE);
            title = value.isEmpty() ? "Spotify" : value;
            artist = text(metadata, MediaMetadata.METADATA_KEY_ARTIST);
            album = text(metadata, MediaMetadata.METADATA_KEY_ALBUM);
            Bitmap art = metadata == null ? null : metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null && metadata != null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (art == null && metadata != null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            artwork = art;
            duration = metadata == null ? 0 : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
            this.state = state;
        }
        private static String text(MediaMetadata metadata, String key) {
            String value = metadata == null ? null : metadata.getString(key);
            return value == null ? "" : value.trim();
        }
    }
    private final MediaSessionManager manager;
    private final ComponentName access;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<MediaController> controllers = new ArrayList<>();
    private boolean started;
    private boolean registered;
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::bind;
    private final MediaController.Callback updates = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { publish(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { publish(); }
        @Override public void onSessionDestroyed() { refresh(); }
    };

    SpotifyPlayback(Context context, Listener listener) {
        this.listener = listener;
        manager = context.getSystemService(MediaSessionManager.class);
        access = new ComponentName(context, SpotifyAccessService.class);
    }

    void start() { started = true; refresh(); }
    void refresh() {
        if (!started || manager == null) return;
        try {
            if (!registered) {
                manager.addOnActiveSessionsChangedListener(sessions, access, handler);
                registered = true;
            }
            bind(manager.getActiveSessions(access));
        } catch (SecurityException denied) {
            clear();
            listener.changed(null, null);
        }
    }
    void stop() {
        started = false;
        if (registered) manager.removeOnActiveSessionsChangedListener(sessions);
        registered = false;
        clear();
    }
    private void clear() {
        for (MediaController controller : controllers) controller.unregisterCallback(updates);
        controllers.clear();
    }
    private void bind(List<MediaController> active) {
        if (!started) return;
        clear();
        if (active != null) for (MediaController controller : active) {
            if (!PACKAGE.equals(controller.getPackageName())) continue;
            controllers.add(controller);
            controller.registerCallback(updates, handler);
        }
        publish();
    }
    private void publish() {
        if (!started) return;
        for (MediaController controller : controllers) {
            PlaybackState state = controller.getPlaybackState();
            if (state == null || state.getState() != PlaybackState.STATE_PLAYING) continue;
            MediaMetadata metadata = controller.getMetadata();
            listener.changed(new Track(metadata, state), controller.getSessionActivity());
            return;
        }
        listener.changed(null, null);
    }
}

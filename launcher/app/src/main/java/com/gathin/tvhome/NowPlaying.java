package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * The track an allowed app (now_playing_packages, e.g. Spotify) is playing, pushed to a listener on every change:
 * no polling, only the media session's own callbacks. Needs NowPlayingListener approved; without it nothing shows.
 */
final class NowPlaying {

    static final class Track {
        final String pkg, title, artist;
        @Nullable final Bitmap art;

        Track(String pkg, String title, String artist, @Nullable Bitmap art) {
            this.pkg = pkg;
            this.title = title;
            this.artist = artist;
            this.art = art;
        }

        boolean sameAs(@Nullable Track o) {
            return o != null && o.pkg.equals(pkg) && o.title.equals(title) && o.artist.equals(artist) && o.art == art;
        }
    }

    interface Listener {
        /** The track playing now, or null when nothing allowed plays. */
        void onNowPlaying(@Nullable Track track);
    }

    private final MediaSessionManager sessions;
    private final ComponentName approval;
    private final List<String> packages;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Listener listener;
    @Nullable private MediaController current;
    @Nullable private Track shown;
    private boolean running;

    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::pick;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            publish();
        }

        @Override
        public void onMetadataChanged(@Nullable MediaMetadata metadata) {
            publish();
        }

        @Override
        public void onSessionDestroyed() {
            refresh();
        }
    };

    NowPlaying(Context context, List<String> packages) {
        sessions = context.getSystemService(MediaSessionManager.class);
        approval = new ComponentName(context, NowPlayingListener.class);
        this.packages = packages;
    }

    void start(Listener l) {
        listener = l;
        if (running || sessions == null || packages.isEmpty()) return;
        try {
            sessions.addOnActiveSessionsChangedListener(changed, approval, handler);
            running = true;
            refresh();
        } catch (SecurityException notApproved) {
            listener.onNowPlaying(null);
        }
    }

    void stop() {
        if (!running) return;
        running = false;
        sessions.removeOnActiveSessionsChangedListener(changed);
        follow(null);
        shown = null;
    }

    private void refresh() {
        if (!running) return;
        try {
            pick(sessions.getActiveSessions(approval));
        } catch (SecurityException revoked) {
            follow(null);
            publish();
        }
    }

    /** The allowed session to follow: one that is playing if any, else the first allowed one. */
    private void pick(@Nullable List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController c : list) {
                if (!packages.contains(c.getPackageName())) continue;
                if (playing(c.getPlaybackState())) {
                    best = c;
                    break;
                }
                if (best == null) best = c;
            }
        }
        follow(best);
        publish();
    }

    private void follow(@Nullable MediaController next) {
        if (current != null && next != null && current.getSessionToken().equals(next.getSessionToken())) return;
        if (current != null) current.unregisterCallback(callback);
        current = next;
        if (current != null) current.registerCallback(callback, handler);
    }

    private void publish() {
        Track track = null;
        MediaController c = current;
        if (c != null && playing(c.getPlaybackState())) {
            MediaMetadata m = c.getMetadata();
            if (m != null) {
                String title = text(m, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                String artist = text(m, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
                Bitmap art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                if (!title.isEmpty()) track = new Track(c.getPackageName(), title, artist, art);
            }
        }
        if (track == null ? shown == null : track.sameAs(shown)) return;
        shown = track;
        if (listener != null) listener.onNowPlaying(track);
    }

    private static String text(MediaMetadata m, String key, String fallbackKey) {
        CharSequence s = m.getText(key);
        if (s == null || s.length() == 0) s = m.getText(fallbackKey);
        return s == null ? "" : s.toString().trim();
    }

    /** Playing, or between two tracks (buffering, skipping), so the line does not blink at each change. */
    private static boolean playing(@Nullable PlaybackState s) {
        if (s == null) return false;
        switch (s.getState()) {
            case PlaybackState.STATE_PLAYING:
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_CONNECTING:
            case PlaybackState.STATE_SKIPPING_TO_NEXT:
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM:
                return true;
            default:
                return false;
        }
    }
}

package br.com.lyriclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.media.session.MediaController;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

public class LyricsNotificationListener extends NotificationListenerService {
    private static final String CHANNEL = "lyriclock_lyrics";
    private static final int NOTIFICATION_ID = 7101;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaController spotify;
    private String cachedKey = "";
    private List<LyricLine> lyricLines = new ArrayList<>();
    private boolean lookupRunning = false;
    private String lastRendered = "";

    private static class LyricLine {
        final long timeMs;
        final String text;
        LyricLine(long timeMs, String text) { this.timeMs = timeMs; this.text = text; }
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        createChannel();
        refreshSpotifySession();
        handler.post(tick);
    }

    @Override public void onListenerDisconnected() {
        handler.removeCallbacks(tick);
        spotify = null;
        super.onListenerDisconnected();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn != null && "com.spotify.music".equals(sbn.getPackageName())) {
            refreshSpotifySession();
        }
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && "com.spotify.music".equals(sbn.getPackageName())) {
            handler.postDelayed(this::refreshSpotifySession, 700);
        }
    }

    private void refreshSpotifySession() {
        try {
            List<MediaController> sessions = getActiveSessions(new ComponentName(this, LyricsNotificationListener.class));
            MediaController found = null;
            if (sessions != null) {
                for (MediaController c : sessions) {
                    if ("com.spotify.music".equals(c.getPackageName())) { found = c; break; }
                }
            }
            spotify = found;
            if (spotify == null) {
                cancelLyrics();
                return;
            }
            MediaMetadata md = spotify.getMetadata();
            if (md != null) ensureLyrics(
                md.getString(MediaMetadata.METADATA_KEY_TITLE),
                md.getString(MediaMetadata.METADATA_KEY_ARTIST),
                md.getString(MediaMetadata.METADATA_KEY_ALBUM),
                md.getLong(MediaMetadata.METADATA_KEY_DURATION)
            );
        } catch (SecurityException ignored) { }
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            try {
                if (spotify == null) refreshSpotifySession();
                MediaController c = spotify;
                if (c != null) {
                    MediaMetadata md = c.getMetadata();
                    PlaybackState ps = c.getPlaybackState();
                    if (md != null && ps != null && ps.getState() == PlaybackState.STATE_PLAYING) {
                        ensureLyrics(md.getString(MediaMetadata.METADATA_KEY_TITLE),
                            md.getString(MediaMetadata.METADATA_KEY_ARTIST),
                            md.getString(MediaMetadata.METADATA_KEY_ALBUM),
                            md.getLong(MediaMetadata.METADATA_KEY_DURATION));
                        renderAt(positionNow(ps));
                    } else if (ps != null && (ps.getState() == PlaybackState.STATE_PAUSED
                            || ps.getState() == PlaybackState.STATE_STOPPED)) {
                        // Keep the last lyric notification visible while paused.
                    }
                }
            } catch (Exception ignored) { }
            handler.postDelayed(this, 650);
        }
    };

    private long positionNow(PlaybackState state) {
        long elapsed = Math.max(0, android.os.SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime());
        float speed = state.getPlaybackSpeed();
        return state.getPosition() + (long)(elapsed * Math.max(0f, speed));
    }

    private void ensureLyrics(String title, String artist, String album, long duration) {
        if (title == null || title.trim().isEmpty()) return;
        String key = title + "|" + (artist == null ? "" : artist);
        if (key.equals(cachedKey) || lookupRunning) return;
        cachedKey = key;
        lyricLines = new ArrayList<>();
        lookupRunning = true;
        new Thread(() -> {
            List<LyricLine> found = fetchLyrics(title, artist, album, duration);
            handler.post(() -> {
                lookupRunning = false;
                lyricLines = found;
                lastRendered = "";
                if (found.isEmpty()) showMessage("Letra sincronizada não encontrada");
            });
        }, "LyricLock-Lyrics").start();
    }

    private List<LyricLine> fetchLyrics(String title, String artist, String album, long duration) {
        List<LyricLine> result = new ArrayList<>();
        HttpURLConnection connection = null;
        try {
            String query = "track_name=" + enc(title)
                + "&artist_name=" + enc(artist == null ? "" : artist)
                + "&album_name=" + enc(album == null ? "" : album)
                + "&duration=" + Math.max(0, duration / 1000);
            URL url = new URL("https://lrclib.net/api/get?" + query);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);
            connection.setRequestProperty("User-Agent", "LyricLock/0.1.0 (Android)");
            if (connection.getResponseCode() == 200) {
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }
                JSONObject json = new JSONObject(body.toString());
                String synced = json.optString("syncedLyrics", "");
                if (!synced.isEmpty()) {
                    for (String line : synced.split("\\r?\\n")) {
                        int close = line.indexOf(']');
                        if (!line.startsWith("[") || close < 0) continue;
                        long time = parseTime(line.substring(1, close));
                        String words = line.substring(close + 1).trim();
                        if (time >= 0 && !words.isEmpty()) result.add(new LyricLine(time, words));
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
        return result;
    }

    private String enc(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    private long parseTime(String stamp) {
        try {
            String[] p = stamp.split(":");
            double minutes = Double.parseDouble(p[0]);
            double seconds = Double.parseDouble(p[1]);
            return (long)((minutes * 60 + seconds) * 1000);
        } catch (Exception e) { return -1; }
    }

    private void renderAt(long position) {
        if (lyricLines.isEmpty()) return;
        int current = 0;
        for (int i = 0; i < lyricLines.size(); i++) {
            if (lyricLines.get(i).timeMs <= position) current = i;
            else break;
        }
        StringBuilder signature = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            int index = current + i;
            signature.append(index < lyricLines.size() ? lyricLines.get(index).text : "").append('|');
        }
        signature.append(current);
        if (signature.toString().equals(lastRendered)) return;
        lastRendered = signature.toString();
        showFourLines(current);
    }

    private void showFourLines(int current) {
        RemoteViews compact = new RemoteViews(getPackageName(), R.layout.lyrics_notification);
        RemoteViews expanded = new RemoteViews(getPackageName(), R.layout.lyrics_notification_expanded);
        for (int i = 0; i < 4; i++) {
            int index = current + i;
            String text = index < lyricLines.size() ? lyricLines.get(index).text : "";
            int color = i == 0 ? 0xFFFFFFFF : 0xFF9CA3B5;
            int viewId = getResources().getIdentifier("lyric_" + (i + 1), "id", getPackageName());
            compact.setTextViewText(viewId, text);
            compact.setTextColor(viewId, color);
            expanded.setTextViewText(viewId, text);
            expanded.setTextColor(viewId, color);
        }
        Notification notification = new NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle("")
            .setContentText(lyricLines.get(current).text)
            .setCustomContentView(compact)
            .setCustomBigContentView(expanded)
            .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification);
    }

    private void showMessage(String message) {
        Notification n = new NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(message)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, n);
    }

    private void cancelLyrics() {
        getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
        cachedKey = "";
        lyricLines.clear();
        lastRendered = "";
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Letras na tela bloqueada",
            NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Mostra quatro versos sincronizados da música do Spotify.");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
}

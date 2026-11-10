# LyricLock

Android app prototype that reads the active Spotify media session and shows four synchronized lyric lines in a public lock-screen notification.

## Build
The GitHub Actions workflow `.github/workflows/build-lyriclock.yml` builds a debug APK on pushes to `lyricslock/**` and uploads it as the `LyricLock-debug` artifact.

## First use
1. Install the APK.
2. Open LyricLock and tap **Ativar acesso às notificações**.
3. Enable LyricLock in Android's Notification access settings.
4. Start a track in Spotify.
5. Allow LyricLock notifications to appear on the lock screen and expand the notification if Android collapses it.

Lyrics are looked up through the LRCLIB public API. Only synchronized lyrics are used in this first version. Lyrics availability and lock-screen rendering depend on the catalog and Android/One UI notification settings.

## Privacy
The app reads Spotify's media-session metadata (track, artist, album and duration) to look up lyrics. It does not log into Spotify or control the Spotify account.

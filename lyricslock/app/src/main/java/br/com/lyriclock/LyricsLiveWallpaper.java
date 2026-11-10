package br.com.lyriclock;

import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.File;
import java.io.InputStream;

public class LyricsLiveWallpaper extends WallpaperService {
    @Override public Engine onCreateEngine() { return new LyricEngine(); }

    private class LyricEngine extends Engine {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private boolean visible = false;
        private float rotation = 0f;
        private Bitmap cover;
        private Bitmap background;
        private String loadedWallpaperUri = "";
        private long lastCoverCheck = 0;
        private long lastWallpaperCheck = 0;

        private final Runnable draw = new Runnable() {
            @Override public void run() { drawFrame(); }
        };

        @Override public void onVisibilityChanged(boolean value) {
            visible = value;
            if (value) drawFrame(); else handler.removeCallbacks(draw);
        }

        @Override public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            drawFrame();
        }

        @Override public void onSurfaceDestroyed(SurfaceHolder holder) {
            visible = false;
            handler.removeCallbacks(draw);
            super.onSurfaceDestroyed(holder);
        }

        private void drawFrame() {
            handler.removeCallbacks(draw);
            if (!visible) return;
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) render(canvas);
            } catch (Exception ignored) {
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
            rotation = (rotation + 2.2f) % 360f;
            handler.postDelayed(draw, 80);
        }

        private void render(Canvas canvas) {
            float w = canvas.getWidth();
            float h = canvas.getHeight();
            loadWallpaperIfNeeded();
            loadCover();

            if (background != null && !background.isRecycled()) {
                float scale = Math.max(w / background.getWidth(), h / background.getHeight());
                float dx = (w - background.getWidth() * scale) / 2f;
                float dy = (h - background.getHeight() * scale) / 2f;
                canvas.drawBitmap(background, new Matrix(), p);
                Matrix matrix = new Matrix();
                matrix.setScale(scale, scale);
                matrix.postTranslate(dx, dy);
                canvas.drawColor(Color.BLACK);
                canvas.drawBitmap(background, matrix, p);
            } else {
                p.setShader(new LinearGradient(0, 0, 0, h, 0xff08152d, 0xff061018, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, p);
                p.setShader(null);
            }

            SharedPreferences prefs = getSharedPreferences("lyriclock_live", MODE_PRIVATE);
            String title = prefs.getString("title", "Sua música");
            String artist = prefs.getString("artist", "Spotify");
            String[] lines = new String[4];
            for (int i = 0; i < 4; i++) lines[i] = prefs.getString("line" + i, i == 0 ? "As letras aparecerão aqui" : "");

            // Vinheta leve para a letra continuar legível sem esconder o wallpaper.
            p.setShader(new LinearGradient(0, h * 0.62f, 0, h, 0x00020a12, 0x99020a12, Shader.TileMode.CLAMP));
            canvas.drawRect(0, h * 0.62f, w, h, p);
            p.setShader(null);

            float center = w * 0.5f;
            float vinylY = h * 0.205f;
            float radius = w * 0.175f;

            p.setColor(0xee080a12);
            p.setShadowLayer(24, 0, 5, 0xaa8a70ff);
            canvas.drawCircle(center, vinylY, radius, p);
            p.clearShadowLayer();

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.5f);
            p.setColor(0xffc5b8ff);
            canvas.drawCircle(center, vinylY, radius, p);
            p.setColor(0xff343044);
            p.setStrokeWidth(1.2f);
            for (int i = 1; i < 8; i++) canvas.drawCircle(center, vinylY, radius * i / 8f, p);
            p.setStyle(Paint.Style.FILL);

            canvas.save();
            canvas.rotate(rotation, center, vinylY);
            if (cover != null && !cover.isRecycled()) {
                p.setShader(new BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                canvas.drawCircle(center, vinylY, radius * 0.43f, p);
                p.setShader(null);
            } else {
                p.setColor(0xff493a83);
                canvas.drawCircle(center, vinylY, radius * 0.43f, p);
                p.setColor(Color.WHITE);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(radius * 0.42f);
                canvas.drawText("♫", center, vinylY + radius * 0.14f, p);
            }
            canvas.restore();

            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            p.setTextSize(w * 0.047f);
            p.setColor(Color.WHITE);
            p.setShadowLayer(5, 0, 2, 0xcc000000);
            canvas.drawText(ellipsize(title, 30), center, h * 0.405f, p);

            p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            p.setTextSize(w * 0.034f);
            p.setColor(0xffe2e2ed);
            canvas.drawText(ellipsize(artist, 36), center, h * 0.435f, p);
            p.clearShadowLayer();

            float lyricY = h * 0.695f;
            p.setTextSize(w * 0.041f);
            for (int i = 0; i < 4; i++) {
                p.setColor(i == 0 ? 0xffffffff : 0xffd0d3df);
                p.setTypeface(Typeface.create("sans-serif", i == 0 ? Typeface.BOLD : Typeface.NORMAL));
                p.setShadowLayer(5, 0, 2, 0xee000000);
                canvas.drawText(ellipsize(lines[i], 38), center, lyricY + i * h * 0.043f, p);
            }
            p.clearShadowLayer();
        }

        private void loadWallpaperIfNeeded() {
            long now = System.currentTimeMillis();
            if (now - lastWallpaperCheck < 2500) return;
            lastWallpaperCheck = now;
            String uriString = getSharedPreferences("lyriclock_live", MODE_PRIVATE).getString("wallpaper_uri", "");
            if (uriString.equals(loadedWallpaperUri)) return;
            loadedWallpaperUri = uriString;
            Bitmap next = null;
            if (uriString != null && !uriString.isEmpty()) {
                try (InputStream input = getContentResolver().openInputStream(Uri.parse(uriString))) {
                    if (input != null) next = BitmapFactory.decodeStream(input);
                } catch (Exception ignored) { }
            }
            if (next != null) {
                if (background != null && !background.isRecycled()) background.recycle();
                background = next;
            }
        }

        private void loadCover() {
            long now = System.currentTimeMillis();
            if (now - lastCoverCheck < 4000) return;
            lastCoverCheck = now;
            try {
                File file = new File(getFilesDir(), "spotify-cover.jpg");
                if (file.exists()) {
                    Bitmap next = BitmapFactory.decodeFile(file.getAbsolutePath());
                    if (next != null) {
                        if (cover != null && !cover.isRecycled()) cover.recycle();
                        cover = next;
                    }
                }
            } catch (Exception ignored) { }
        }

        private String ellipsize(String text, int max) {
            if (text == null) return "";
            return text.length() > max ? text.substring(0, max - 1) + "…" : text;
        }
    }
}

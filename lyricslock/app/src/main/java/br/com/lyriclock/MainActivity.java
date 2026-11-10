package br.com.lyriclock;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(10, 12, 18));
        getWindow().setNavigationBarColor(Color.rgb(10, 12, 18));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(26), dp(40), dp(26), dp(30));
        page.setBackgroundColor(Color.rgb(10, 12, 18));

        TextView logo = new TextView(this);
        logo.setText("♫");
        logo.setTextSize(48);
        logo.setTextColor(Color.rgb(151, 125, 255));
        logo.setGravity(Gravity.CENTER);
        page.addView(logo, new LinearLayout.LayoutParams(-1, dp(72)));

        TextView title = new TextView(this);
        title.setText("LyricLock");
        title.setTextSize(30);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        page.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Vinil animado e letras do Spotify na tela de bloqueio.");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.rgb(174, 179, 194));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(10), 0, dp(30));
        page.addView(subtitle);

        TextView preview = new TextView(this);
        preview.setText("♪  Sua música está tocando\n\nVerso atual da letra\nPróximo verso\nTerceiro verso\nQuarto verso");
        preview.setTextSize(16);
        preview.setTextColor(Color.WHITE);
        preview.setGravity(Gravity.CENTER);
        preview.setLineSpacing(dp(7), 1f);
        preview.setPadding(dp(18), dp(24), dp(18), dp(24));
        preview.setBackgroundColor(Color.rgb(25, 28, 40));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.bottomMargin = dp(28);
        page.addView(preview, pp);

        TextView instructions = new TextView(this);
        instructions.setText("Para funcionar:\n1. Toque em ativar acesso às notificações.\n2. Permita o acesso do LyricLock.\n3. Volte ao app e deixe uma música tocar no Spotify.\n\nO LyricLock lê a sessão de mídia do Spotify e procura letras sincronizadas no LRCLIB. A letra aparece na notificação e no papel de parede animado. A exibição pode variar conforme as configurações de notificações da Samsung.");
        instructions.setTextSize(14);
        instructions.setTextColor(Color.rgb(190, 194, 207));
        instructions.setLineSpacing(dp(5), 1f);
        page.addView(instructions, new LinearLayout.LayoutParams(-1, 0, 1));

        Button enable = new Button(this);
        enable.setText("Ativar acesso às notificações");
        enable.setAllCaps(false);
        enable.setTextColor(Color.WHITE);
        enable.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(104, 82, 220)));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(54));
        bp.topMargin = dp(20);
        page.addView(enable, bp);
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        Button wallpaper = new Button(this);
        wallpaper.setText("Aplicar na tela de bloqueio");
        wallpaper.setAllCaps(false);
        wallpaper.setTextColor(Color.WHITE);
        wallpaper.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(55, 65, 105)));
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, dp(54));
        wp.topMargin = dp(10);
        page.addView(wallpaper, wp);
        wallpaper.setOnClickListener(v -> {
            Intent intent = new Intent("android.service.wallpaper.CHANGE_LIVE_WALLPAPER");
            intent.putExtra("android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT",
                new android.content.ComponentName(this, LyricsLiveWallpaper.class));
            startActivity(intent);
        });

        Button wallpaper = new Button(this);
        wallpaper.setText("Aplicar na tela de bloqueio");
        wallpaper.setAllCaps(false);
        wallpaper.setTextColor(Color.WHITE);
        wallpaper.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(55, 65, 105)));
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, dp(54));
        wp.topMargin = dp(10);
        page.addView(wallpaper, wp);
        wallpaper.setOnClickListener(v -> {
            Intent intent = new Intent("android.service.wallpaper.CHANGE_LIVE_WALLPAPER");
            intent.putExtra("android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT",
                new android.content.ComponentName(this, LyricsLiveWallpaper.class));
            startActivity(intent);
        });

        TextView hint = new TextView(this);
        hint.setText("Depois, mantenha as notificações do LyricLock permitidas na tela de bloqueio.");
        hint.setTextSize(12);
        hint.setTextColor(Color.rgb(130, 136, 154));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(12), 0, 0);
        page.addView(hint);

        setContentView(page);
    }
}

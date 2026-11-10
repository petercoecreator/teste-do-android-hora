package br.com.relogioremoto;

import android.app.*;
import android.content.pm.PackageManager;\nimport androidx.core.app.ActivityCompat;\nimport androidx.core.content.ContextCompat;\nimport com.journeyapps.barcodescanner.CaptureActivity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Gravity;
import android.widget.*;
import android.text.InputType;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private EditText ip, pin;
    private TextView status, current, dateText, timeText, connectionBadge;
    private View statusDot;
    private volatile String token = "";
    private LocalDate date = LocalDate.now();
    private int hour = 12, minute = 0, second = 0;

    private final int bg = Color.rgb(8, 13, 27);
    private final int surface = Color.rgb(18, 29, 49);
    private final int surface2 = Color.rgb(25, 39, 63);
    private final int blue = Color.rgb(66, 145, 255);
    private final int cyan = Color.rgb(78, 205, 232);
    private final int white = Color.rgb(241, 246, 255);
    private final int muted = Color.rgb(151, 169, 195);
    private final int green = Color.rgb(71, 205, 145);

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable outline(int color, int stroke, int radius) {
        GradientDrawable d = shape(color, radius);
        d.setStroke(dp(1), stroke);
        return d;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setIncludeFontPadding(true);
        return t;
    }

    private TextView label(String value) {
        TextView t = text(value.toUpperCase(), 11, cyan);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setLetterSpacing(0.08f);
        return t;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private void add(LinearLayout parent, View child, int height, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, height < 0 ? height : dp(height));
        p.bottomMargin = dp(bottom);
        parent.addView(child, p);
    }

    private void addWeight(LinearLayout parent, View child, int width, float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            width == 0 ? 0 : dp(width), LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        parent.addView(child, p);
    }

    private LinearLayout card(LinearLayout parent, int color, int padding) {
        LinearLayout c = column();
        c.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        c.setBackground(shape(color, 22));
        add(parent, c, -2, 14);
        return c;
    }

    private Button button(String value, int color, int textColor) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(textColor);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setBackground(shape(color, 15));
        return b;
    }

    private EditText field(String hint, boolean numeric) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setTextSize(14);
        e.setHint(hint);
        e.setTextColor(white);
        e.setHintTextColor(muted);
        e.setPadding(dp(15), 0, dp(15), 0);
        e.setBackground(outline(surface2, Color.rgb(48, 67, 96), 13));
        if (numeric) e.setInputType(InputType.TYPE_CLASS_NUMBER);
        else e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return e;
    }

    private void space(LinearLayout parent, int height) {
        View v = new View(this);
        add(parent, v, height, 0);
    }

    private void sectionTitle(LinearLayout parent, String title, String subtitle) {
        TextView t = text(title, 17, white);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add(parent, t, -2, 3);
        if (subtitle != null) {
            TextView s = text(subtitle, 12, muted);
            add(parent, s, -2, 13);
        } else {
            space(parent, 12);
        }
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(0);

        LinearLayout page = column();
        page.setPadding(dp(20), dp(18), dp(20), dp(28));
        page.setBackgroundColor(bg);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(page);
        setContentView(scroll);

        buildHeader(page);
        buildConnection(page);
        buildClock(page);
        buildControls(page);
        buildFooter(page);

        String savedHost = getPreferences(0).getString("host", "");
        token = getPreferences(0).getString("token", "");
        if (!savedHost.isEmpty()) ip.setText(savedHost);
        updateLabels();
        if (!token.isEmpty() && !savedHost.isEmpty()) {
            setStatus("Conectando ao computador...", false);
            refresh();
        }
    }

    private void buildHeader(LinearLayout page) {
        LinearLayout top = row();
        LinearLayout logo = new LinearLayout(this);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(shape(blue, 17));
        TextView glyph = text("◷", 31, Color.WHITE);
        glyph.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        logo.addView(glyph);
        top.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout names = column();
        names.setPadding(dp(13), 0, 0, 0);
        TextView title = text("Relógio Remoto", 23, white);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add(names, title, -2, 3);
        TextView subtitle = text("CONTROLE DE DATA E HORA DO WINDOWS", 9, muted);
        subtitle.setLetterSpacing(0.07f);
        add(names, subtitle, -2, 0);
        top.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        add(page, top, -2, 22);

        LinearLayout welcome = column();
        welcome.setPadding(dp(18), dp(17), dp(18), dp(17));
        welcome.setBackground(shape(Color.rgb(20, 49, 82), 19));
        TextView h = text("Seu computador, sob controle.", 17, white);
        h.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add(welcome, h, -2, 5);
        TextView desc = text("Consulte o relógio e ajuste a data e a hora do Windows pelo celular.", 12, Color.rgb(190, 215, 242));
        desc.setLineSpacing(dp(2), 1f);
        add(welcome, desc, -2, 0);
        add(page, welcome, -2, 20);
    }

    private void buildConnection(LinearLayout page) {
        LinearLayout c = card(page, surface, 17);
        LinearLayout top = row();
        TextView title = text("Conexão", 17, white);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        connectionBadge = text("● OFFLINE", 10, muted);
        connectionBadge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        connectionBadge.setPadding(dp(10), dp(7), dp(10), dp(7));
        connectionBadge.setBackground(shape(surface2, 30));
        top.addView(connectionBadge);
        add(c, top, -2, 12);

        Button scan = button("▦   Ler QR Code do computador", blue, Color.WHITE);
        add(c, scan, 52, 9);
        scan.setOnClickListener(v -> scanQr());

        TextView manual = text("Ou conecte manualmente", 12, muted);
        manual.setGravity(Gravity.CENTER);
        add(c, manual, 25, 8);

        ip = field("Endereço do computador", false);
        add(c, ip, 50, 9);
        pin = field("PIN de 6 dígitos", true);
        add(c, pin, 50, 10);

        Button connect = button("Conectar ao computador", surface2, white);
        connect.setBackground(outline(surface2, Color.rgb(57, 83, 119), 14));
        add(c, connect, 48, 10);
        connect.setOnClickListener(v -> pair());

        status = text("O computador ainda não está conectado.", 12, muted);
        status.setGravity(Gravity.CENTER);
        add(c, status, -2, 0);
    }

    private void buildClock(LinearLayout page) {
        LinearLayout c = card(page, Color.rgb(15, 37, 61), 18);
        LinearLayout top = row();
        TextView title = text("Relógio do computador", 16, white);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView live = text("● AO VIVO", 9, green);
        live.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(live);
        add(c, top, -2, 14);

        current = text("Conecte-se para consultar a data e a hora.", 18, white);
        current.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        current.setGravity(Gravity.CENTER);
        current.setLineSpacing(dp(5), 1f);
        current.setPadding(0, dp(10), 0, dp(10));
        add(c, current, -2, 12);

        Button update = button("↻   Atualizar horário", Color.rgb(25, 64, 96), Color.rgb(195, 228, 255));
        add(c, update, 44, 0);
        update.setOnClickListener(v -> refresh());
    }

    private void buildControls(LinearLayout page) {
        LinearLayout c = card(page, surface, 17);
        sectionTitle(c, "Ajustar data e hora", "Escolha os valores que serão enviados ao Windows.");

        TextView dateCaption = label("Data selecionada");
        add(c, dateCaption, -2, 7);
        LinearLayout dateRow = row();
        dateText = text("", 20, white);
        dateText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        dateRow.addView(dateText, new LinearLayout.LayoutParams(0, -2, 1));
        Button chooseDate = button("Escolher data", surface2, white);
        dateRow.addView(chooseDate, new LinearLayout.LayoutParams(dp(132), dp(44)));
        add(c, dateRow, -2, 17);
        chooseDate.setOnClickListener(v ->
            new DatePickerDialog(this, (v1, y, m, d) -> {
                date = LocalDate.of(y, m + 1, d);
                updateLabels();
            }, date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth()).show()
        );

        TextView timeCaption = label("Hora selecionada");
        add(c, timeCaption, -2, 7);
        LinearLayout timeRow = row();
        timeText = text("", 20, white);
        timeText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        timeRow.addView(timeText, new LinearLayout.LayoutParams(0, -2, 1));
        Button chooseTime = button("Escolher hora", surface2, white);
        timeRow.addView(chooseTime, new LinearLayout.LayoutParams(dp(132), dp(44)));
        add(c, timeRow, -2, 17);
        chooseTime.setOnClickListener(v ->
            new TimePickerDialog(this, (v1, h, m) -> {
                hour = h;
                minute = m;
                EditText sec = new EditText(this);
                sec.setInputType(InputType.TYPE_CLASS_NUMBER);
                sec.setSingleLine(true);
                sec.setText(String.valueOf(second));
                sec.setHint("Segundos (0–59)");
                new AlertDialog.Builder(this).setTitle("Definir segundos").setView(sec)
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Salvar", (a, w) -> {
                        try {
                            second = Math.max(0, Math.min(59,
                                Integer.parseInt(sec.getText().toString())));
                        } catch (Exception ex) { second = 0; }
                        updateLabels();
                    }).show();
            }, hour, minute, true).show()
        );

        Button apply = button("✓   Aplicar no computador", blue, Color.WHITE);
        add(c, apply, 52, 0);
        apply.setOnClickListener(v -> confirmApply());
    }

    private void buildFooter(LinearLayout page) {
        TextView note = text(
            "Importante: mantenha o servidor aberto no Windows. O computador precisa estar acessível pela rede configurada. A alteração do relógio exige autorização de administrador e pode afetar certificados, autenticação e atualizações.",
            11, muted);
        note.setGravity(Gravity.CENTER);
        note.setLineSpacing(dp(3), 1f);
        note.setPadding(dp(8), dp(3), dp(8), 0);
        add(page, note, -2, 0);
    }

    private void setStatus(String value, boolean connected) {
        if (status != null) status.setText(value);
        if (connectionBadge != null) {
            connectionBadge.setText(connected ? "● CONECTADO" : "● OFFLINE");
            connectionBadge.setTextColor(connected ? green : muted);
            connectionBadge.setBackground(shape(
                connected ? Color.rgb(19, 66, 57) : surface2, 30));
        }
    }

    private static final int CAMERA_REQUEST = 731;

    private void scanQr() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA}, CAMERA_REQUEST);
            return;
        }
        launchQrScanner();
    }

    private void launchQrScanner() {
        new IntentIntegrator(this)
            .setCaptureActivity(CaptureActivity.class)
            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setPrompt("Aponte a câmera para o QR Code exibido no computador")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
            .initiateScan();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchQrScanner();
            } else {
                message("A permissão da câmera é necessária para ler o QR Code. Você também pode conectar digitando o IP e o PIN.");
            }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                try {
                    JSONObject qr = new JSONObject(result.getContents());
                    if (!"RelogioRemoto".equals(qr.optString("app")))
                        throw new Exception("Este QR Code não pertence ao Relógio Remoto.");
                    String host = qr.getString("host").trim();
                    int port = qr.optInt("port", 8765);
                    String code = qr.getString("pin").trim();
                    if (host.isEmpty() || code.length() != 6 || port < 1 || port > 65535)
                        throw new Exception("QR Code incompleto ou inválido.");
                    ip.setText(port == 8765 ? host : host + ":" + port);
                    pin.setText(code);
                    token = "";
                    getPreferences(0).edit().remove("token").apply();
                    pair();
                } catch (Exception e) { message(e.getMessage()); }
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void updateLabels() {
        dateText.setText(String.format("%02d/%02d/%04d",
            date.getDayOfMonth(), date.getMonthValue(), date.getYear()));
        timeText.setText(String.format("%02d:%02d:%02d", hour, minute, second));
    }

    private String base() {
        String host = ip.getText().toString().trim()
            .replace("http://", "").replace("https://", "");
        while (host.endsWith("/")) host = host.substring(0, host.length() - 1);
        if (host.contains("/")) host = host.substring(0, host.indexOf('/'));
        if (host.isEmpty())
            throw new IllegalArgumentException("Informe o endereço do computador ou leia o QR Code.");
        return host.matches(".*:\\d+$") ? "http://" + host : "http://" + host + ":8765";
    }

    private JSONObject request(String baseUrl, String path, JSONObject body, boolean auth) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(baseUrl + path).openConnection();
        c.setConnectTimeout(7000);
        c.setReadTimeout(7000);
        c.setRequestMethod(body == null ? "GET" : "POST");
        c.setRequestProperty("Accept", "application/json");
        if (auth) c.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream o = c.getOutputStream()) {
                    o.write(body.toString().getBytes("UTF-8"));
                }
            }
            int code = c.getResponseCode();
            InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (in != null) try (InputStream x = in) {
                byte[] buf = new byte[1024];
                int n;
                while ((n = x.read(buf)) != -1) out.write(buf, 0, n);
            }
            JSONObject result = new JSONObject(out.toString("UTF-8"));
            if (code >= 400) throw new Exception(result.optString("error", "Erro HTTP " + code));
            return result;
        } finally { c.disconnect(); }
    }

    private void pair() {
        final String code = pin.getText().toString().trim();
        final String endpoint;
        try { endpoint = base(); }
        catch (Exception e) { message(e.getMessage()); return; }
        if (code.length() != 6) {
            message("Leia o QR Code ou digite o PIN de 6 números exibido no computador.");
            return;
        }
        setStatus("Conectando...", false);
        worker.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("pin", code);
                JSONObject result = request(endpoint, "/pair", body, false);
                token = result.getString("token");
                String host = ip.getText().toString().trim();
                getPreferences(0).edit().putString("token", token).putString("host", host).apply();
                runOnUiThread(() -> {
                    setStatus("Conectado ao computador.", true);
                    refresh();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setStatus("Não foi possível conectar.", false);
                    message(e.getMessage());
                });
            }
        });
    }

    private void refresh() {
        if (token.isEmpty()) {
            message("Conecte-se primeiro lendo o QR Code.");
            return;
        }
        final String endpoint;
        try { endpoint = base(); }
        catch (Exception e) { message(e.getMessage()); return; }
        worker.execute(() -> {
            try {
                JSONObject r = request(endpoint, "/time", null, true);
                String value = r.optString("datetime").replace("T", "   ") +
                    "\nFuso horário: " + r.optString("timezone");
                runOnUiThread(() -> {
                    current.setText(value);
                    setStatus("Conectado ao computador.", true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setStatus("Falha na conexão.", false);
                    message(e.getMessage());
                });
            }
        });
    }

    private void confirmApply() {
        if (token.isEmpty()) {
            message("Conecte-se primeiro lendo o QR Code.");
            return;
        }
        String value = String.format("%04d-%02d-%02dT%02d:%02d:%02d",
            date.getYear(), date.getMonthValue(), date.getDayOfMonth(),
            hour, minute, second);
        new AlertDialog.Builder(this).setTitle("Confirmar alteração")
            .setMessage("Alterar a data e a hora do Windows para:\n\n" + value + "\n\nDeseja continuar?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Aplicar", (d, w) -> {
                final String endpoint;
                try { endpoint = base(); }
                catch (Exception e) { message(e.getMessage()); return; }
                worker.execute(() -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("datetime", value);
                        request(endpoint, "/set", body, true);
                        runOnUiThread(() -> {
                            message("Data e hora alteradas no computador.");
                            refresh();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> message(e.getMessage()));
                    }
                });
            }).show();
    }

    private void message(String s) {
        new AlertDialog.Builder(this)
            .setMessage(s == null ? "Erro desconhecido." : s)
            .setPositiveButton("OK", null).show();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }
}

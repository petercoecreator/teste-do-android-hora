package br.com.relogioremoto;

import android.app.*;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;
import com.journeyapps.barcodescanner.IntentIntegrator;
import com.journeyapps.barcodescanner.IntentResult;
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
    private TextView status, current, dateText, timeText;
    private volatile String token = "";
    private LocalDate date = LocalDate.now();
    private int hour = 12, minute = 0, second = 0;
    private final int bg = Color.rgb(8,13,27), panel = Color.rgb(19,31,54),
            blue = Color.rgb(55,145,255), white = Color.rgb(238,244,255);

    private GradientDrawable rounded(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(22); return d;
    }
    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size);
        t.setTextColor(color); return t;
    }
    private void add(LinearLayout root, View v, int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, height);
        p.bottomMargin = 12; root.addView(v,p);
    }
    private Button button(String s, LinearLayout root) {
        Button b = new Button(this); b.setText(s); b.setTextColor(white);
        b.setTextSize(13); b.setTypeface(null, Typeface.BOLD);
        b.setBackground(rounded(panel)); add(root,b,54); return b;
    }
    private EditText field(String hint, LinearLayout root, boolean numeric) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setHint(hint);
        e.setTextColor(white); e.setHintTextColor(0xff9aabc5);
        e.setPadding(16,4,16,4); e.setBackground(rounded(panel));
        if (numeric) e.setInputType(2);
        add(root,e,54); return e;
    }
    private void heading(LinearLayout root, String s) {
        TextView t=text(s,12,blue); t.setTypeface(null,Typeface.BOLD);
        t.setPadding(0,14,0,8); add(root,t,42);
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        LinearLayout root=new LinearLayout(this); root.setOrientation(1);
        root.setPadding(22,26,22,28); root.setBackgroundColor(bg);
        ScrollView scroll=new ScrollView(this); scroll.addView(root); setContentView(scroll);

        TextView title=text("RELÓGIO REMOTO",25,white);
        title.setTypeface(null,Typeface.BOLD); add(root,title,42);
        add(root,text("CONTROLE DE DATA E HORA DO WINDOWS",11,blue),28);

        heading(root,"CONEXÃO AUTOMÁTICA");
        Button scan=button("LER QR CODE DO COMPUTADOR",root);
        scan.setBackground(rounded(blue));
        scan.setOnClickListener(v -> scanQr());

        heading(root,"CONEXÃO MANUAL (ALTERNATIVA)");
        ip=field("IP do PC (ex.: 192.168.0.10)",root,false);
        pin=field("PIN de 6 dígitos",root,true);
        button("CONECTAR",root).setOnClickListener(v -> pair());
        status=text("● Desconectado",13,0xff9aabc5); add(root,status,36);

        heading(root,"HORÁRIO ATUAL DO COMPUTADOR");
        current=text("Leia o QR Code para conectar.",18,white); add(root,current,60);
        button("ATUALIZAR",root).setOnClickListener(v -> refresh());

        heading(root,"DEFINIR NOVO HORÁRIO");
        dateText=text("",19,white); add(root,dateText,38);
        button("ESCOLHER DATA",root).setOnClickListener(v ->
            new DatePickerDialog(this,(v1,y,m,d)->{date=LocalDate.of(y,m+1,d);updateLabels();},
                date.getYear(),date.getMonthValue()-1,date.getDayOfMonth()).show());

        timeText=text("",19,white); add(root,timeText,38);
        button("ESCOLHER HORA",root).setOnClickListener(v ->
            new TimePickerDialog(this,(v1,h,m)->{
                hour=h; minute=m;
                EditText sec=new EditText(this); sec.setInputType(2);
                sec.setHint("Segundos (0–59)"); sec.setText(String.valueOf(second));
                new AlertDialog.Builder(this).setTitle("Segundos").setView(sec)
                    .setNegativeButton("Cancelar",null).setPositiveButton("OK",(a,w)->{
                        try { second=Math.max(0,Math.min(59,Integer.parseInt(sec.getText().toString()))); }
                        catch(Exception ex){second=0;} updateLabels();
                    }).show();
            },hour,minute,true).show());

        Button apply=button("APLICAR NO PC",root); apply.setBackground(rounded(blue));
        apply.setOnClickListener(v -> confirmApply());
        TextView note=text("PC e celular precisam estar na mesma rede Wi-Fi. O Windows solicitará autorização de administrador. Alterar o relógio pode afetar certificados, autenticação e atualizações.",12,0xff9aabc5);
        note.setPadding(0,14,0,0); add(root,note,-2);

        String savedHost=getPreferences(0).getString("host","");
        token=getPreferences(0).getString("token","");
        if(!savedHost.isEmpty()) ip.setText(savedHost);
        updateLabels();
        if(!token.isEmpty() && !savedHost.isEmpty()) refresh();
    }

    private void scanQr() {
        new IntentIntegrator(this)
            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setPrompt("Aponte a câmera para o QR Code exibido no computador")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
            .initiateScan();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data) {
        IntentResult result=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);
        if(result!=null) {
            if(result.getContents()!=null) {
                try {
                    JSONObject qr=new JSONObject(result.getContents());
                    if(!"RelogioRemoto".equals(qr.optString("app")))
                        throw new Exception("Este QR Code não pertence ao Relógio Remoto.");
                    String host=qr.getString("host").trim();
                    int port=qr.optInt("port",8765);
                    String code=qr.getString("pin").trim();
                    if(host.isEmpty() || code.length()!=6 || port<1 || port>65535)
                        throw new Exception("QR Code incompleto ou inválido.");
                    ip.setText(port==8765?host:host+":"+port);
                    pin.setText(code);
                    token="";
                    getPreferences(0).edit().remove("token").apply();
                    pair();
                } catch(Exception e) { message(e.getMessage()); }
            }
            return;
        }
        super.onActivityResult(requestCode,resultCode,data);
    }

    private void updateLabels() {
        dateText.setText(String.format("%02d/%02d/%04d",date.getDayOfMonth(),date.getMonthValue(),date.getYear()));
        timeText.setText(String.format("%02d:%02d:%02d",hour,minute,second));
    }
    private String base() {
        String host=ip.getText().toString().trim().replace("http://","").replace("https://","");
        while(host.endsWith("/")) host=host.substring(0,host.length()-1);
        if(host.contains("/")) host=host.substring(0,host.indexOf('/'));
        if(host.isEmpty()) throw new IllegalArgumentException("Informe o IP do computador ou leia o QR Code.");
        return host.matches(".*:\\d+$")?"http://"+host:"http://"+host+":8765";
    }
    private JSONObject request(String baseUrl,String path,JSONObject body,boolean auth) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(baseUrl+path).openConnection();
        c.setConnectTimeout(7000); c.setReadTimeout(7000);
        c.setRequestMethod(body==null?"GET":"POST");
        c.setRequestProperty("Accept","application/json");
        if(auth) c.setRequestProperty("Authorization","Bearer "+token);
        try {
            if(body!=null) {
                c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json; charset=utf-8");
                try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes("UTF-8"));}
            }
            int code=c.getResponseCode();
            InputStream in=code<400?c.getInputStream():c.getErrorStream();
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            if(in!=null) try(InputStream x=in){byte[] buf=new byte[1024];int n;while((n=x.read(buf))!=-1)out.write(buf,0,n);}
            JSONObject result=new JSONObject(out.toString("UTF-8"));
            if(code>=400) throw new Exception(result.optString("error","Erro HTTP "+code));
            return result;
        } finally { c.disconnect(); }
    }
    private void pair() {
        final String code=pin.getText().toString().trim();
        final String endpoint;
        try { endpoint=base(); } catch(Exception e){message(e.getMessage());return;}
        if(code.length()!=6){message("Leia o QR Code ou digite o PIN de 6 números exibido no computador.");return;}
        status.setText("Conectando...");
        worker.execute(()->{
            try {
                JSONObject body=new JSONObject(); body.put("pin",code);
                JSONObject result=request(endpoint,"/pair",body,false);
                token=result.getString("token");
                String host=ip.getText().toString().trim();
                getPreferences(0).edit().putString("token",token).putString("host",host).apply();
                runOnUiThread(()->{status.setText("● Conectado ao computador");refresh();});
            } catch(Exception e) { runOnUiThread(()->{status.setText("● Não conectado");message(e.getMessage());}); }
        });
    }
    private void refresh() {
        if(token.isEmpty()){message("Conecte-se primeiro lendo o QR Code.");return;}
        final String endpoint;
        try { endpoint=base(); } catch(Exception e){message(e.getMessage());return;}
        worker.execute(()->{
            try {
                JSONObject r=request(endpoint,"/time",null,true);
                String value=r.optString("datetime").replace("T","  ")+"\nFuso: "+r.optString("timezone");
                runOnUiThread(()->{current.setText(value);status.setText("● Conectado ao computador");});
            } catch(Exception e) { runOnUiThread(()->{status.setText("● Falha na conexão");message(e.getMessage());}); }
        });
    }
    private void confirmApply() {
        if(token.isEmpty()){message("Conecte-se primeiro lendo o QR Code.");return;}
        String value=String.format("%04d-%02d-%02dT%02d:%02d:%02d",date.getYear(),date.getMonthValue(),date.getDayOfMonth(),hour,minute,second);
        new AlertDialog.Builder(this).setTitle("Confirmar alteração")
            .setMessage("Alterar a data e a hora do Windows para:\n"+value+"?")
            .setNegativeButton("Cancelar",null).setPositiveButton("Aplicar",(d,w)->{
                final String endpoint;
                try { endpoint=base(); } catch(Exception e){message(e.getMessage());return;}
                worker.execute(()->{
                    try {
                        JSONObject body=new JSONObject();body.put("datetime",value);
                        request(endpoint,"/set",body,true);
                        runOnUiThread(()->{message("Data e hora alteradas no computador.");refresh();});
                    } catch(Exception e){runOnUiThread(()->message(e.getMessage()));}
                });
            }).show();
    }
    private void message(String s) {
        new AlertDialog.Builder(this).setMessage(s==null?"Erro desconhecido.":s).setPositiveButton("OK",null).show();
    }
    @Override protected void onDestroy(){super.onDestroy();worker.shutdownNow();}
}

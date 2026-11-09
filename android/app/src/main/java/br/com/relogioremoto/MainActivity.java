package br.com.relogioremoto;

import android.app.*;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;
import android.widget.*;
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
    private String token = "";
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
    private void add(LinearLayout root, android.view.View v, int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, height);
        p.bottomMargin = 12; root.addView(v,p);
    }
    private Button button(String s, LinearLayout root) {
        Button b = new Button(this); b.setText(s); b.setTextColor(white);
        b.setTextSize(13); b.setTypeface(null, Typeface.BOLD);
        b.setBackground(rounded(panel)); add(root,b,54); return b;
    }
    private EditText field(String hint, LinearLayout root) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setHint(hint);
        e.setTextColor(white); e.setHintTextColor(0xff9aabc5);
        e.setPadding(16,4,16,4); e.setBackground(rounded(panel)); add(root,e,54);
        return e;
    }
    private void heading(LinearLayout root, String s) {
        TextView t=text(s,12,blue); t.setTypeface(null,Typeface.BOLD);
        t.setPadding(0,14,0,8); add(root,t,42);
    }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        token=getPreferences(0).getString("token","");
        LinearLayout root=new LinearLayout(this); root.setOrientation(1);
        root.setPadding(22,26,22,28); root.setBackgroundColor(bg);
        ScrollView scroll=new ScrollView(this); scroll.addView(root); setContentView(scroll);
        TextView title=text("RELÓGIO REMOTO",25,white);
        title.setTypeface(null,Typeface.BOLD); add(root,title,42);
        add(root,text("CONTROLE DE DATA E HORA DO WINDOWS",11,blue),28);

        heading(root,"CONEXÃO COM O PC");
        ip=field("IP do PC (ex.: 192.168.0.10)",root);
        pin=field("PIN de 6 dígitos mostrado no PC",root); pin.setInputType(2);
        button("CONECTAR",root).setOnClickListener(v -> pair());
        status=text("● Desconectado",13,0xff9aabc5); add(root,status,36);

        heading(root,"HORÁRIO ATUAL DO COMPUTADOR");
        current=text("Conecte-se para consultar.",18,white); add(root,current,60);
        button("ATUALIZAR",root).setOnClickListener(v -> refresh());

        heading(root,"DEFINIR NOVO HORÁRIO");
        dateText=text("",19,white); add(root,dateText,38);
        button("ESCOLHER DATA",root).setOnClickListener(v ->
            new DatePickerDialog(this,(v1,y,m,d)->{date=LocalDate.of(y,m+1,d); updateLabels();},
                date.getYear(),date.getMonthValue()-1,date.getDayOfMonth()).show());
        timeText=text("",19,white); add(root,timeText,38);
        button("ESCOLHER HORA",root).setOnClickListener(v ->
            new TimePickerDialog(this,(v1,h,m)->{
                hour=h; minute=m;
                EditText sec=new EditText(this); sec.setInputType(2); sec.setHint("Segundos (0–59)");
                sec.setText(String.valueOf(second));
                new AlertDialog.Builder(this).setTitle("Segundos").setView(sec)
                    .setNegativeButton("Cancelar",null).setPositiveButton("OK",(a,w)->{
                        try { second=Math.max(0,Math.min(59,Integer.parseInt(sec.getText().toString()))); }
                        catch(Exception ex){second=0;} updateLabels();
                    }).show();
            },hour,minute,true).show());
        Button apply=button("APLICAR NO PC",root); apply.setBackground(rounded(blue));
        apply.setOnClickListener(v -> confirmApply());
        TextView note=text("PC e celular precisam estar na mesma rede Wi-Fi. O servidor do Windows deve ficar aberto como administrador.",12,0xff9aabc5);
        note.setPadding(0,14,0,0); add(root,note,-2);
        updateLabels();
        if(!token.isEmpty()) refresh();
    }
    private void updateLabels() {
        dateText.setText(String.format("%02d/%02d/%04d",date.getDayOfMonth(),date.getMonthValue(),date.getYear()));
        timeText.setText(String.format("%02d:%02d:%02d",hour,minute,second));
    }
    private String base() {
        String host=ip.getText().toString().trim().replace("http://","");
        while(host.endsWith("/")) host=host.substring(0,host.length()-1);
        return "http://"+host+":8765";
    }
    private JSONObject request(String path, JSONObject body, boolean auth) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(base()+path).openConnection();
        c.setConnectTimeout(5000); c.setReadTimeout(5000);
        c.setRequestMethod(body==null?"GET":"POST");
        if(auth) c.setRequestProperty("Authorization","Bearer "+token);
        if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
            try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes("UTF-8"));}}
        int code=c.getResponseCode(); InputStream in=code<400?c.getInputStream():c.getErrorStream();
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream x=in){byte[] buf=new byte[1024];int n;while((n=x.read(buf))!=-1)out.write(buf,0,n);}
        c.disconnect(); JSONObject result=new JSONObject(out.toString("UTF-8"));
        if(code>=400) throw new Exception(result.optString("error","Erro HTTP "+code));
        return result;
    }
    private void pair() {
        if(pin.getText().length()!=6){message("Digite o PIN de 6 números exibido no PC.");return;}
        status.setText("Conectando...");
        worker.execute(()->{try{
            JSONObject b=new JSONObject();b.put("pin",pin.getText().toString());
            JSONObject r=request("/pair",b,false);token=r.getString("token");
            getPreferences(0).edit().putString("token",token).apply();
            runOnUiThread(()->{status.setText("● Conectado");refresh();});
        }catch(Exception e){runOnUiThread(()->message(e.getMessage()));}});
    }
    private void refresh() {
        if(token.isEmpty()){message("Conecte-se primeiro.");return;}
        worker.execute(()->{try{
            JSONObject r=request("/time",null,true);
            runOnUiThread(()->{current.setText(r.optString("datetime").replace("T","  ")+"\nFuso: "+r.optString("timezone"));status.setText("● Conectado ao PC");});
        }catch(Exception e){runOnUiThread(()->message(e.getMessage()));}});
    }
    private void confirmApply() {
        if(token.isEmpty()){message("Conecte-se primeiro.");return;}
        String value=String.format("%04d-%02d-%02dT%02d:%02d:%02d",date.getYear(),date.getMonthValue(),date.getDayOfMonth(),hour,minute,second);
        new AlertDialog.Builder(this).setTitle("Confirmar alteração")
            .setMessage("Alterar o relógio do Windows para:\n"+value+"?")
            .setNegativeButton("Cancelar",null).setPositiveButton("Aplicar",(d,w)->worker.execute(()->{
                try{JSONObject b=new JSONObject();b.put("datetime",value);request("/set",b,true);
                    runOnUiThread(()->{message("Data e hora alteradas no PC.");refresh();});
                }catch(Exception e){runOnUiThread(()->message(e.getMessage()));}
            })).show();
    }
    private void message(String s) {
        new AlertDialog.Builder(this).setMessage(s==null?"Erro desconhecido":s).setPositiveButton("OK",null).show();
    }
    @Override protected void onDestroy(){super.onDestroy();worker.shutdownNow();}
}

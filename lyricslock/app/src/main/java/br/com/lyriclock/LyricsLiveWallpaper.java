package br.com.lyriclock;

import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import java.io.File;
import java.util.concurrent.TimeUnit;

public class LyricsLiveWallpaper extends WallpaperService {
 @Override public Engine onCreateEngine(){ return new LyricEngine(); }
 private class LyricEngine extends Engine {
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final Paint p=new Paint(3);
  private boolean visible=false;
  private float rotation=0;
  private Bitmap cover;
  private long lastCoverCheck=0;
  private final Runnable draw=new Runnable(){public void run(){drawFrame();}};
  @Override public void onVisibilityChanged(boolean v){visible=v;if(v)drawFrame();else handler.removeCallbacks(draw);}
  @Override public void onSurfaceChanged(SurfaceHolder h,int f,int w,int he){super.onSurfaceChanged(h,f,w,he);drawFrame();}
  @Override public void onSurfaceDestroyed(SurfaceHolder h){visible=false;handler.removeCallbacks(draw);super.onSurfaceDestroyed(h);}
  private void drawFrame(){
   handler.removeCallbacks(draw);
   if(!visible)return;
   SurfaceHolder holder=getSurfaceHolder(); Canvas c=null;
   try{c=holder.lockCanvas();if(c!=null)render(c);}catch(Exception ignored){}finally{if(c!=null)holder.unlockCanvasAndPost(c);}
   rotation=(rotation+2.2f)%360;
   handler.postDelayed(draw,80);
  }
  private void render(Canvas c){
   float w=c.getWidth(),h=c.getHeight();
   c.drawColor(Color.rgb(5,8,22));
   p.setShader(new LinearGradient(0,0,0,h,0xff080d25,0xff02040c,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
   // lua e céu estrelado
   for(int i=0;i<55;i++){float x=(i*137.7f)%w,y=(i*83.3f)%(h*.48f);p.setColor(0x887da9ff);p.setAlpha(90+(i%5)*25);c.drawCircle(x,y,1+(i%3)*.45f,p);}
   p.setColor(0xffdce4ff);p.setShadowLayer(28,0,0,0xff9c9cff);c.drawCircle(w*.78f,h*.17f,w*.105f,p);p.clearShadowLayer();
   p.setColor(0xff111a36);c.drawCircle(w*.82f,h*.15f,w*.095f,p);
   // cidade e torre
   p.setColor(0xff080d1d);
   for(int i=0;i<13;i++){float bw=w*(.035f+(i%3)*.018f),x=i*w/12f;c.drawRect(x,h*.59f-(i%4)*h*.035f,x+bw,h*.79f,p);}
   p.setColor(0xff4738bd);p.setShadowLayer(12,0,0,0xff7555ff);c.drawRect(w*.81f,h*.48f,w*.825f,h*.75f,p);c.drawCircle(w*.817f,h*.48f,4,p);p.clearShadowLayer();
   // água refletiva
   p.setShader(new LinearGradient(0,h*.77f,0,h,0xff10132d,0xff03040b,Shader.TileMode.CLAMP));c.drawRect(0,h*.77f,w,h,p);p.setShader(null);
   for(int i=0;i<24;i++){float y=h*(.79f+i*.008f),x=(i*71)%((int)w);p.setColor(i%3==0?0x997b54ff:0x6678aaff);p.setStrokeWidth(1+i%3);c.drawLine(x,y,x+w*(.08f+(i%4)*.025f),y,p);}
   SharedPreferences s=getSharedPreferences("lyriclock_live",0);
   String title=s.getString("title","Sua música"),artist=s.getString("artist","Spotify");
   String[] lines=new String[4];for(int i=0;i<4;i++)lines[i]=s.getString("line"+i,i==0?"As letras aparecerão aqui":"");
   float center=w*.5f, vinylY=h*.315f, radius=w*.235f;
   p.setColor(0xff090a10);p.setShadowLayer(22,0,0,0xff8752ff);c.drawCircle(center,vinylY,radius,p);p.clearShadowLayer();
   p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(0xff9c65ff);c.drawCircle(center,vinylY,radius,p);p.setStyle(Paint.Style.FILL);
   p.setColor(0xff252431);for(int i=1;i<8;i++){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);c.drawCircle(center,vinylY,radius*i/8f,p);}p.setStyle(Paint.Style.FILL);
   c.save();c.rotate(rotation,center,vinylY);loadCover();if(cover!=null){p.setShader(new BitmapShader(cover,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP));c.drawCircle(center,vinylY,radius*.43f,p);p.setShader(null);}else{p.setColor(0xff45318b);c.drawCircle(center,vinylY,radius*.43f,p);p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(radius*.42f);c.drawText("♫",center,vinylY+radius*.14f,p);}c.restore();
   p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));p.setTextSize(w*.052f);p.setColor(Color.WHITE);c.drawText(ellipsize(title,26),center,h*.49f,p);
   p.setTypeface(Typeface.create("sans-serif",0));p.setTextSize(w*.038f);p.setColor(0xffb8b5ce);c.drawText(ellipsize(artist,35),center,h*.525f,p);
   float ly=h*.59f;p.setTextSize(w*.047f);
   for(int i=0;i<4;i++){p.setColor(i==0?0xfff4edff:0xff9293a8);p.setTypeface(Typeface.create("sans-serif",i==0?Typeface.BOLD:Typeface.NORMAL));c.drawText(ellipsize(lines[i],35),center,ly+i*h*.046f,p);}
  }
  private String ellipsize(String t,int n){if(t==null)return "";return t.length()>n?t.substring(0,n-1)+"…":t;}
  private void loadCover(){long now=System.currentTimeMillis();if(now-lastCoverCheck<4000)return;lastCoverCheck=now;try{File f=new File(getFilesDir(),"spotify-cover.jpg");if(f.exists()){Bitmap b=BitmapFactory.decodeFile(f.getAbsolutePath());if(b!=null){if(cover!=null)cover.recycle();cover=b;}}}catch(Exception ignored){}}
 }
}
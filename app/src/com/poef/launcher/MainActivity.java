package com.poef.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.MediaMetadata;
import android.media.SoundPool;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
  static volatile MainActivity instance;
  final Handler idleHandler = new Handler();
  final Handler mediaHandler = new Handler();
  boolean leavingForApp = false;
  boolean inSaver = false;
  MediaSessionManager mediaSessions;
  MediaController spotifyController;
  volatile MediaSnapshot media = new MediaSnapshot(false,"","",0,0,null);
  static volatile MediaSnapshot sharedMedia = new MediaSnapshot(false,"","",0,0,null);
  final Runnable idleSaver = new Runnable(){ public void run(){ if(!leavingForApp && !inSaver){ inSaver=true; setContentView(new PoefView(MainActivity.this,true)); } } };
  final Runnable mediaPoll = new Runnable(){ public void run(){ updateMedia(); mediaHandler.postDelayed(this,700); } };
  static class MediaSnapshot {
    final boolean playing; final String title; final String artist; final long position; final long duration; final Bitmap art; final boolean airplayAudio;
    MediaSnapshot(boolean p,String t,String a,long pos,long dur,Bitmap b){this(p,t,a,pos,dur,b,false);}
    MediaSnapshot(boolean p,String t,String a,long pos,long dur,Bitmap b,boolean ap){playing=p;title=t;artist=a;position=pos;duration=dur;art=b;airplayAudio=ap;}
  }
  static volatile String weatherText = "Pretoria North  •  --°";
  static volatile String weatherTemp = "--°";
  static volatile int weatherCode = 0;
  static volatile boolean weatherDay = true;
  static volatile boolean weatherAvailable = false;
  static volatile boolean weatherStale = false;
  static void loadWeather(final PoefView target) {
    new Thread(new Runnable() { public void run() {
      HttpURLConnection h = null;
      try {
        URL u = new URL("https://api.open-meteo.com/v1/forecast?latitude=-25.67&longitude=28.20&current=temperature_2m,weather_code,is_day&timezone=Africa%2FJohannesburg");
        h = (HttpURLConnection)u.openConnection(); h.setConnectTimeout(5000); h.setReadTimeout(5000);
        BufferedReader r = new BufferedReader(new InputStreamReader(h.getInputStream())); StringBuilder b = new StringBuilder(); String line;
        while((line=r.readLine())!=null) b.append(line);
        Matcher m = Pattern.compile("\\\"temperature_2m\\\"\\s*:\\s*(-?[0-9.]+)").matcher(b.toString()); boolean gotTemp=m.find();
        if(gotTemp) { weatherTemp = Math.round(Float.parseFloat(m.group(1))) + "°"; weatherText = "Pretoria North  •  " + weatherTemp; }
        Matcher wc = Pattern.compile("\\\"weather_code\\\"\\s*:\\s*([0-9]+)").matcher(b.toString()); boolean gotCode=wc.find(); if(gotCode) weatherCode=Integer.parseInt(wc.group(1));
        Matcher wd = Pattern.compile("\\\"is_day\\\"\\s*:\\s*([0-9]+)").matcher(b.toString()); boolean gotDay=wd.find(); if(gotDay) weatherDay="1".equals(wd.group(1));
        boolean hadWeather=weatherAvailable; weatherAvailable=gotTemp&&gotCode&&gotDay; weatherStale=hadWeather&&!weatherAvailable;
      } catch(Exception ignored) { weatherStale=weatherAvailable; }
      target.postInvalidate();
      target.postDelayed(new Runnable(){ public void run(){ loadWeather(target); } },weatherAvailable?600000:30000);
      if(h!=null) h.disconnect();
    }}).start();
  }
  public void onCreate(Bundle b) { super.onCreate(b); instance=this; getWindow().setFlags(1024,1024); try{ startService(new Intent(this,AirplayGuard.class)); }catch(Exception ignored){} setContentView(new PoefView(this, false)); resetIdleSaver(); startMediaBridge(); }
  protected void onNewIntent(Intent intent) { super.onNewIntent(intent); if(intent!=null&&Intent.ACTION_MAIN.equals(intent.getAction())&&intent.hasCategory(Intent.CATEGORY_HOME)){ leavingForApp=false; inSaver=false; setContentView(new PoefView(this,false,false)); resetIdleSaver(); } }
  public boolean onKeyDown(int keyCode, android.view.KeyEvent event) { if(keyCode==android.view.KeyEvent.KEYCODE_HOME&&(inSaver||leavingForApp)){ leavingForApp=false; inSaver=false; setContentView(new PoefView(this,false,false)); resetIdleSaver(); return true; } return super.onKeyDown(keyCode,event); }
  void startMediaBridge(){ try{ mediaSessions=(MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE); }catch(Exception ignored){} mediaHandler.post(mediaPoll); }
  boolean sideplayAudioActive(){
    try{
      if(mediaSessions==null)return false;
      java.util.List<MediaController> sessions=mediaSessions.getActiveSessions(new ComponentName(this,PoefMediaListener.class));
      for(MediaController m:sessions) if("io.github.sudnep.sideplay".equals(m.getPackageName())){ PlaybackState s=m.getPlaybackState(); int st=s==null?PlaybackState.STATE_NONE:s.getState(); if(st==PlaybackState.STATE_PLAYING||st==PlaybackState.STATE_BUFFERING)return true; }
    }catch(Exception ignored){}
    return false;
  }
  boolean isSpotifyPackage(String pkg){ return pkg!=null && (pkg.startsWith("com.spotify.tv") || pkg.equals("com.spotify.music") || pkg.startsWith("com.spotify.")); }
  boolean isPlaying(MediaController m){ try{ PlaybackState s=m.getPlaybackState(); int st=s==null?PlaybackState.STATE_NONE:s.getState(); return st==PlaybackState.STATE_PLAYING||st==PlaybackState.STATE_BUFFERING; }catch(Exception ignored){ return false; } }
  void updateMedia(){
    MediaController spotifyCandidate=null, airplayCandidate=null;
    MediaController chosen=null;
    boolean airplayAudio=false;
    try{
      if(mediaSessions!=null){ java.util.List<MediaController> sessions=mediaSessions.getActiveSessions(new ComponentName(this,PoefMediaListener.class)); for(MediaController m:sessions){ if(isSpotifyPackage(m.getPackageName())){ if(spotifyCandidate==null) spotifyCandidate=m; } else if("io.github.sudnep.sideplay".equals(m.getPackageName()) && isPlaying(m)){ airplayCandidate=m; } } }
    }catch(Exception ignored){}
    if(spotifyCandidate!=null && isPlaying(spotifyCandidate)){ chosen=spotifyCandidate; }
    else if(airplayCandidate!=null){ chosen=airplayCandidate; airplayAudio=true; }
    spotifyController=chosen;
    if(chosen==null){ media=new MediaSnapshot(false,"","",0,0,null); sharedMedia=media; getWindow().getDecorView().postInvalidate(); return; }
    PlaybackState ps=chosen.getPlaybackState(); int state=ps==null?PlaybackState.STATE_NONE:ps.getState(); boolean playing=state==PlaybackState.STATE_PLAYING||state==PlaybackState.STATE_BUFFERING;
    MediaMetadata md=chosen.getMetadata(); String title=""; String artist=""; long duration=0; Bitmap art=null;
    if(md!=null){ title=md.getString(MediaMetadata.METADATA_KEY_TITLE); if(title==null||title.length()==0) title=md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE); artist=md.getString(MediaMetadata.METADATA_KEY_ARTIST); if(artist==null||artist.length()==0) artist=md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE); duration=md.getLong(MediaMetadata.METADATA_KEY_DURATION); art=md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART); if(art==null) art=md.getBitmap(MediaMetadata.METADATA_KEY_ART); if(art==null) art=md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON); }
    if(title==null||title.length()==0) title=airplayAudio?"AirPlay audio":"Spotify"; if(artist==null) artist=""; long position=ps==null?0:Math.max(0,ps.getPosition());
    media=new MediaSnapshot(playing,title,artist,position,duration,art,airplayAudio); sharedMedia=media; getWindow().getDecorView().postInvalidate();
  }
  void mediaPlayPause(){ if(spotifyController==null)return; PlaybackState s=spotifyController.getPlaybackState(); if(s!=null&&s.getState()==PlaybackState.STATE_PLAYING) spotifyController.getTransportControls().pause(); else spotifyController.getTransportControls().play(); }
  void mediaNext(){ if(spotifyController!=null)spotifyController.getTransportControls().skipToNext(); }
  void mediaPrevious(){ if(spotifyController!=null)spotifyController.getTransportControls().skipToPrevious(); }
  void resetIdleSaver(){ if(inSaver || leavingForApp) return; idleHandler.removeCallbacks(idleSaver); idleHandler.postDelayed(idleSaver,300000); }
  void exitIdleSaver(){ if(!inSaver) return; inSaver=false; setContentView(new PoefView(this,false,false)); resetIdleSaver(); }
  protected void onPause() { super.onPause(); }
  protected void onResume() { super.onResume(); leavingForApp=false; resetIdleSaver(); }
  protected void onDestroy() { if(instance==this)instance=null; idleHandler.removeCallbacks(idleSaver); mediaHandler.removeCallbacks(mediaPoll); super.onDestroy(); }
  static class PoefView extends View {
    Paint p = new Paint(3); Bitmap snoopy, snoopySaver; Drawable[] icons = new Drawable[5]; boolean dream;
    SoundPool focusSounds; int focusPluck, bootChime, focusStream;
    static Bitmap cachedSnoopy, cachedSnoopySaver;
    int selected = 0;
    boolean mediaFocused = false;
    int mediaControl = 1;
    float[] tileScale = {1f,1f,1f,1f,1f};
    long splashUntil;
    String marqueeText=""; long marqueeStart=0L;
    String[] names = {"Netflix","Spotify","YouTube","VLC","Settings"};
    String[] pkgs = {"com.netflix.ninja","com.spotify.tv.android","com.google.android.youtube.tv","org.videolan.vlc","com.android.tv.settings"};
    PoefView(final android.content.Context c, boolean d) { this(c,d,!d); }
    PoefView(final android.content.Context c, boolean d, boolean playBoot) { super(c); dream=d; splashUntil=d||!playBoot?0:SystemClock.uptimeMillis()+3800; AudioAttributes aa=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(); focusSounds=new SoundPool.Builder().setAudioAttributes(aa).setMaxStreams(2).build(); focusPluck=focusSounds.load(c,com.poef.launcher.R.raw.focus_pluck,1); if(!dream&&playBoot){ focusSounds.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener(){ public void onLoadComplete(SoundPool sp,int sampleId,int status){ if(status==0&&sampleId==bootChime) sp.play(sampleId,.58f,.58f,1,0,1.0f); } }); bootChime=focusSounds.load(c,com.poef.launcher.R.raw.boot_chime,1); } if(cachedSnoopy==null) cachedSnoopy=BitmapFactory.decodeResource(getResources(), com.poef.launcher.R.drawable.snoopy); if(cachedSnoopySaver==null) cachedSnoopySaver=BitmapFactory.decodeResource(getResources(), com.poef.launcher.R.drawable.snoopy_saver); snoopy=cachedSnoopy; snoopySaver=cachedSnoopySaver; for(int i=0;i<5;i++){ try{ icons[i]=c.getPackageManager().getApplicationIcon(pkgs[i]); }catch(Exception ignored){} } icons[4]=getResources().getDrawable(android.R.drawable.ic_menu_manage); setFocusable(true); setFocusableInTouchMode(true); requestFocus(); p.setTypeface(Typeface.create("sans",Typeface.NORMAL)); p.setAntiAlias(true); p.setFilterBitmap(true); p.setDither(true); p.setSubpixelText(true); loadWeather(this); }
    protected void onDraw(Canvas c) {
      int w=getWidth(), h=getHeight(); float s=w/640f; c.save(); c.scale(s,s); w=640; h=360;
      if (!dream && SystemClock.uptimeMillis() < splashUntil) { drawStartup(c); postInvalidateOnAnimation(); c.restore(); return; }
      c.drawColor(Color.rgb(19,24,29));
      Bitmap bg = dream && snoopySaver != null ? snoopySaver : snoopy;
      if (bg != null) { Rect src=new Rect(0,0,bg.getWidth(),bg.getHeight()); float scale=Math.max((float)w/bg.getWidth(),(float)h/bg.getHeight()); float dw=bg.getWidth()*scale, dh=bg.getHeight()*scale; RectF dst=new RectF((w-dw)*.5f,(h-dh)*.5f-(dream?0:35), (w+dw)*.5f,(h+dh)*.5f-(dream?0:35)); p.setAlpha(dream?255:150); c.drawBitmap(bg,src,dst,p); if(!dream && dst.bottom<h){ Rect tail=new Rect(0,Math.max(0,bg.getHeight()-24),bg.getWidth(),bg.getHeight()); c.drawBitmap(bg,tail,new RectF(0,Math.max(0,dst.bottom),w,h),p); } p.setAlpha(255); }
      p.setColor(Color.WHITE); p.setTypeface(Typeface.create("sans",Typeface.NORMAL));
      if (dream) { if(MainActivity.sharedMedia.playing) drawMediaBanner(c,MainActivity.sharedMedia,28,68,260,80,mediaFocused); drawWeatherBanner(c,28,164,134,60); drawClock(c,77,300,49); drawAirplay(c,145,300,.4f); c.restore(); postInvalidateDelayed(1000); return; }
      p.setTextAlign(Paint.Align.LEFT); p.setTextSize(22); c.drawText("Poef", 28, 40, p); p.setTextSize(11); p.setColor(Color.LTGRAY); String brand="Roebssie's TV OS"; c.drawText(brand,29,58,p); float brandEnd=29+p.measureText(brand); p.setTextSize(6); c.drawText("®",brandEnd+1,51,p);
      if(!MainActivity.sharedMedia.playing) mediaFocused=false;
      if(MainActivity.sharedMedia.playing) drawMediaBanner(c,MainActivity.sharedMedia,28,70,260,80,mediaFocused);
      drawWifi(c,366,72,11,selected==5); drawWeatherBanner(c,390,43,126,60); drawClock(c,570,72,48);
      p.setColor(Color.argb(178,175,174,178)); p.setShadowLayer(14,0,8,Color.argb(120,0,0,0)); c.drawRoundRect(new RectF(18,230,622,348),22,22,p); p.clearShadowLayer();
      boolean settling=false; int y=247; for(int i=0;i<5;i++){ int x=28+i*120; float target=i==selected?1.10f:1f; tileScale[i]+=(target-tileScale[i])*.20f; if(Math.abs(target-tileScale[i])>.002f) settling=true; float f=tileScale[i]; c.save(); c.scale(f,f,x+52,y+31); drawAppCard(c,x,y,i,i==selected); c.restore(); } if(settling) postInvalidateOnAnimation();
      c.restore();
    }
    void drawStartup(Canvas c){
      c.drawColor(Color.rgb(10,18,31));
      long elapsed = SystemClock.uptimeMillis(); float sweep = -160 + ((elapsed % 1500L) / 1500f) * 960f;
      p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create("sans",Typeface.NORMAL)); p.setTextSize(28); p.setColor(Color.WHITE); p.setShader(null);
      String bootBrand="Roebssie's TV OS"; c.drawText(bootBrand,320,176,p); float bootEnd=320+p.measureText(bootBrand)/2f; p.setTextSize(7); c.drawText("®",bootEnd+2,168,p);
      p.setTextSize(9); p.setColor(Color.rgb(190,210,230)); c.drawText("Preparing your TV experience",320,196,p);
      p.setShader(new LinearGradient(sweep-90,0,sweep+90,0,Color.TRANSPARENT,Color.WHITE,Shader.TileMode.CLAMP)); p.setTextSize(28); c.drawText(bootBrand,320,176,p); p.setShader(null); p.setTextSize(7); c.drawText("®",bootEnd+2,168,p); p.setTextAlign(Paint.Align.LEFT);
    }
    void drawAppCard(Canvas c,int x,int y,int i,boolean focus){
      p.setStyle(Paint.Style.FILL); p.setShader(null); p.setShadowLayer(focus?12:4,0,6,Color.argb(focus?190:100,0,0,0));
      int tile=(i==3)?Color.rgb(246,126,18):Color.rgb(248,248,248);
      p.setShader(null); p.setColor(tile); p.setShadowLayer(focus?14:6,0,7,Color.argb(focus?170:100,0,0,0));
      c.drawRoundRect(new RectF(x,y,x+104,y+62),9,9,p); p.clearShadowLayer();
      if(i==0){ p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD)); p.setTextSize(18); p.setColor(Color.rgb(205,18,25)); c.drawText("NETFLIX",x+52,y+36,p); }
      else if(i==1){ p.setColor(Color.rgb(18,16,18)); p.setStyle(Paint.Style.FILL); c.drawRoundRect(new RectF(x,y,x+104,y+62),9,9,p); p.setColor(Color.WHITE); c.drawCircle(x+21,y+31,16.5f,p); p.setColor(Color.rgb(18,16,18)); p.setStyle(Paint.Style.STROKE); p.setAntiAlias(true); p.setStrokeWidth(2.7f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); Path a=new Path(); a.moveTo(x+9,y+27); a.quadTo(x+21,y+20.3f,x+33,y+27); c.drawPath(a,p); Path b=new Path(); b.moveTo(x+10,y+33); b.quadTo(x+21,y+26.5f,x+32,y+33); c.drawPath(b,p); Path d=new Path(); d.moveTo(x+12,y+39); d.quadTo(x+21,y+33.4f,x+31,y+39); c.drawPath(d,p); p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.LEFT); p.setTypeface(Typeface.create("sans",Typeface.BOLD)); p.setTextSize(15); p.setColor(Color.WHITE); c.drawText("Spotify",x+40,y+37,p); p.setTextSize(4.5f); c.drawText("®",x+94,y+25,p); }
      else if(i==2){ p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(235,0,0)); c.drawRoundRect(new RectF(x+12,y+21,x+39,y+41),5,5,p); Path play=new Path(); play.moveTo(x+23,y+25); play.lineTo(x+23,y+37); play.lineTo(x+32,y+31); play.close(); p.setColor(Color.WHITE); c.drawPath(play,p); p.setTextAlign(Paint.Align.LEFT); p.setTypeface(Typeface.create("sans",Typeface.BOLD)); p.setTextSize(12); p.setColor(Color.rgb(20,20,20)); c.drawText("YouTube",x+43,y+35,p); }
      else if(i==3){ p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE); Path cone=new Path(); cone.moveTo(x+52,y+9); cone.lineTo(x+38,y+48); cone.quadTo(x+52,y+53,x+66,y+48); cone.close(); c.drawPath(cone,p); p.setColor(Color.rgb(246,126,18)); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); c.drawLine(x+44,y+25,x+60,y+28,p); c.drawLine(x+41,y+34,x+63,y+37,p); p.setStyle(Paint.Style.FILL); c.drawRoundRect(new RectF(x+34,y+48,x+70,y+54),3,3,p); }
      else if(i==4){ float gx=x+52, gy=y+31; p.setColor(Color.rgb(125,125,128)); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.SQUARE); p.setStrokeWidth(3); for(int k=0;k<12;k++){ double a=k*Math.PI/6; c.drawLine(gx+(float)Math.cos(a)*14,gy+(float)Math.sin(a)*14,gx+(float)Math.cos(a)*18,gy+(float)Math.sin(a)*18,p); } p.setStrokeWidth(4); c.drawCircle(gx,gy,14,p); p.setStrokeWidth(3); c.drawCircle(gx,gy,8,p); for(int k=0;k<3;k++){ double a=k*2*Math.PI/3-Math.PI/2; c.drawLine(gx,gy,gx+(float)Math.cos(a)*7,gy+(float)Math.sin(a)*7,p); } p.setStyle(Paint.Style.FILL); c.drawCircle(gx,gy,3,p); }
      else if(icons[i]!=null){ icons[i].setBounds(x+28,y+6,x+76,y+54); icons[i].draw(c); }
      if(focus){p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans",Typeface.NORMAL));p.setTextSize(10);p.setColor(Color.WHITE);c.drawText(names[i],x+52,y+78,p);p.setTextAlign(Paint.Align.LEFT);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(Color.WHITE);c.drawRoundRect(new RectF(x+1,y+1,x+103,y+61),8,8,p);p.setStyle(Paint.Style.FILL);}
    }
    void drawWeatherIcon(Canvas c,float x,float y,float r){
      if(!weatherAvailable){
        Path cloud=new Path(); cloud.moveTo(x-r*.68f,y+r*.20f); cloud.quadTo(x-r*.65f,y-r*.12f,x-r*.36f,y-r*.08f); cloud.quadTo(x-r*.22f,y-r*.57f,x+r*.10f,y-r*.43f); cloud.quadTo(x+r*.35f,y-r*.48f,x+r*.40f,y-r*.08f); cloud.quadTo(x+r*.68f,y-r*.12f,x+r*.66f,y+r*.20f); cloud.lineTo(x+r*.66f,y+r*.30f); cloud.lineTo(x-r*.68f,y+r*.30f); cloud.close();
        p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(224,239,247)); c.drawPath(cloud,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(Math.max(1.6f,r*.16f)); p.setColor(Color.rgb(100,120,134)); c.drawPath(cloud,p);
        p.setStyle(Paint.Style.FILL); return;
      }
      boolean clear=weatherCode<=1;
      boolean partly=weatherCode==2||weatherCode==3;
      boolean moon=clear&&!weatherDay;
      boolean rain=(weatherCode>=51&&weatherCode<=67)||(weatherCode>=80&&weatherCode<=82);
      boolean snow=weatherCode>=71&&weatherCode<=77;
      boolean storm=weatherCode>=95;
      float k=Math.max(0.7f,r/11f);
      int outline=Color.rgb(67,94,116);
      int cloudFill=Color.rgb(224,239,247);
      if(clear && !moon || partly){
        p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(250,209,63)); c.drawCircle(x-r*.18f,y-r*.16f,r*.48f,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(Math.max(1.4f,1.9f*k)); p.setColor(outline);
        for(int i=0;i<8;i++){ double a=i*Math.PI/4; c.drawLine(x-r*.18f+(float)Math.cos(a)*r*.66f,y-r*.16f+(float)Math.sin(a)*r*.66f,x-r*.18f+(float)Math.cos(a)*r*.90f,y-r*.16f+(float)Math.sin(a)*r*.90f,p); }
      } else if(moon){
        p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(241,223,145));
        Path crescent=new Path(); crescent.addCircle(x-r*.08f,y-r*.08f,r*.64f,Path.Direction.CW);
        Path cut=new Path(); cut.addCircle(x+r*.20f,y-r*.28f,r*.58f,Path.Direction.CW);
        crescent.op(cut,Path.Op.DIFFERENCE); c.drawPath(crescent,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1.2f,1.6f*k)); p.setColor(Color.rgb(67,94,116)); c.drawPath(crescent,p);
      }
      if(!clear || partly || rain || snow || storm){
        Path cloud=new Path(); cloud.moveTo(x-r*.68f,y+r*.20f); cloud.quadTo(x-r*.65f,y-r*.12f,x-r*.36f,y-r*.08f); cloud.quadTo(x-r*.22f,y-r*.57f,x+r*.10f,y-r*.43f); cloud.quadTo(x+r*.35f,y-r*.48f,x+r*.40f,y-r*.08f); cloud.quadTo(x+r*.68f,y-r*.12f,x+r*.66f,y+r*.20f); cloud.lineTo(x+r*.66f,y+r*.30f); cloud.lineTo(x-r*.68f,y+r*.30f); cloud.close();
        p.setStyle(Paint.Style.FILL); p.setColor(cloudFill); c.drawPath(cloud,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(Math.max(1.6f,2.1f*k)); p.setColor(outline); c.drawPath(cloud,p);
        if(storm){
          p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(255,205,43)); Path bolt=new Path(); bolt.moveTo(x+r*.08f,y+r*.28f); bolt.lineTo(x-r*.12f,y+r*.72f); bolt.lineTo(x+r*.06f,y+r*.68f); bolt.lineTo(x-r*.02f,y+r*1.06f); bolt.lineTo(x+r*.30f,y+r*.52f); bolt.lineTo(x+r*.10f,y+r*.56f); bolt.close(); c.drawPath(bolt,p);
        } else if(rain){
          p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1.2f,1.6f*k)); p.setColor(Color.rgb(71,155,201)); for(int i=-1;i<=1;i++) c.drawLine(x+i*r*.32f,y+r*.48f,x+i*r*.32f-r*.10f,y+r*.84f,p);
        } else if(snow){
          p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE); for(int i=-1;i<=1;i++) c.drawCircle(x+i*r*.30f,y+r*.65f,r*.08f,p);
        }
      }
      p.setStyle(Paint.Style.FILL); p.setStrokeCap(Paint.Cap.BUTT);
    }
    void drawWifi(Canvas c,float x,float y,float r,boolean focus){
      drawBubble(c,new RectF(x-18,y-18,x+18,y+18),9,150);
      p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(Math.max(1.8f,r*.22f)); p.setColor(focus?Color.WHITE:Color.rgb(38,45,54));
      RectF a=new RectF(x-r*1.25f,y-r*1.02f,x+r*1.25f,y+r*1.10f); c.drawArc(a,225,90,false,p);
      RectF b=new RectF(x-r*.78f,y-r*.52f,x+r*.78f,y+r*.76f); c.drawArc(b,225,90,false,p);
      p.setStyle(Paint.Style.FILL); c.drawCircle(x,y+r*.30f,r*.19f,p);
      p.setStrokeCap(Paint.Cap.BUTT);
    }
    void drawAirplay(Canvas c,float x,float y,float scale){
      p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setStrokeWidth(3.2f*scale); p.setColor(Color.rgb(38,45,54)); p.setShadowLayer(3,0,1,Color.argb(70,0,0,0));
      c.drawRoundRect(new RectF(x-18*scale,y-15*scale,x+18*scale,y+8*scale),5*scale,5*scale,p);
      p.setStyle(Paint.Style.FILL); Path a=new Path(); a.moveTo(x,y-1*scale); a.lineTo(x-11*scale,y+16*scale); a.quadTo(x-12*scale,y+18*scale,x-9*scale,y+18*scale); a.lineTo(x+9*scale,y+18*scale); a.quadTo(x+12*scale,y+18*scale,x+11*scale,y+16*scale); a.close(); c.drawPath(a,p); p.clearShadowLayer(); p.setStrokeCap(Paint.Cap.BUTT); p.setStrokeJoin(Paint.Join.MITER);
    }
    void drawWeatherBanner(Canvas c,float x,float y,float w,float h){
      drawBubble(c,new RectF(x,y,x+w,y+h),14,150);
      drawWeatherIcon(c,x+22,y+29,11);
      p.setTypeface(Typeface.create("sans",Typeface.NORMAL)); p.setTextAlign(Paint.Align.LEFT); p.setColor(Color.rgb(38,45,54)); p.setTextSize(9); c.drawText("Pretoria North",x+40,y+21,p); p.setTextSize(19); p.setTypeface(Typeface.create("sans",Typeface.BOLD)); c.drawText(weatherAvailable?weatherTemp:"--°",x+40,y+42,p);
      p.setTypeface(Typeface.create("sans",Typeface.NORMAL)); p.setLetterSpacing(.045f); p.setTextSize(8); p.setColor(Color.rgb(82,88,95)); c.drawText(weatherAvailable?(weatherStale?"stale":"now"):"unavailable",x+40,y+55,p); p.setLetterSpacing(0f);
    }
    void drawMediaBanner(Canvas c,MediaSnapshot m,float x,float y,float w,float h,boolean focus){
      drawBubble(c,new RectF(x,y,x+w,y+h),14,150);
      if(m.airplayAudio){
        // Audio-only AirPlay uses the same banner size and glass treatment as
        // Spotify, but contains only the AirPlay mark and its label.
        drawAirplay(c,x+w*.38f,y+h*.50f,.58f);
        p.setTextAlign(Paint.Align.LEFT); p.setTypeface(Typeface.create("sans",Typeface.NORMAL)); p.setTextSize(10); p.setColor(Color.rgb(38,45,54)); c.drawText("AirPlay audio",x+w*.47f,y+h*.56f,p);
        p.setTextAlign(Paint.Align.LEFT);
        return;
      }
      RectF artRect=new RectF(x+8,y+8,x+72,y+h-8);
      if(m.art!=null){ Rect src=new Rect(0,0,m.art.getWidth(),m.art.getHeight()); float side=Math.min(m.art.getWidth(),m.art.getHeight()); int left=(int)((m.art.getWidth()-side)/2), top=(int)((m.art.getHeight()-side)/2); src.set(left,top,left+(int)side,top+(int)side); Path clip=new Path(); clip.addRoundRect(artRect,10,10,Path.Direction.CW); c.save(); c.clipPath(clip); c.drawBitmap(m.art,src,artRect,p); c.restore(); } else { p.setColor(Color.rgb(35,42,48)); c.drawRoundRect(artRect,10,10,p); p.setColor(Color.rgb(33,198,93)); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); c.drawCircle(x+39,y+39,13,p); c.drawArc(new RectF(x+28,y+25,x+50,y+47),205,130,false,p); c.drawArc(new RectF(x+31,y+31,x+47,y+47),205,130,false,p); p.setStyle(Paint.Style.FILL); }
      p.setTextAlign(Paint.Align.LEFT); p.setTypeface(Typeface.create("sans",Typeface.BOLD)); p.setTextSize(11); p.setColor(Color.rgb(38,45,54)); drawScrollingText(c,m.title,x+84,y+22,w-96); p.setTypeface(Typeface.create("sans",Typeface.NORMAL)); p.setTextSize(8); p.setColor(Color.rgb(82,88,95)); c.drawText(shortText(m.artist,28),x+84,y+36,p);
      float trackW=w-106; float progress=m.duration>0?Math.min(1f,(float)m.position/(float)m.duration):0f; p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(2); p.setColor(Color.argb(100,38,45,54)); c.drawLine(x+84,y+48,x+84+trackW,y+48,p); p.setColor(Color.rgb(38,45,54)); c.drawLine(x+84,y+48,x+84+trackW*progress,y+48,p); c.drawCircle(x+84+trackW*progress,y+48,2.5f,p); p.setTypeface(Typeface.create("sans",Typeface.NORMAL)); p.setTextSize(6); p.setColor(Color.rgb(82,88,95)); c.drawText(formatTime(m.position),x+84,y+57,p); p.setTextAlign(Paint.Align.RIGHT); c.drawText(formatTime(m.duration),x+84+trackW,y+57,p); p.setTextAlign(Paint.Align.LEFT);
      float fx=mediaControl==0?x+106:(mediaControl==1?x+146:x+186);
      if(focus){ p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(55,55,61,68)); p.setShadowLayer(7,0,2,Color.argb(110,55,61,68)); c.drawCircle(fx,y+69,15,p); p.clearShadowLayer(); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f); p.setColor(Color.argb(105,75,82,90)); c.drawRoundRect(new RectF(x+1,y+1,x+w-1,y+h-1),14,14,p); p.setStyle(Paint.Style.FILL); }
      p.setColor(Color.rgb(38,45,54)); drawMediaPrev(c,x+106,y+69); drawMediaPlay(c,x+146,y+69,m.playing); drawMediaNext(c,x+186,y+69);
    }
    String formatTime(long ms){ if(ms<=0)return "0:00"; long sec=ms/1000; return (sec/60)+":"+String.format(java.util.Locale.US,"%02d",sec%60); }
    String shortText(String s,int max){ if(s==null)return ""; return s.length()>max?s.substring(0,max-1)+"…":s; }
    void drawScrollingText(Canvas c,String text,float x,float baseline,float maxWidth){
      if(text==null)text=""; float tw=p.measureText(text); if(tw<=maxWidth){ c.drawText(text,x,baseline,p); return; }
      if(!text.equals(marqueeText)){ marqueeText=text; marqueeStart=SystemClock.uptimeMillis(); }
      long elapsed=(SystemClock.uptimeMillis()-marqueeStart)%30000L; float travel=tw+maxWidth+36; float drawX=x+maxWidth-(elapsed/30000f)*travel;
      c.save(); c.clipRect(x,baseline-14,x+maxWidth,baseline+4); c.drawText(text,drawX,baseline,p); c.drawText(text,drawX+tw+36,baseline,p); c.restore(); postInvalidateOnAnimation();
    }
    void drawMediaPrev(Canvas c,float x,float y){ Path t=new Path(); t.moveTo(x+5,y-6);t.lineTo(x-1,y);t.lineTo(x+5,y+6);t.close();c.drawPath(t,p);t=new Path();t.moveTo(x+12,y-6);t.lineTo(x+6,y);t.lineTo(x+12,y+6);t.close();c.drawPath(t,p);c.drawRect(x-4,y-7,x-1,y+7,p); }
    void drawMediaNext(Canvas c,float x,float y){ Path t=new Path(); t.moveTo(x-5,y-6);t.lineTo(x+1,y);t.lineTo(x-5,y+6);t.close();c.drawPath(t,p);t=new Path();t.moveTo(x-12,y-6);t.lineTo(x-6,y);t.lineTo(x-12,y+6);t.close();c.drawPath(t,p);c.drawRect(x+1,y-7,x+4,y+7,p); }
    void drawMediaPlay(Canvas c,float x,float y,boolean playing){ if(playing){c.drawRect(x-4,y-7,x-1,y+7,p);c.drawRect(x+2,y-7,x+5,y+7,p);}else{Path t=new Path();t.moveTo(x-4,y-8);t.lineTo(x+7,y);t.lineTo(x-4,y+8);t.close();c.drawPath(t,p);} }
    void drawClock(Canvas c,float x,float y,float r){
      drawBubble(c,new RectF(x-r,y-r,x+r,y+r),r*.24f,218);
      p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); Calendar q=Calendar.getInstance();
      for(int i=0;i<60;i++){ double a=i*Math.PI/30-Math.PI/2; boolean major=i%5==0; p.setStrokeWidth(major?Math.max(2,r*.035f):Math.max(1,r*.018f)); p.setColor(major?Color.rgb(65,65,68):Color.rgb(190,190,193)); float inner=r*(major?.76f:.82f); c.drawLine(x+(float)Math.cos(a)*inner,y+(float)Math.sin(a)*inner,x+(float)Math.cos(a)*r*.88f,y+(float)Math.sin(a)*r*.88f,p); }
      p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(55,55,58)); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create("sans",Typeface.NORMAL)); p.setTextSize(r*.38f); c.drawText("12",x,y-r*.48f,p); c.drawText("3",x+r*.49f,y+r*.10f,p); c.drawText("6",x,y+r*.56f,p); c.drawText("9",x-r*.49f,y+r*.10f,p);
      double ah=(q.get(Calendar.HOUR)%12+q.get(Calendar.MINUTE)/60.0)*Math.PI/6-Math.PI/2; double am=(q.get(Calendar.MINUTE)+q.get(Calendar.SECOND)/60.0)*Math.PI/30-Math.PI/2; double as=q.get(Calendar.SECOND)*Math.PI/30-Math.PI/2;
      p.setColor(Color.rgb(30,30,32)); p.setStrokeWidth(Math.max(5,r*.095f)); c.drawLine(x,y,x+(float)Math.cos(ah)*r*.48f,y+(float)Math.sin(ah)*r*.48f,p); p.setStrokeWidth(Math.max(4,r*.07f)); c.drawLine(x,y,x+(float)Math.cos(am)*r*.72f,y+(float)Math.sin(am)*r*.72f,p);
      p.setColor(Color.rgb(255,145,0)); p.setStrokeWidth(Math.max(2,r*.025f)); c.drawLine(x-(float)Math.cos(as)*r*.16f,y-(float)Math.sin(as)*r*.16f,x+(float)Math.cos(as)*r*.80f,y+(float)Math.sin(as)*r*.80f,p); p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE); c.drawCircle(x,y,r*.07f,p); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2,r*.025f)); p.setColor(Color.rgb(255,145,0)); c.drawCircle(x,y,r*.07f,p); p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.LEFT); postInvalidateDelayed(1000);
    }
    void drawBubble(Canvas c,RectF bounds,float radius,int alpha){
      p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(alpha,255,255,255)); p.setShadowLayer(8,0,3,Color.argb(72,0,0,0)); c.drawRoundRect(bounds,radius,radius,p); p.clearShadowLayer();
    }
    void playFocus(){ if(focusSounds!=null&&focusPluck!=0){ if(focusStream!=0) focusSounds.stop(focusStream); focusStream=focusSounds.play(focusPluck,0.42f,0.42f,1,0,1.0f); } }
    void launch(int i){ try{ ((MainActivity)getContext()).leavingForApp=true; if(i==5){ Intent s=new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS); s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP); getContext().startActivity(s); } else if(i==4){ Intent s=new Intent(android.provider.Settings.ACTION_SETTINGS); s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP); getContext().startActivity(s); } else getContext().startActivity(getContext().getPackageManager().getLaunchIntentForPackage(pkgs[i])); ((Activity)getContext()).overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); }catch(Exception z){} }
    public boolean onKeyDown(int key, android.view.KeyEvent e){
      if(dream){
        if(key==android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){ if(MainActivity.instance!=null)MainActivity.instance.mediaPlayPause(); return true; }
        if(key==android.view.KeyEvent.KEYCODE_MEDIA_NEXT){ if(MainActivity.instance!=null)MainActivity.instance.mediaNext(); return true; }
        if(key==android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS){ if(MainActivity.instance!=null)MainActivity.instance.mediaPrevious(); return true; }
        if(key==android.view.KeyEvent.KEYCODE_VOLUME_UP || key==android.view.KeyEvent.KEYCODE_VOLUME_DOWN || key==android.view.KeyEvent.KEYCODE_VOLUME_MUTE) return false;
        try{ if(getContext() instanceof MainActivity) ((MainActivity)getContext()).exitIdleSaver(); else ((android.service.dreams.DreamService)getContext()).finish(); }catch(Exception ignored){}
        return true;
      }
      ((MainActivity)getContext()).resetIdleSaver();
      if(key==android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){((MainActivity)getContext()).mediaPlayPause();return true;} if(key==android.view.KeyEvent.KEYCODE_MEDIA_NEXT){((MainActivity)getContext()).mediaNext();return true;} if(key==android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS){((MainActivity)getContext()).mediaPrevious();return true;}
      if(key==android.view.KeyEvent.KEYCODE_BACK) return true;
      if(MainActivity.sharedMedia.playing && mediaFocused){
        if(key==android.view.KeyEvent.KEYCODE_DPAD_LEFT){mediaControl=Math.max(0,mediaControl-1);invalidate();playFocus();return true;}
        if(key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT){mediaControl=Math.min(2,mediaControl+1);invalidate();playFocus();return true;}
        if(key==android.view.KeyEvent.KEYCODE_DPAD_UP){mediaFocused=false;selected=5;invalidate();playFocus();return true;}
        if(key==android.view.KeyEvent.KEYCODE_DPAD_DOWN){mediaFocused=false;selected=0;invalidate();playFocus();return true;}
        if(key==android.view.KeyEvent.KEYCODE_DPAD_CENTER||key==android.view.KeyEvent.KEYCODE_ENTER){if(mediaControl==0)((MainActivity)getContext()).mediaPrevious();else if(mediaControl==1)((MainActivity)getContext()).mediaPlayPause();else ((MainActivity)getContext()).mediaNext();return true;}
        return true;
      }
      if(MainActivity.sharedMedia.playing && key==android.view.KeyEvent.KEYCODE_DPAD_UP && selected!=5){mediaFocused=true;mediaControl=1;invalidate();playFocus();return true;}
      if(key==android.view.KeyEvent.KEYCODE_DPAD_UP){if(selected!=5){selected=5;playFocus();invalidate();}return true;} if(key==android.view.KeyEvent.KEYCODE_DPAD_DOWN){if(selected==5){selected=0;playFocus();invalidate();}return true;} if(key==android.view.KeyEvent.KEYCODE_DPAD_LEFT){int old=selected; selected=selected==5?4:Math.max(0,selected-1); if(selected!=old) playFocus(); invalidate();return true;} if(key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT){int old=selected; selected=selected==5?5:Math.min(4,selected+1); if(selected!=old) playFocus(); invalidate();return true;} if(key==android.view.KeyEvent.KEYCODE_DPAD_CENTER||key==android.view.KeyEvent.KEYCODE_ENTER){launch(selected); return true;} return super.onKeyDown(key,e); }
    public boolean onTouchEvent(android.view.MotionEvent e){ float s=getWidth()/640f, x=e.getX()/s, y=e.getY()/s; if(dream){ if(MainActivity.sharedMedia.playing && x>=28&&x<=288&&y>=68&&y<=148 && e.getAction()==android.view.MotionEvent.ACTION_UP && MainActivity.instance!=null){ if(y>=117){ if(x<150)MainActivity.instance.mediaPrevious(); else if(x<190)MainActivity.instance.mediaPlayPause(); else MainActivity.instance.mediaNext(); } return true; } if(e.getAction()==android.view.MotionEvent.ACTION_DOWN) try{if(getContext() instanceof MainActivity) ((MainActivity)getContext()).exitIdleSaver(); else ((android.service.dreams.DreamService)getContext()).finish();}catch(Exception ignored){} return true; } if(e.getAction()==android.view.MotionEvent.ACTION_DOWN) ((MainActivity)getContext()).resetIdleSaver(); if(MainActivity.sharedMedia.playing && x>=28&&x<=288&&y>=70&&y<=150 && e.getAction()==android.view.MotionEvent.ACTION_UP){ if(y>=120){ if(x<150){mediaFocused=true;mediaControl=0;((MainActivity)getContext()).mediaPrevious();} else if(x<190){mediaFocused=true;mediaControl=1;((MainActivity)getContext()).mediaPlayPause();} else {mediaFocused=true;mediaControl=2;((MainActivity)getContext()).mediaNext();} invalidate(); } return true; } if(x>=375&&x<=425&&y>=50&&y<=105){if(e.getAction()==android.view.MotionEvent.ACTION_DOWN){if(selected!=5)playFocus();selected=5;invalidate();return true;} if(e.getAction()==android.view.MotionEvent.ACTION_UP){launch(5);return true;}} if(y>=225&&y<=348){int i=(int)((x-28)/120); if(i>=0&&i<5){if(e.getAction()==android.view.MotionEvent.ACTION_DOWN){if(selected!=i) playFocus(); selected=i;invalidate();return true;} if(e.getAction()==android.view.MotionEvent.ACTION_UP){launch(i);return true;}} } return true; }
  }
}

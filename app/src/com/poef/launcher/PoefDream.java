package com.poef.launcher;
import android.service.dreams.DreamService;
public class PoefDream extends DreamService {
  public static volatile boolean active = false;
  public void onAttachedToWindow(){ super.onAttachedToWindow(); active=true; setFullscreen(true); setContentView(new MainActivity.PoefView(this,true)); }
  public void onDetachedFromWindow(){ active=false; super.onDetachedFromWindow(); }
}

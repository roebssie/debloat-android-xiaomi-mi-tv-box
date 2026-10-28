package com.poef.launcher;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;

public class AirplayGuard extends Service {
  final Handler handler = new Handler();
  // Keep the receiver alive without stealing a genuine mirror surface.
  // Audio/menu suppression is handled by AirplayAccessibility, which can
  // inspect the window tree and leave a video-only SurfaceView alone.
  final Runnable check = new Runnable(){ public void run(){ handler.postDelayed(this,250); }};
  public void onCreate(){ super.onCreate(); handler.post(check); }
  public int onStartCommand(Intent intent,int flags,int startId){ return START_STICKY; }
  public void onDestroy(){ handler.removeCallbacks(check); super.onDestroy(); }
  public IBinder onBind(Intent intent){ return null; }
}

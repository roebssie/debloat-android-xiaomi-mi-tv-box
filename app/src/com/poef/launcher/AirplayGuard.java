package com.poef.launcher;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;

public class AirplayGuard extends Service {
  final Handler handler = new Handler();
  final Runnable check = new Runnable(){ public void run(){
    try {
      ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
      java.util.List<ActivityManager.RunningTaskInfo> tasks=am.getRunningTasks(1);
      if(!tasks.isEmpty() && tasks.get(0).topActivity!=null && "io.github.sudnep.sideplay".equals(tasks.get(0).topActivity.getPackageName())){
        Intent home=new Intent(Intent.ACTION_MAIN); home.addCategory(Intent.CATEGORY_HOME); home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION); startActivity(home);
      }
    } catch(Exception ignored){}
    handler.postDelayed(this,20);
  }};
  public void onCreate(){ super.onCreate(); handler.post(check); }
  public int onStartCommand(Intent intent,int flags,int startId){ return START_STICKY; }
  public void onDestroy(){ handler.removeCallbacks(check); super.onDestroy(); }
  public IBinder onBind(Intent intent){ return null; }
}

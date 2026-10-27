package com.poef.launcher;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

public class BootRelay extends BroadcastReceiver {
  public void onReceive(Context context, Intent intent) {
    // Boot directly into Poef Home. Never launch Wi-Fi settings or Sideplay's
    // visible activity; Sideplay remains responsible for its background service.
    Intent home=new Intent(context,MainActivity.class);
    home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    context.startActivity(home);
  }
}

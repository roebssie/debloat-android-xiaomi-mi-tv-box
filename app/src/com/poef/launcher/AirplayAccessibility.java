package com.poef.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

public class AirplayAccessibility extends AccessibilityService {
  public void onAccessibilityEvent(AccessibilityEvent event){
    if(event==null || event.getPackageName()==null) return;
    if("io.github.sudnep.sideplay".contentEquals(event.getPackageName())) {
      Intent home=new Intent(Intent.ACTION_MAIN);
      home.addCategory(Intent.CATEGORY_HOME);
      home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION);
      try { startActivity(home); } catch(Exception ignored) {}
      performGlobalAction(GLOBAL_ACTION_HOME);
    }
  }
  public void onInterrupt(){}
}

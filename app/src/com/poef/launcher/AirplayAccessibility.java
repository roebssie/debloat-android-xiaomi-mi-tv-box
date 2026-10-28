package com.poef.launcher;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class AirplayAccessibility extends AccessibilityService {
  public void onAccessibilityEvent(AccessibilityEvent event){
    if(event==null || event.getPackageName()==null) return;
    if("io.github.sudnep.sideplay".contentEquals(event.getPackageName())) performGlobalAction(GLOBAL_ACTION_HOME);
  }
  public void onInterrupt(){}
}

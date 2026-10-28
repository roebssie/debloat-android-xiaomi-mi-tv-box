package com.poef.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityEvent;

public class AirplayAccessibility extends AccessibilityService {
  public void onAccessibilityEvent(AccessibilityEvent event){
    if(event==null || event.getPackageName()==null) return;
    if("io.github.sudnep.sideplay".contentEquals(event.getPackageName()) && hasMenuContent(getRootInActiveWindow())) {
      Intent home=new Intent(Intent.ACTION_MAIN);
      home.addCategory(Intent.CATEGORY_HOME);
      home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION);
      try { startActivity(home); } catch(Exception ignored) {}
      performGlobalAction(GLOBAL_ACTION_HOME);
    }
  }
  boolean hasMenuContent(AccessibilityNodeInfo node){
    if(node==null)return false;
    CharSequence text=node.getText();
    CharSequence desc=node.getContentDescription();
    if((text!=null&&text.length()>0)||(desc!=null&&desc.length()>0))return true;
    for(int i=0;i<node.getChildCount();i++) if(hasMenuContent(node.getChild(i))) return true;
    return false;
  }
  public void onInterrupt(){}
}

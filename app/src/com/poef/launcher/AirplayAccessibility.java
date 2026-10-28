package com.poef.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.graphics.PixelFormat;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityEvent;

public class AirplayAccessibility extends AccessibilityService {
  private static final String SIDEPLAY = "io.github.sudnep.sideplay";
  private static final long MENU_CHECK_DELAY_MS = 40L;
  private static final long MENU_CHECK_WINDOW_MS = 1800L;
  private static final long DISMISS_COOLDOWN_MS = 200L;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private long lastDismissAt;
  private long menuDeadline;
  private WindowManager windowManager;
  private HomeMask homeMask;
  private final Runnable menuCheck = new Runnable(){ public void run(){ dismissIdleMenuIfPresent(); }};

  public void onAccessibilityEvent(AccessibilityEvent event){
    if(event==null || event.getPackageName()==null) return;
    if(!SIDEPLAY.contentEquals(event.getPackageName())) return;
    int type=event.getEventType();
    if(type!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
       type!=AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
       type!=AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return;
    activateHomeMask();
    menuDeadline=SystemClock.uptimeMillis()+MENU_CHECK_WINDOW_MS;
    handler.removeCallbacks(menuCheck);
    handler.postDelayed(menuCheck, MENU_CHECK_DELAY_MS);
  }

  private void dismissIdleMenuIfPresent(){
    long now=SystemClock.uptimeMillis();
    if(now-lastDismissAt < DISMISS_COOLDOWN_MS) return;
    AccessibilityNodeInfo root=activeRoot();
    if(root==null){ if(now<menuDeadline) handler.postDelayed(menuCheck,MENU_CHECK_DELAY_MS); else deactivateHomeMask(); return; }
    if(containsVideoSurface(root)){ deactivateHomeMask(); return; }
    if(!hasReceiverLabel(root)){
      if(now<menuDeadline) handler.postDelayed(menuCheck,MENU_CHECK_DELAY_MS);
      else deactivateHomeMask();
      return;
    }
    if(!sideplayAudioActive()){
      if(now<menuDeadline) handler.postDelayed(menuCheck,MENU_CHECK_DELAY_MS);
      else {
        deactivateHomeMask();
        lastDismissAt=now;
        goHome();
      }
      return;
    }
    deactivateHomeMask();
    lastDismissAt=now;
    goHome();
  }

  void goHome(){
    Intent home=new Intent(Intent.ACTION_MAIN);
    home.addCategory(Intent.CATEGORY_HOME);
    home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION);
    try { startActivity(home); } catch(Exception ignored) {}
    performGlobalAction(GLOBAL_ACTION_HOME);
  }

  boolean isDismissibleMenu(AccessibilityNodeInfo root){
    if(root==null || containsVideoSurface(root)) return false;
    return hasReceiverLabel(root) && hasInteractiveControl(root);
  }

  AccessibilityNodeInfo activeRoot(){
    AccessibilityNodeInfo root=getRootInActiveWindow();
    if(root!=null && SIDEPLAY.contentEquals(root.getPackageName())) return root;
    try{
      java.util.List<android.view.accessibility.AccessibilityWindowInfo> windows=getWindows();
      if(windows!=null) for(android.view.accessibility.AccessibilityWindowInfo window:windows){
        AccessibilityNodeInfo candidate=window.getRoot();
        if(candidate!=null && SIDEPLAY.contentEquals(candidate.getPackageName())) return candidate;
      }
    }catch(Exception ignored){}
    return null;
  }

  boolean hasReceiverLabel(AccessibilityNodeInfo node){
    if(node==null)return false;
    String value=readText(node);
    if(value.contains("poef") || value.contains("sideplay") || value.contains("screen mirror") ||
       value.contains("screen mirroring") || value.contains("receiver") ||
       value.contains("waiting for") || value.equals("ready") ||
       value.contains("is ready") ||
       value.contains("airplay")) return true;
    for(int i=0;i<node.getChildCount();i++) if(hasReceiverLabel(node.getChild(i))) return true;
    return false;
  }

  boolean hasInteractiveControl(AccessibilityNodeInfo node){
    if(node==null)return false;
    if(node.isClickable() || node.isFocusable() || node.isScrollable()) return true;
    for(int i=0;i<node.getChildCount();i++) if(hasInteractiveControl(node.getChild(i))) return true;
    return false;
  }

  boolean containsVideoSurface(AccessibilityNodeInfo node){
    if(node==null)return false;
    CharSequence className=node.getClassName();
    if(className!=null){
      String name=className.toString();
      if(name.contains("SurfaceView") || name.contains("TextureView") ||
         name.contains("VideoView") || name.contains("GLSurfaceView")) return true;
    }
    for(int i=0;i<node.getChildCount();i++) if(containsVideoSurface(node.getChild(i))) return true;
    return false;
  }

  String readText(AccessibilityNodeInfo node){
    StringBuilder value=new StringBuilder();
    CharSequence text=node.getText();
    CharSequence desc=node.getContentDescription();
    if(text!=null) value.append(text).append(' ');
    if(desc!=null) value.append(desc);
    return value.toString().trim().toLowerCase(java.util.Locale.US);
  }

  public void onDestroy(){
    handler.removeCallbacks(menuCheck);
    removeHomeMask();
    super.onDestroy();
  }

  public void onInterrupt(){}

  protected void onServiceConnected(){
    super.onServiceConnected();
    windowManager=(WindowManager)getSystemService(WINDOW_SERVICE);
    showHomeMask();
    android.accessibilityservice.AccessibilityServiceInfo info=getServiceInfo();
    if(info!=null){
      info.eventTypes=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED|AccessibilityEvent.TYPE_WINDOWS_CHANGED|AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
      info.feedbackType=android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC;
      info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS|android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
      setServiceInfo(info);
    }
  }

  boolean sideplayAudioActive(){
    try{ MainActivity m=MainActivity.instance; return m!=null && m.sideplayAudioActive(); }
    catch(Exception ignored){ return false; }
  }

  void showHomeMask(){
    if(windowManager==null || homeMask!=null) return;
    homeMask=new HomeMask(this);
    WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT);
    lp.gravity=Gravity.TOP|Gravity.LEFT;
    try{ windowManager.addView(homeMask,lp); }catch(Exception ignored){ homeMask=null; }
  }
  void activateHomeMask(){ if(homeMask!=null){ homeMask.active=true; homeMask.invalidate(); } }
  void deactivateHomeMask(){ if(homeMask!=null){ homeMask.active=false; homeMask.invalidate(); } }
  void removeHomeMask(){ if(windowManager!=null&&homeMask!=null){ try{windowManager.removeView(homeMask);}catch(Exception ignored){} homeMask=null; } }

  static final class HomeMask extends View{
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    final MainActivity.PoefView homeView; boolean active;
    HomeMask(android.content.Context c){ super(c); homeView=new MainActivity.PoefView(c,false,false); }
    protected void onDraw(Canvas c){
      if(!active)return;
      homeView.layout(0,0,getWidth(),getHeight());
      homeView.draw(c);
    }
  }
}

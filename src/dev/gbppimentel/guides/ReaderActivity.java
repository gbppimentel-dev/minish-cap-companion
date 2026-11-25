package dev.gbppimentel.guides;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import org.json.*;
import java.util.HashSet;

public class ReaderActivity extends Activity implements Choreographer.FrameCallback {
 WebView web; android.content.SharedPreferences state; String id,url;
 boolean ready=false,restoring=true,framePending=false;
 Handler timer=new Handler(); HashSet<Integer> heldKeys=new HashSet<>();
 long holdStarted,lastFrame; float stick=0,hat=0; double remainder=0;
 ValueAnimator tap;
 final Runnable saveLoop=new Runnable(){public void run(){save();timer.postDelayed(this,700);}};
 final Runnable startHold=()->{if(!heldKeys.isEmpty()){cancelTap();ensureFrame();}};

 public void onCreate(Bundle b){
  super.onCreate(b);getWindow().setFlags(1024,1024);
  state=getSharedPreferences("reading-progress",0);
  Uri uri=getIntent().getData();id=getIntent().getStringExtra("guideId");if(id==null)id="embedded";
  url=uri==null?"file:///android_asset/guide.html":uri.toString();
  web=new WebView(this){
   @Override public boolean dispatchKeyEventPreIme(KeyEvent e){return handleKey(e)||super.dispatchKeyEventPreIme(e);}
   @Override public boolean dispatchKeyEvent(KeyEvent e){return handleKey(e)||super.dispatchKeyEvent(e);}
   @Override public boolean dispatchGenericMotionEvent(MotionEvent e){return handleMotion(e)||super.dispatchGenericMotionEvent(e);}
   @Override public boolean onGenericMotionEvent(MotionEvent e){return handleMotion(e)||super.onGenericMotionEvent(e);}
  };web.setBackgroundColor(0xfff8f7f2);
  web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
  web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(true);
  web.getSettings().setBuiltInZoomControls(true);web.getSettings().setDisplayZoomControls(false);
  web.setWebViewClient(new WebViewClient(){
   public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
    String dest=r.getUrl().toString();if(dest.split("#",2)[0].equals(url.split("#",2)[0]))return false;
    String scheme=r.getUrl().getScheme();if("https".equals(scheme)||"http".equals(scheme))try{startActivity(new Intent(Intent.ACTION_VIEW,r.getUrl()));}catch(Exception ignored){}
    return true;
   }
   public void onPageFinished(WebView v,String u){
    if(ready)return;ready=true;String saved=state.getString(id,null);String restore="";
    if(saved!=null)try{JSONObject s=new JSONObject(saved);String hash=s.optString("hash","");double y=s.optDouble("y",0);
     restore="try{if(typeof navigate==='function')navigate("+JSONObject.quote(hash)+");history.replaceState(null,'',"+JSONObject.quote(hash.isEmpty()?"#chapter-1":hash)+");}catch(e){}requestAnimationFrame(()=>requestAnimationFrame(()=>scrollTo(0,"+y+")));";
    }catch(Exception ignored){}
    v.evaluateJavascript(restore,null);timer.postDelayed(()->{restoring=false;saveLoop.run();},1400);
   }
   public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())Toast.makeText(ReaderActivity.this,"Guide unavailable. Reconnect its folder in Walkthroughs.",Toast.LENGTH_LONG).show();}
  });
  setContentView(web);web.loadUrl(url);
 }

 float scale(){return web.getScale();}
 void cancelTap(){if(tap!=null){tap.cancel();tap=null;}}
 void tapScroll(int css){
  cancelTap();final float[] previous={0};tap=ValueAnimator.ofFloat(0,css*scale());tap.setDuration(170);tap.setInterpolator(new DecelerateInterpolator());
  tap.addUpdateListener(a->{float now=(Float)a.getAnimatedValue();scrollPixels(now-previous[0]);previous[0]=now;});tap.start();
 }
 void scrollPixels(double px){remainder+=px;int step=(int)remainder;if(step!=0){web.scrollBy(0,step);remainder-=step;}}
 int keyDirection(){int d=0;if(heldKeys.contains(KeyEvent.KEYCODE_DPAD_DOWN)||heldKeys.contains(KeyEvent.KEYCODE_DPAD_RIGHT))d++;if(heldKeys.contains(KeyEvent.KEYCODE_DPAD_UP)||heldKeys.contains(KeyEvent.KEYCODE_DPAD_LEFT))d--;return d;}
 boolean holding(){return !heldKeys.isEmpty()&&SystemClock.uptimeMillis()-holdStarted>=220;}
 void ensureFrame(){if(!framePending){framePending=true;lastFrame=0;Choreographer.getInstance().postFrameCallback(this);}}
 public void doFrame(long time){
  framePending=false;double dt=lastFrame==0?1.0/60:Math.min(.04,(time-lastFrame)/1e9);lastFrame=time;
  double velocity=stick*720;
  if(hat!=0)velocity=hat*500;
  if(holding())velocity=keyDirection()*(420+Math.min(1,(SystemClock.uptimeMillis()-holdStarted-220)/350.0)*200);
  if(velocity!=0){scrollPixels(velocity*scale()*dt);framePending=true;Choreographer.getInstance().postFrameCallback(this);}else lastFrame=0;
 }
 @Override public boolean dispatchKeyEvent(KeyEvent event){return handleKey(event)||super.dispatchKeyEvent(event);}
 boolean handleKey(KeyEvent event){
  int k=event.getKeyCode();boolean arrow=k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN||k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT;
  if(arrow){
   if(event.getAction()==KeyEvent.ACTION_DOWN&&!heldKeys.contains(k)){
    if(heldKeys.isEmpty()){holdStarted=SystemClock.uptimeMillis();timer.postDelayed(startHold,220);}heldKeys.add(k);
    int sign=k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_LEFT?-1:1;
    tapScroll(sign*(k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT?220:64));
   }else if(event.getAction()==KeyEvent.ACTION_UP){heldKeys.remove(k);if(heldKeys.isEmpty())timer.removeCallbacks(startHold);}
   return true;
  }
  return false;
 }
 float axis(MotionEvent e,int axis){
  float value=e.getAxisValue(axis),dead=.18f;InputDevice device=e.getDevice();
  if(device!=null){InputDevice.MotionRange range=device.getMotionRange(axis,e.getSource());if(range!=null)dead=Math.max(dead,range.getFlat());}
  if(Math.abs(value)<=dead)return 0;float normalized=Math.min(1,(Math.abs(value)-dead)/(1-dead));return Math.copySign((float)Math.pow(normalized,1.35),value);
 }
 @Override public boolean dispatchGenericMotionEvent(MotionEvent event){return handleMotion(event)||super.dispatchGenericMotionEvent(event);}
 boolean handleMotion(MotionEvent event){
  if((event.getSource()&InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK){
   if(event.getAction()==MotionEvent.ACTION_CANCEL){stopScroll();return true;}
   if(event.getAction()==MotionEvent.ACTION_MOVE){
    float left=axis(event,MotionEvent.AXIS_Y),right=axis(event,MotionEvent.AXIS_RZ);if(right==0)right=axis(event,MotionEvent.AXIS_RY);
    stick=Math.abs(left)>=Math.abs(right)?left:right;hat=event.getAxisValue(MotionEvent.AXIS_HAT_Y);
    if(Math.abs(hat)<.5f)hat=0;
    cancelTap();if(stick!=0||hat!=0)ensureFrame();

   }
   return true;
  }
  return false;
 }
 void stopScroll(){stick=hat=0;heldKeys.clear();timer.removeCallbacks(startHold);cancelTap();Choreographer.getInstance().removeFrameCallback(this);framePending=false;lastFrame=0;remainder=0;}
 @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus)stopScroll();}
 @Override public void onTopResumedActivityChanged(boolean top){super.onTopResumedActivityChanged(top);if(!top)stopScroll();}
 void save(){if(!ready||restoring||web==null)return;web.evaluateJavascript("JSON.stringify({hash:location.hash,y:scrollY})",value->{try{String raw=new JSONArray("["+value+"]").getString(0);JSONObject s=new JSONObject(raw);if(s.has("y"))state.edit().putString(id,raw).commit();}catch(Exception ignored){}});}
 protected void onPause(){stopScroll();save();super.onPause();timer.removeCallbacks(saveLoop);}
 protected void onResume(){super.onResume();if(ready&&!restoring)timer.post(saveLoop);}
 protected void onDestroy(){stopScroll();timer.removeCallbacksAndMessages(null);super.onDestroy();if(web!=null)web.destroy();}
 public void onBackPressed(){stopScroll();save();super.onBackPressed();}
}

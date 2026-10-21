package com.snaptvnow.tv;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.media3.exoplayer.ExoPlayer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,qualifiers="w960dp-h540dp-television")
public class BoxPanelLayoutsTest {
 private void set(Object target,Class<?> type,String key,Object value)throws Exception{Field f=type.getDeclaredField(key);f.setAccessible(true);f.set(target,value);}
 private Object field(Object target,String key)throws Exception{Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);return f.get(target);}
 private void render(MainActivity app)throws Exception{Method m=MainActivity.class.getDeclaredMethod("render");m.setAccessible(true);m.invoke(app);}
 private String text(View v){String value=v instanceof TextView?((TextView)v).getText().toString():"";if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)value+="\n"+text(((ViewGroup)v).getChildAt(i));return value;}
 @Test public void eightNativeDesignsAndRevocationUseTheActualActivity()throws Exception{
  for(String design:new String[]{"classic","cinema","compact","aura","panorama","stadium","noir","horizon"}){
   MainActivity activity=Robolectric.buildActivity(MainActivity.class).get();
   try{
    String id=design.equals("cinema")?"22222222-2222-4222-8222-222222222222":"11111111-1111-4111-8111-111111111111";
    JSONObject identity=new JSONObject().put("appId",id).put("name","Marca "+design).put("design",design).put("accent","#16d4cd").put("background","#07151f").put("logo","");
    identity.put("style",new JSONObject().put("wallpaper","sunset").put("density","compact").put("welcome","Bienvenido a tu mundo"));
    JSONObject art=new JSONObject().put("fit","contain").put("data","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aFeQAAAAASUVORK5CYII=");identity.put("artwork",new JSONObject().put("hero",art).put("poster1",art).put("tv",art));
    JSONObject config=new JSONObject().put("app",identity).put("servers",new JSONArray().put("https://provider.example.com")).put("slides",new JSONArray().put(new JSONObject().put("id","33333333-3333-4333-8333-333333333333").put("seconds",10)));
    BoxPanel.enabled=true;set(null,BoxPanel.class,"identity",identity);set(null,BoxPanel.class,"config",config);ShadowSystemClock.advanceBy(Duration.ofSeconds(1));set(null,BoxPanel.class,"verifiedAt",SystemClock.elapsedRealtime());
    set(activity,MainActivity.class,"prefs",activity.getSharedPreferences("test",0));set(activity,MainActivity.class,"playbackHistory",new PlaybackHistory(activity));set(activity,MainActivity.class,"items",new ArrayList<Catalog.Item>());set(activity,MainActivity.class,"logged",true);
    render(activity);LinearLayout root=(LinearLayout)field(activity,"root");String visible=text(root);assertTrue(visible.contains("Marca "+design));assertTrue(visible.contains("Bienvenido a tu mundo"));assertTrue(visible.contains("TV EN VIVO"));assertTrue(visible.contains("PELÍCULAS"));assertTrue(visible.contains("SERIES"));assertFalse(visible.contains(ExclusiveCatalog.SECTION));
    LinearLayout first=(LinearLayout)root.getChildAt(0);assertEquals(LinearLayout.HORIZONTAL,first.getOrientation());
    assertEquals(design.equals("compact")||design.equals("cinema"),first.getChildAt(0) instanceof LinearLayout);assertNotNull(root.findViewWithTag("boxpanel-home-"+design));assertNotNull(field(activity,"adView"));if(!design.equals("compact"))assertTrue(visible.contains("Destacados de tu marca"));assertTrue(visible.contains("Imágenes de tu servicio")||visible.contains("Novedades de tu servicio"));assertTrue(visible.contains("Actualizar contenido"));
    ExoPlayer video=mock(ExoPlayer.class);set(activity,MainActivity.class,"video",video);set(null,BoxPanel.class,"verifiedAt",0L);render(activity);verify(video).release();assertNull(field(activity,"client"));assertFalse((Boolean)field(activity,"logged"));
   }finally{BoxPanel.enabled=false;set(null,BoxPanel.class,"identity",null);set(null,BoxPanel.class,"config",null);set(null,BoxPanel.class,"verifiedAt",0L);((ExecutorService)field(activity,"searchExecutor")).shutdownNow();}
  }
 }
}

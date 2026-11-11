package com.snaptvnow.tv;

import static org.junit.Assert.*;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import okhttp3.mockwebserver.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Real refresh UI and HTTP catalogs, with synthetic subscriber credentials. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w960dp-h540dp-television",shadows={ManagedRefreshTest.ConfigSource.class,ManagedRefreshTest.SavedSession.class,ManagedRefreshTest.ManagedOrigin.class,ManagedRefreshTest.UpdateSource.class,ManagedRefreshTest.MessageSource.class,ManagedRefreshTest.AdSource.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class ManagedRefreshTest {
  static String legacyAddress,managedAddress;
  static volatile XtreamClient persisted;
  @Implements(value=AppConfig.class,isInAndroidSdk=false)
  public static class ConfigSource {
    @Implementation protected static List<String> load(android.content.Context c){return Arrays.asList(legacyAddress,managedAddress);}
  }
  @Implements(value=SessionStore.class,isInAndroidSdk=false)
  public static class SavedSession {
    @Implementation protected static XtreamClient restore(android.content.Context c){return new XtreamClient(legacyAddress,"copied-user","same-password","1893456000","3");}
    @Implementation protected static void save(android.content.Context c,XtreamClient client){persisted=client;}
  }
  @Implements(value=ProfileReference.class,isInAndroidSdk=false)
  public static class ManagedOrigin {
    @Implementation protected static boolean managed(String server){return Objects.equals(server,managedAddress);}
    // Profile synchronization is unrelated to selecting the new HTTP service.
    @Implementation protected static String managedOrigin(String server){return null;}
  }
  @Implements(value=UpdateChecker.class,isInAndroidSdk=false)
  public static class UpdateSource {@Implementation protected static void check(android.app.Activity a){}}
  @Implements(value=DirectMessages.class,isInAndroidSdk=false)
  public static class MessageSource {@Implementation protected static JSONObject request(XtreamClient c,String action,int id){return new JSONObject();}}
  @Implements(value=Advertisement.class,isInAndroidSdk=false)
  public static class AdSource {
    @Implementation protected static List<Advertisement.Slide> playlist(){return Collections.emptyList();}
  }

  private MockWebServer legacy,managed;
  private ActivityController<MainActivity> controller;
  private MainActivity activity;
  private volatile boolean migrated,catalogFailure,savedBeforeCatalog;
  private CountDownLatch catalogEntered,releaseCatalog;
  private Thread.UncaughtExceptionHandler previousHandler;
  private Object field(String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception e){throw new AssertionError(e);}}
  private void field(String name,Object value){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}catch(Exception e){throw new AssertionError(e);}}
  private void until(BooleanSupplier done)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(!done.getAsBoolean()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);}Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue("Timed out awaiting refresh",done.getAsBoolean());}
  private View find(View view,String label){if(view instanceof TextView&&((TextView)view).getText().toString().contains(label))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=find(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}return null;}
  private MockResponse active(){return new MockResponse().setHeader("Content-Type","application/json").setBody("{\"user_info\":{\"auth\":1,\"status\":\"Active\",\"exp_date\":\"1893456000\",\"max_connections\":\"3\"}}");}

  @Before public void setup()throws Exception{
    previousHandler=Thread.getDefaultUncaughtExceptionHandler();persisted=null;migrated=false;catalogFailure=false;savedBeforeCatalog=false;
    catalogEntered=new CountDownLatch(1);releaseCatalog=new CountDownLatch(1);
    legacy=new MockWebServer();legacy.setDispatcher(new Dispatcher(){@Override public MockResponse dispatch(RecordedRequest request){return active();}});legacy.start();legacyAddress=legacy.url("/").toString().replaceAll("/$","");
    managed=new MockWebServer();managed.setDispatcher(new Dispatcher(){@Override public MockResponse dispatch(RecordedRequest request){
      String path=request.getPath();
      if(!migrated)return new MockResponse().setResponseCode(401).setBody("{\"user_info\":{\"auth\":0},\"error\":\"invalid_credentials\"}");
      if(path.contains("get_live_categories")){
        savedBeforeCatalog=persisted!=null&&Objects.equals(persisted.server(),managedAddress);catalogEntered.countDown();
        try{releaseCatalog.await(5,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}
        return catalogFailure?new MockResponse().setResponseCode(503):new MockResponse().setHeader("Content-Type","application/json").setBody("[{\"category_id\":\"42\",\"category_name\":\"Fixture news\"}]");
      }
      if(path.contains("get_live_streams"))return new MockResponse().setHeader("Content-Type","application/json").setBody("[{\"stream_id\":501,\"name\":\"Managed channel\",\"category_id\":\"42\"}]");
      return active();
    }});managed.start();managedAddress=managed.url("/").toString().replaceAll("/$","");
    controller=Robolectric.buildActivity(MainActivity.class);activity=controller.get();controller.create().start().resume().visible();until(()->Boolean.TRUE.equals(field("logged")));
    assertEquals(legacyAddress,((XtreamClient)field("client")).server());assertEquals(legacyAddress,persisted.server());
    Catalog.Item stale=new Catalog.Item("live900","Old CCF channel","TV en vivo",legacyAddress+"/live/copied-user/same-password/900.m3u8","Fixture news","");
    field("items",new ArrayList<>(Arrays.asList(stale)));field("activeGroup",new XtreamClient.Group("7","Fixture news"));field("activeGroupSection","TV en vivo");field("loadedSection","TV en vivo");
    ((Map<String,Catalog.Item>)field("seen")).put(stale.id,stale);((List<Catalog.Item>)field("recentlyPlayed")).add(stale);
    migrated=true;
  }
  @After public void cleanup()throws Exception{
    releaseCatalog.countDown();if(controller!=null)controller.pause().stop().destroy();managed.shutdown();legacy.shutdown();Thread.setDefaultUncaughtExceptionHandler(previousHandler);
  }
  private void refresh()throws Exception{
    View button=find(activity.getWindow().getDecorView(),"Actualizar contenido");assertNotNull(button);assertTrue(button.performClick());
    until(()->catalogEntered.getCount()==0);assertTrue(savedBeforeCatalog);
    assertEquals(managedAddress,((XtreamClient)field("client")).server());assertEquals(managedAddress,persisted.server());
    assertEquals(managedAddress,AppConfig.savedWorking(activity));assertEquals("copied-user",persisted.username());assertEquals("same-password",persisted.password());
    assertTrue(((List<?>)field("items")).isEmpty());assertTrue(((Map<?,?>)field("seen")).isEmpty());assertTrue(((List<?>)field("recentlyPlayed")).isEmpty());assertNull(field("activeGroup"));
  }
  @Test public void refreshPersistsMigrationBeforeCatalogAndOnlyLoadsNewServiceUrls()throws Exception{
    refresh();releaseCatalog.countDown();until(()->!Boolean.TRUE.equals(field("refreshing")));
    List<Catalog.Item> items=(List<Catalog.Item>)field("items");assertEquals(1,items.size());assertEquals("live501",items.get(0).id);assertTrue(items.get(0).url.startsWith(managedAddress+"/live/"));assertFalse(items.get(0).url.contains(legacyAddress));assertEquals("42",((XtreamClient.Group)field("activeGroup")).id);
  }
  @Test public void failedDestinationCatalogCannotUndoTheVerifiedMigrationOrRestoreCcfUrls()throws Exception{
    catalogFailure=true;refresh();releaseCatalog.countDown();until(()->!Boolean.TRUE.equals(field("refreshing")));
    assertEquals(managedAddress,((XtreamClient)field("client")).server());assertEquals(managedAddress,persisted.server());assertTrue(((List<?>)field("items")).isEmpty());assertTrue(((Map<?,?>)field("seen")).isEmpty());assertNull(field("activeGroup"));
    android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);assertNotNull(find(dialog.getWindow().getDecorView(),"Tu conexión se actualizó"));
  }
}

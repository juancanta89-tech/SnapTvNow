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

/** Real TV activity/lifecycle and HTTP catalog; no subscriber credentials or external services. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w960dp-h540dp-television",shadows={FireTvStartupTest.ConfigSource.class,FireTvStartupTest.SavedSession.class,FireTvStartupTest.UpdateSource.class,FireTvStartupTest.MessageSource.class,FireTvStartupTest.AdSource.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class FireTvStartupTest {
  static String address;
  static CountDownLatch adEntered,releaseAd;
  @Implements(value=AppConfig.class,isInAndroidSdk=false)
  public static class ConfigSource {@Implementation protected static List<String> load(android.content.Context c){return Arrays.asList(address);}}
  @Implements(value=SessionStore.class,isInAndroidSdk=false)
  public static class SavedSession {
    @Implementation protected static XtreamClient restore(android.content.Context c){return new XtreamClient(address,"fixture","fixture-password","1893456000","3");}
    @Implementation protected static void save(android.content.Context c,XtreamClient client){}
  }
  @Implements(value=UpdateChecker.class,isInAndroidSdk=false)
  public static class UpdateSource {@Implementation protected static void check(android.app.Activity a){}}
  @Implements(value=DirectMessages.class,isInAndroidSdk=false)
  public static class MessageSource {@Implementation protected static JSONObject request(XtreamClient c,String action,int id){return new JSONObject();}}
  @Implements(value=Advertisement.class,isInAndroidSdk=false)
  public static class AdSource {
    @Implementation protected static List<Advertisement.Slide> playlist()throws Exception{adEntered.countDown();releaseAd.await(5,TimeUnit.SECONDS);return Arrays.asList(new Advertisement.Slide("legacy",3),new Advertisement.Slide("legacy",3));}
    @Implementation protected static android.graphics.Bitmap image(Advertisement.Slide s){return null;}
  }
  private MockWebServer server;
  private ActivityController<MainActivity> controller;
  private MainActivity activity;
  private Thread.UncaughtExceptionHandler previousHandler;
  private Object field(String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception e){throw new AssertionError(e);}}
  private void until(BooleanSupplier done)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(!done.getAsBoolean()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);}Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue("Timed out awaiting the real TV UI",done.getAsBoolean());}
  private View find(View view,String text){if(text.equals(view.getContentDescription())||view instanceof TextView&&((TextView)view).getText().toString().contains(text))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View result=find(((ViewGroup)view).getChildAt(i),text);if(result!=null)return result;}return null;}
  @Before public void setup()throws Exception{
    previousHandler=Thread.getDefaultUncaughtExceptionHandler();adEntered=new CountDownLatch(1);releaseAd=new CountDownLatch(1);
    server=new MockWebServer();server.setDispatcher(new Dispatcher(){@Override public MockResponse dispatch(RecordedRequest request){String query=request.getPath();String body=query.contains("get_live_categories")?"[{\"category_id\":\"1\",\"category_name\":\"Fixture news\"}]":query.contains("get_live_streams")?"[{\"stream_id\":1,\"name\":\"Fixture channel\",\"category_id\":\"1\"}]":"{\"user_info\":{\"auth\":1,\"status\":\"Active\",\"exp_date\":\"1893456000\",\"max_connections\":\"3\"}}";return new MockResponse().setHeader("Content-Type","application/json").setBody(body);}});server.start();address=server.url("/").toString().replaceAll("/$","");
    controller=Robolectric.buildActivity(MainActivity.class);activity=controller.get();controller.create().start().resume().visible();until(()->Boolean.TRUE.equals(field("logged")));assertTrue((Boolean)field("wide"));
  }
  @After public void cleanup()throws Exception{releaseAd.countDown();if(controller!=null)controller.pause().stop().destroy();server.shutdown();Thread.setDefaultUncaughtExceptionHandler(previousHandler);}
  @Test public void firstNavigationFromHomeToChannelsSurvivesDelayedStartupWork()throws Exception{
    View tv=find(activity.getWindow().getDecorView(),"TV en vivo");assertNotNull(tv);tv.requestFocus();assertTrue(tv.performClick());
    until(()->find(activity.getWindow().getDecorView(),"Fixture news")!=null);View group=find(activity.getWindow().getDecorView(),"Fixture news");group.requestFocus();assertTrue(group.performClick());
    until(()->find(activity.getWindow().getDecorView(),"Fixture channel")!=null);releaseAd.countDown();until(()->((List<?>)field("adSlides")).size()==2);
    assertFalse(activity.isFinishing());assertFalse(activity.isDestroyed());assertNotNull(find(activity.getWindow().getDecorView(),"Fixture channel"));assertEquals("TV en vivo",field("section"));
  }
  @Test public void completedStartupRequestsMustNotRestartAdvertisingAfterTheTvActivityIsDestroyed()throws Exception{
    assertTrue(adEntered.await(5,TimeUnit.SECONDS));controller.pause().stop().destroy();controller=null;releaseAd.countDown();
    long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);while(System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);}
    assertTrue(activity.isDestroyed());assertNull("Destroyed activity must not restart its three-second advertisement timer",field("adNext"));assertTrue(((List<?>)field("adSlides")).isEmpty());
  }
  @Test public void advertisementRepliesWhileTheInstallerIsForegroundedWaitForResume()throws Exception{
    assertTrue(adEntered.await(5,TimeUnit.SECONDS));controller.pause().stop();releaseAd.countDown();until(()->((List<?>)field("adSlides")).size()==2);
    assertNull("Paused TV activity must not restart advertising",field("adNext"));
    controller.restart().start().resume().visible();assertNotNull(field("adNext"));assertFalse(activity.isFinishing());
  }
  @Test public void theTvRemoteCanOpenThePersistentDiagnosticWithoutAStartupPopup(){
    View account=find(activity.getWindow().getDecorView(),"◎");assertNotNull(account);account.requestFocus();assertTrue(account.performClick());
    View diagnostic=find(activity.getWindow().getDecorView(),"Diagnóstico de cierres");assertNotNull(diagnostic);diagnostic.requestFocus();assertTrue(diagnostic.performClick());
    android.app.AlertDialog report=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(report);assertNotNull(find(report.getWindow().getDecorView(),"No hay una excepción Java registrada"));assertFalse(activity.isFinishing());
  }
}

package com.snaptvnow.tv;
import static org.junit.Assert.*;
import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class LiveTransportPersistenceTest {
 @Test public void firstFramePersistsOnlyAnOpaqueKeyAndTransportThenFailureForgetsIt(){
  SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("transport-test",Context.MODE_PRIVATE);prefs.edit().clear().commit();
  String url="https://api.snaptvnow.com/live/fixtureUser/fixturePassword/169.m3u8";
  LiveTransportPolicy first=new LiveTransportPolicy();first.attach(prefs);first.preferred(url);
  assertEquals(1,prefs.getAll().size());first.rendered(url,url);
  LiveTransportPolicy reopened=new LiveTransportPolicy();reopened.attach(prefs);assertEquals(url,reopened.preferred(url));
  assertTrue(reopened.preferred(url.replace("fixturePassword","changed")).endsWith(".ts"));
  String persisted=prefs.getAll().toString();assertFalse(persisted.contains("fixtureUser"));assertFalse(persisted.contains("fixturePassword"));assertFalse(persisted.contains("https"));
  reopened.failed(url,url);LiveTransportPolicy retried=new LiveTransportPolicy();retried.attach(prefs);assertTrue(retried.preferred(url).endsWith(".ts"));
 }
 @Test public void expiredOrCorruptEntriesCannotOverrideDefaultOrGrowWithoutBound(){
  SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("bounded-transport-test",0);prefs.edit().clear().commit();LiveTransportPolicy p=new LiveTransportPolicy();p.attach(prefs);
  for(int i=0;i<100;i++){String url="https://api.snaptvnow.com/live/fixture/private/"+i+".m3u8";p.rendered(url,url);}
  assertTrue(prefs.getAll().size()<=65);
  for(String key:prefs.getAll().keySet())if(key.startsWith("route:"))prefs.edit().putString(key,"m3u8:1").commit();
  LiveTransportPolicy reopened=new LiveTransportPolicy();reopened.attach(prefs);assertTrue(reopened.preferred("https://api.snaptvnow.com/live/fixture/private/99.m3u8").endsWith(".ts"));
 }
}

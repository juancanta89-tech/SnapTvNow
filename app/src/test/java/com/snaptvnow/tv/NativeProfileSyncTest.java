package com.snaptvnow.tv;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.json.JSONObject;
import org.json.JSONArray;
import android.content.Context;
import java.util.HashSet;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class NativeProfileSyncTest {
 private Context context;private long now;
 @Before public void setup(){context=RuntimeEnvironment.getApplication();context.getSharedPreferences("native_profile",0).edit().clear().commit();context.getSharedPreferences("account_favorites",0).edit().clear().commit();context.getSharedPreferences("vod_playback",0).edit().clear().commit();now=System.currentTimeMillis();}
 private JSONObject favorite(long at,boolean deleted)throws Exception{return new JSONObject().put("kind","favorite").put("key","movie:ccf:123").put("type","movie").put("server","ccf").put("id","123").put("updatedAt",at).put("deleted",deleted);}
 @Test public void canonicalIdentityNeverUsesTheMappedXtreamId()throws Exception{
  JSONObject row=new JSONObject().put("stream_id",9001).put("name","Película").put("smn_profile",new JSONObject().put("type","movie").put("id","123").put("server","ccf"));
  Catalog.Item item=XtreamClient.parseItems("https://api.snaptvnow.com","alice","synthetic-password","Películas",new JSONArray().put(row),new java.util.HashMap<>()).get(0);
  assertEquals("movie9001",item.id);assertEquals("movie:ccf:123",item.profile.key());assertTrue(item.url.endsWith("/9001.mp4"));
  assertNull(XtreamClient.parseItems("https://unrelated.example","alice","test","Películas",new JSONArray().put(row),new java.util.HashMap<>()).get(0).profile);
  assertFalse(ProfileReference.managed("http://api.snaptvnow.com"));assertFalse(ProfileReference.managed("https://api.snaptvnow.com.evil.example"));assertFalse(ProfileReference.managed("https://api.snaptvnow.com:444"));
 }
 @Test public void favoritesAreSeparatedByAccountAndService(){
  AccountFavorites favorites=new AccountFavorites(context);String alice=AccountFavorites.scope("https://api.snaptvnow.com","Alice"),bob=AccountFavorites.scope("https://api.snaptvnow.com","bob"),other=AccountFavorites.scope("https://other.example","alice");
  context.getSharedPreferences("demo",0).edit().putBoolean("fav_movie123",true).commit();favorites.save(alice,new HashSet<>(Arrays.asList("movie123")));
  assertTrue(favorites.load(alice).contains("movie123"));assertTrue(favorites.load(bob).isEmpty());assertTrue(favorites.load(other).isEmpty());assertEquals(alice,AccountFavorites.scope("https://api.snaptvnow.com/","alice"));
 }
 @Test public void offlineRemovalSurvivesRestartAndOlderRemoteRows()throws Exception{
  String account="synthetic-account";NativeProfileSync first=new NativeProfileSync(context,account,(op,body)->{throw new Exception("offline");},()->{});first.queue(favorite(now,true));first.stop();
  final JSONArray[] posted={null};NativeProfileSync reopened=new NativeProfileSync(context,account,(op,body)->{if(op.equals("profile_get"))return new JSONObject().put("records",new JSONArray().put(favorite(now-10,false)));posted[0]=body.getJSONArray("patches");return new JSONObject().put("records",posted[0]);},()->{});
  reopened.syncNow();assertNotNull(posted[0]);assertTrue(posted[0].getJSONObject(0).getBoolean("deleted"));assertTrue(reopened.record("favorite",new ProfileReference("movie","123","ccf","")).getBoolean("deleted"));reopened.stop();
  NativeProfileSync bob=new NativeProfileSync(context,"bob",(o,b)->new JSONObject().put("records",new JSONArray()),()->{});assertTrue(bob.snapshot().isEmpty());bob.stop();
 }
 @Test public void newerRemoteRemovalDefeatsAnOldOfflineAddition()throws Exception{
  final int[] posts={0};NativeProfileSync sync=new NativeProfileSync(context,"alice",(op,b)->{if(op.equals("profile_patch"))posts[0]++;return new JSONObject().put("records",new JSONArray().put(favorite(now,true)));},()->{});
  sync.queue(favorite(now-1,false));sync.syncNow();assertEquals(0,posts[0]);assertTrue(sync.record("favorite",new ProfileReference("movie","123","ccf","")).getBoolean("deleted"));sync.stop();
 }
 @Test public void remoteProgressUsesSecondsAndPreservesNewerLocalPlayback()throws Exception{
  PlaybackHistory history=new PlaybackHistory(context);Catalog.Item item=new Catalog.Item("movie9001","Prueba","Películas","","Drama").withProfile(new ProfileReference("movie","123","ccf",""));
  JSONObject row=new JSONObject().put("time",91).put("duration",1000).put("updatedAt",now);history.applyRemote("alice",item,row);assertEquals(91000,history.position("alice",item.id));assertEquals(1000000,history.duration("alice",item.id));assertEquals(0,history.position("bob",item.id));
  history.save("alice",item.id,120000,1000000,true);history.applyRemote("alice",item,row);assertEquals(120000,history.position("alice",item.id));
 }
 @Test public void aStoppedAccountCannotApplyALateResponse()throws Exception{
  final NativeProfileSync[] sync={null};final int[] changed={0};sync[0]=new NativeProfileSync(context,"alice",(op,b)->{sync[0].stop();return new JSONObject().put("records",new JSONArray().put(favorite(now,false)));},()->changed[0]++);
  sync[0].syncNow();assertTrue(sync[0].snapshot().isEmpty());assertEquals(0,changed[0]);
 }
}

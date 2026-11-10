package com.snaptvnow.tv;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
  String backup="https://snapmovienow-edge.juancanta89.workers.dev";
  assertEquals(backup,ProfileReference.managedOrigin(backup+":443/"));
  assertFalse(ProfileReference.managed(backup+".example"));
  assertEquals(item.profile.key(),XtreamClient.parseItems(backup,"alice","test","Películas",new JSONArray().put(row),new java.util.HashMap<>()).get(0).profile.key());
 }
 @Test public void aClientKeepsItsServerAfterAnotherAccountConnects(){
  XtreamClient.SERVER="https://first.example";XtreamClient first=new XtreamClient("alice","test","","");XtreamClient.SERVER="https://second.example";XtreamClient second=new XtreamClient("bob","test","","");assertEquals("https://first.example",first.server());assertEquals("https://second.example",second.server());
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
 private void refresh(NativeProfileSync sync)throws Exception{
  CountDownLatch loaded=new CountDownLatch(1);
  sync.refresh(loaded::countDown);
  assertTrue(loaded.await(5,TimeUnit.SECONDS));
 }
 @Test public void aPausedTvSharesMinuteTwentyAndCannotOverwriteTheOtherTv()throws Exception{
  AtomicReference<JSONObject> saved=new AtomicReference<>();
  NativeProfileSync.Remote sameAccount=(op,body)->{
   if(op.equals("profile_patch")){
    JSONObject checkpoint=body.getJSONArray("patches").getJSONObject(0);
    saved.updateAndGet(old->old==null||checkpoint.optLong("updatedAt")>old.optLong("updatedAt")?checkpoint:old);
   }
   JSONArray rows=new JSONArray();if(saved.get()!=null)rows.put(saved.get());
   return new JSONObject().put("records",rows);
  };
  NativeProfileSync living=new NativeProfileSync(context,"living-local",sameAccount,()->{});
  NativeProfileSync bedroom=new NativeProfileSync(context,"bedroom-local",sameAccount,()->{});
  PlaybackHistory history=new PlaybackHistory(context);
  Catalog.Item chapter=new Catalog.Item("episode9","Capítulo 2","Series","https://example.test/9.mp4","","","series3","Serie",1,2)
      .withProfile(new ProfileReference("series","3","ccf","9"));
  MediaItem source=MediaItem.fromUri(chapter.url);
  Player livingPlayer=mock(Player.class),bedroomPlayer=mock(Player.class);
  when(livingPlayer.getDuration()).thenReturn(2_400_000L);
  when(bedroomPlayer.getDuration()).thenReturn(2_400_000L);
  VodPlaybackSession first=new VodPlaybackSession(livingPlayer,history,"living-local",chapter.id,()->living.progress(chapter,history,"living-local"));
  VodPlaybackSession second=new VodPlaybackSession(bedroomPlayer,history,"bedroom-local",chapter.id,()->bedroom.progress(chapter,history,"bedroom-local"));
  try{
   first.start(source,-1,false);first.onPlaybackStateChanged(Player.STATE_READY);
   when(livingPlayer.getCurrentPosition()).thenReturn(1_200_000L);
   first.onPlayWhenReadyChanged(false,Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
   refresh(living);refresh(bedroom);
   assertTrue(history.applyRemote("bedroom-local",chapter,bedroom.record("progress",chapter.profile)));
   second.start(source,-1,false);
   verify(bedroomPlayer).setMediaItem(source,1_200_000L);
   second.onPlaybackStateChanged(Player.STATE_READY);
   when(bedroomPlayer.getCurrentPosition()).thenReturn(1_260_000L);
   second.onPlayWhenReadyChanged(false,Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
   refresh(bedroom);
   first.saveNow();refresh(living);
   assertEquals(1260,living.record("progress",chapter.profile).getLong("time"));
  }finally{first.close();second.close();living.stop();bedroom.stop();}
 }
 @Test public void completedEpisodeKeepsOtherBookmarksAndRecommendsTheNextChapter()throws Exception{
  PlaybackHistory history=new PlaybackHistory(context);
  Catalog.Item earlier=new Catalog.Item("episode8","Capítulo 1","Series","","","","series3","Serie",1,1)
      .withProfile(new ProfileReference("series","3","ccf","8"));
  Catalog.Item finished=new Catalog.Item("episode9","Capítulo 2","Series","","","","series3","Serie",1,2)
      .withProfile(new ProfileReference("series","3","ccf","9"));
  Catalog.Item next=new Catalog.Item("episode10","Capítulo 3","Series","","","","series3","Serie",1,3);
  history.remember("alice",earlier);history.save("alice",earlier.id,80_000,2_400_000,true);
  JSONObject row=new JSONObject().put("updatedAt",history.updated("alice",earlier.id)+1).put("episodeId","9").put("deleted",true).put("completed",true);
  assertFalse(history.applyRemote("alice",earlier,row));
  assertTrue(history.applyRemote("alice",finished,row));
  assertEquals(80_000,history.position("alice",earlier.id));
  assertEquals(2,EpisodeSelection.recommended(Arrays.asList(earlier,finished,next),history,"alice"));
 }
 @Test public void aStoppedAccountCannotApplyALateResponse()throws Exception{
  final NativeProfileSync[] sync={null};final int[] changed={0};sync[0]=new NativeProfileSync(context,"alice",(op,b)->{sync[0].stop();return new JSONObject().put("records",new JSONArray().put(favorite(now,false)));},()->changed[0]++);
  sync[0].syncNow();assertTrue(sync[0].snapshot().isEmpty());assertEquals(0,changed[0]);
 }
 @Test public void metricsRetryTheSameIdentityAndAreNotPersisted()throws Exception{
  final int[] calls={0};final String[] id={null};NativeProfileSync sync=new NativeProfileSync(context,"alice",(op,body)->{if(op.equals("playback_metric")){calls[0]++;String next=body.getJSONObject("metric").getString("report_id");if(id[0]==null)id[0]=next;else assertEquals(id[0],next);if(calls[0]==1)throw new Exception("network_failed");return new JSONObject().put("ok",true).put("limited",calls[0]==2);}return new JSONObject().put("records",new JSONArray());},()->{});
  sync.queueMetric(new JSONObject().put("report_id","synthetic-report"));sync.syncNow();sync.syncNow();sync.syncNow();sync.syncNow();assertEquals(3,calls[0]);assertFalse(context.getSharedPreferences("native_profile",0).getAll().toString().contains("synthetic-report"));sync.stop();
 }
 @Test public void failedTelemetryDoesNotBlockFavoritesProgressOrLanguages()throws Exception{
  final JSONArray[] posted={null};final int[] changes={0};NativeProfileSync sync=new NativeProfileSync(context,"alice",(op,body)->{
   if(op.equals("playback_metric"))throw new Exception("network_failed");
   if(op.equals("profile_get"))return new JSONObject().put("records",new JSONArray().put(new JSONObject().put("kind","preference").put("key","subtitle").put("value","off").put("updatedAt",now-1)));
   posted[0]=body.getJSONArray("patches");return new JSONObject().put("records",posted[0]);
  },()->changes[0]++);
  try{
   sync.queueMetric(new JSONObject().put("report_id","failed-telemetry"));sync.queue(favorite(now,false));
   sync.queue(new JSONObject().put("kind","progress").put("key","movie:ccf:123").put("type","movie").put("server","ccf").put("id","123").put("time",91).put("duration",1000).put("updatedAt",now));
   sync.queue(new JSONObject().put("kind","preference").put("key","audio").put("value","en").put("updatedAt",now));
   sync.syncNow();assertNotNull(posted[0]);assertEquals(3,posted[0].length());assertEquals(1,changes[0]);assertEquals("en",sync.preference("audio"));assertEquals("off",sync.preference("subtitle"));
  }finally{sync.stop();}
 }
 @Test public void aChangeDuringAnUploadIsSentWithoutWaitingForAnotherRefresh()throws Exception{
  final NativeProfileSync[] sync={null};java.util.concurrent.CountDownLatch uploaded=new java.util.concurrent.CountDownLatch(1);java.util.List<JSONObject> sent=java.util.Collections.synchronizedList(new java.util.ArrayList<>());
  sync[0]=new NativeProfileSync(context,"alice",(op,body)->{
   if(op.equals("profile_get"))return new JSONObject().put("records",new JSONArray());
   JSONObject patch=body.getJSONArray("patches").getJSONObject(0);sent.add(patch);
   if(sent.size()==1){sync[0].queue(favorite(now+1,true));sync[0].refresh();}else uploaded.countDown();
   return new JSONObject().put("records",new JSONArray().put(patch));
  },()->{});
  try{sync[0].queue(favorite(now,false));sync[0].refresh();assertTrue(uploaded.await(5,java.util.concurrent.TimeUnit.SECONDS));assertEquals(2,sent.size());assertFalse(sent.get(0).getBoolean("deleted"));assertTrue(sent.get(1).getBoolean("deleted"));}
  finally{sync[0].stop();}
 }
 @Test public void historyIsSeparatedByService()throws Exception{
  PlaybackHistory history=new PlaybackHistory(context);String a=AccountFavorites.scope("https://first.example","alice"),b=AccountFavorites.scope("https://second.example","alice");history.save(a,"movie123",90000,120000,true);assertEquals(0,history.position(b,"movie123"));
 }
 @Test public void httpLoginServersNeverEnableManagedProfileSync()throws Exception{
  assertEquals("https://api.snaptvnow.com",AppConfig.validate("https://api.snaptvnow.com/"));
  for(String server:Arrays.asList("http://provider.example","http://api.snaptvnow.com")){
   assertEquals(server,AppConfig.validate(server+"/"));
   assertFalse(ProfileReference.managed(server));
   assertNull(NativeProfileSync.connect(context,null,server,()->{}));
  }
 }
 @Test public void closingAPlayerReleasesItsReceiptEvenWhenTheProfileStops()throws Exception{
  java.util.concurrent.CountDownLatch released=new java.util.concurrent.CountDownLatch(1);String ticket="a".repeat(80);
  NativeProfileSync sync=new NativeProfileSync(context,"closing-account",(op,body)->{assertEquals("playback_release",op);assertEquals(ticket,body.getString("quality_ticket"));assertFalse(body.has("sid"));released.countDown();return new JSONObject().put("ok",true);},()->{});
  sync.releasePlayback(ticket);sync.stop();assertTrue(released.await(5,java.util.concurrent.TimeUnit.SECONDS));assertFalse(context.getSharedPreferences("native_profile",0).getAll().toString().contains(ticket));
 }

}

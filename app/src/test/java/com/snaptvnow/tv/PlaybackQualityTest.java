package com.snaptvnow.tv;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import android.net.Uri;
import android.os.Looper;
import android.content.Context;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DecoderCounters;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;
import java.time.Duration;
import java.util.Collections;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class PlaybackQualityTest {
 private static final String FIRST="A".repeat(80),SECOND="B".repeat(80);
 private void receipt(PlaybackTransfer transfer,String url,String ticket){
  DataSource source=mock(DataSource.class);
  when(source.getResponseHeaders()).thenReturn(Collections.singletonMap("x-snap-playback-quality",Collections.singletonList(ticket)));
  transfer.onTransferStart(source,new DataSpec(Uri.parse(url)),true);
 }
 @Test public void receiptsAreAcceptedOnlyFromTheManagedHttpsPlaybackEndpoint(){
  PlaybackTransfer transfer=new PlaybackTransfer();
  for(String url:new String[]{"http://api.snaptvnow.com/live/test/test/1.ts","https://api.snaptvnow.com.evil.test/live/test/test/1.ts","https://provider.test/live/test/test/1.ts","https://api.snaptvnow.com:8444/live/test/test/1.ts","https://api.snaptvnow.com/stream?t=test"})receipt(transfer,url,FIRST);
  assertNull(transfer.qualityTicket());
  receipt(transfer,"https://api.snaptvnow.com/live/test/test/1.m3u8",FIRST);assertEquals(FIRST,transfer.qualityTicket());
  receipt(transfer,"https://provider.test/movie/test/test/1.mp4",SECOND);assertEquals(FIRST,transfer.qualityTicket());
  receipt(transfer,"https://api.snaptvnow.com/movie/test/test/1.mp4","invalid receipt");assertEquals(FIRST,transfer.qualityTicket());
  DataSource duplicate=mock(DataSource.class);when(duplicate.getResponseHeaders()).thenReturn(Collections.singletonMap(PlaybackTransfer.MONITOR_RESPONSE_HEADER,Arrays.asList(FIRST,SECOND)));
  transfer.onTransferStart(duplicate,new DataSpec(Uri.parse("https://api.snaptvnow.com/movie/test/test/1.mp4")),true);assertEquals(FIRST,transfer.qualityTicket());
  receipt(transfer,"https://api.snaptvnow.com:443/series/test/test/2.mkv",SECOND);assertEquals(SECOND,transfer.qualityTicket());
  receipt(transfer,"https://api.snaptvnow.com/exclusive/test/test/custom-movie.m3u8",FIRST);assertEquals(FIRST,transfer.qualityTicket());
 }
 @Test public void retryKeepsTheOriginalReceiptAndAgesTheObservationWithoutPersistingIt()throws Exception{
  Context context=RuntimeEnvironment.getApplication();List<JSONObject> sent=new ArrayList<>();
  NativeProfileSync sync=new NativeProfileSync(context,"quality-isolation",(op,body)->{
   if(!op.equals("playback_metric"))return new JSONObject().put("records",new JSONArray());
   sent.add(new JSONObject(body.toString()));
   if(sent.size()==1)throw new Exception("offline");
   return new JSONObject().put("ok",true).put("limited",true).put("qualityHandled",true);
  },()->{});
  try{
   JSONObject metric=new JSONObject().put("report_id","first-metric");sync.queueMetric(metric,FIRST,1920,1080);metric.put("report_id","mutated-after-queue");
   sync.queueMetric(new JSONObject().put("report_id","second-metric"),SECOND,1280,720);
   sync.syncNow();ShadowSystemClock.advanceBy(Duration.ofSeconds(5));sync.syncNow();sync.syncNow();sync.syncNow();
   assertEquals(3,sent.size());assertEquals(FIRST,sent.get(0).getString("quality_ticket"));assertEquals(FIRST,sent.get(1).getString("quality_ticket"));assertEquals(SECOND,sent.get(2).getString("quality_ticket"));
   assertEquals("first-metric",sent.get(1).getJSONObject("metric").getString("report_id"));assertTrue(sent.get(1).getJSONObject("observation").getLong("ageMs")>=5000);
   assertEquals(1920,sent.get(1).getJSONObject("observation").getInt("width"));assertEquals(720,sent.get(2).getJSONObject("observation").getInt("height"));
   String persisted=context.getSharedPreferences("native_profile",0).getAll().toString();assertFalse(persisted.contains(FIRST));assertFalse(persisted.contains("first-metric"));
  }finally{sync.stop();}
 }
 @Test public void renderedFramesReachTheScopedReportAfterTheFirstMeasurementWindow()throws Exception{
  Context context=RuntimeEnvironment.getApplication();List<JSONObject> scoped=new ArrayList<>();
  NativeProfileSync sync=new NativeProfileSync(context,"quality-player",(op,body)->{
   if(op.equals("playback_metric")&&body.has("quality_ticket"))scoped.add(new JSONObject(body.toString()));
   return new JSONObject().put("ok",true).put("records",new JSONArray());
  },()->{}){
   // Drive delivery explicitly: this test advances the player's virtual clock,
   // while NativeProfileSyncTest covers its real background executor.
   @Override void metric(JSONObject metric,String ticket,int width,int height){queueMetric(metric,ticket,width,height);}
  };
  ExoPlayer player=mock(ExoPlayer.class);when(player.getApplicationLooper()).thenReturn(Looper.getMainLooper());when(player.getVideoSize()).thenReturn(new VideoSize(1920,1080));when(player.isPlaying()).thenReturn(true);when(player.getPlayWhenReady()).thenReturn(true);when(player.getPlaybackState()).thenReturn(Player.STATE_READY);
  PlaybackTransfer transfer=new PlaybackTransfer();receipt(transfer,"https://api.snaptvnow.com/live/test/test/1.m3u8",FIRST);
  NativePlaybackMetrics metrics=new NativePlaybackMetrics(player,sync,new ProfileReference("live","123","ccf",""),transfer);
  try{
   metrics.onRenderedFirstFrame();DecoderCounters decoder=new DecoderCounters();metrics.onVideoEnabled(null,decoder);decoder.renderedOutputBufferCount=300;
   sync.syncNow();assertTrue(scoped.isEmpty());
   Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11));sync.syncNow();
   assertEquals("The scheduled player report must reach the scoped telemetry transport",1,scoped.size());
   JSONObject report=scoped.get(0);assertEquals(FIRST,report.getString("quality_ticket"));assertTrue(report.getJSONObject("metric").getLong("decoded")>0);assertTrue(report.getJSONObject("metric").getLong("watchMs")>=5000);assertEquals(1080,report.getJSONObject("observation").getInt("height"));
  }finally{metrics.close();sync.stop();}
 }
}

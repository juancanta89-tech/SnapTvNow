package com.snaptvnow.tv;

import android.os.Handler;
import android.os.SystemClock;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import org.json.JSONObject;
import java.util.UUID;

/** Aggregate playback counters. Contains no title, stream URL, username or device identifier. */
final class NativePlaybackMetrics implements Player.Listener, AnalyticsListener {
 static final class Counters {
  private final String type,server;private final long begin;
  private long last,watch,stall;private int stalls,errors;private boolean started,active,waiting,attemptSent,startSent,failureSent;
  private Long startup;private String quality="other";
  private long decoded,dropped,bytes,previousBytes;private boolean bytesMeasured;
  Counters(ProfileReference ref,long now){type=ref.type;server=ref.server;begin=last=now;}
  void sample(long now){long dt=Math.max(0,Math.min(5000,now-last));last=now;if(active){watch=Math.min(300000,watch+dt);if(waiting)stall=Math.min(watch,stall+dt);}}
  void firstFrame(long now){sample(now);if(!started){started=true;startup=Math.min(300000,Math.max(0,now-begin));}}
  void state(long now,boolean playing,boolean buffering,boolean intended){sample(now);if(started&&buffering&&!waiting&&intended)stalls=Math.min(100,stalls+1);waiting=buffering;active=started&&(playing||buffering&&intended);}
  void error(){errors=Math.min(100,errors+1);}
  void height(int height){quality=height>=1080?"1080":height>=720?"720":"other";}
  void frames(long rendered,long lost){
   rendered=Math.max(0,rendered);lost=Math.max(0,lost);
   decoded=Math.min(100000,decoded+rendered+lost);dropped=Math.min(decoded,Math.min(100000,dropped+lost));
  }
  void transfer(long total){if(total<0)return;bytesMeasured=true;bytes=Math.min(250000000,bytes+(total>=previousBytes?total-previousBytes:total));previousBytes=total;}
  JSONObject report(long now,boolean finalReport) throws Exception {
   sample(now);boolean failed=!started&&!failureSent&&(finalReport||errors>0);
   if(watch==0&&errors==0&&decoded==0&&bytes==0&&!failed&&(!started||startSent))return null;
   JSONObject m=new JSONObject().put("report_id",UUID.randomUUID().toString()).put("platform","android").put("type",type).put("server",server).put("quality",quality)
    .put("attempted",!attemptSent).put("started",started&&!startSent).put("failedStart",failed).put("startupMs",started&&!startSent?startup:JSONObject.NULL)
    .put("watchMs",watch).put("stallMs",stall).put("stalls",stalls).put("errors",errors).put("decoded",decoded).put("dropped",dropped).put("bytes",bytesMeasured?bytes:JSONObject.NULL);
   attemptSent=true;if(started)startSent=true;if(failed)failureSent=true;watch=stall=decoded=dropped=bytes=0;stalls=errors=0;return m;
  }
 }
 private final ExoPlayer player;private final NativeProfileSync sync;private final Handler handler;private final Counters counters;private boolean closed;private int ticks;
 private final PlaybackTransfer transfer;private DecoderCounters decoder;private int renderedSnapshot,droppedSnapshot;
 private final Runnable tick=new Runnable(){public void run(){if(closed)return;sample();if(++ticks%30==0)flush(false);handler.postDelayed(this,1000);}};
 NativePlaybackMetrics(ExoPlayer player,NativeProfileSync sync,ProfileReference ref,PlaybackTransfer transfer){this.player=player;this.sync=sync;this.transfer=transfer;handler=new Handler(player.getApplicationLooper());counters=new Counters(ref,SystemClock.elapsedRealtime());player.addListener(this);player.addAnalyticsListener(this);handler.postDelayed(tick,1000);}
 private void sample(){captureFrames();if(transfer!=null)counters.transfer(transfer.bytes());counters.state(SystemClock.elapsedRealtime(),player.isPlaying(),player.getPlaybackState()==Player.STATE_BUFFERING,player.getPlayWhenReady()&&player.getPlaybackSuppressionReason()==Player.PLAYBACK_SUPPRESSION_REASON_NONE);}
 private void captureFrames(){
  if(decoder==null)return;decoder.ensureUpdated();
  int rendered=decoder.renderedOutputBufferCount,lost=decoder.droppedBufferCount;
  counters.frames(Math.max(0,rendered-renderedSnapshot),Math.max(0,lost-droppedSnapshot));renderedSnapshot=rendered;droppedSnapshot=lost;
 }
 @Override public void onVideoEnabled(AnalyticsListener.EventTime time,DecoderCounters next){captureFrames();decoder=next;renderedSnapshot=droppedSnapshot=0;captureFrames();}
 @Override public void onVideoDisabled(AnalyticsListener.EventTime time,DecoderCounters previous){if(decoder==previous){captureFrames();decoder=null;}}
 @Override public void onRenderedFirstFrame(){counters.firstFrame(SystemClock.elapsedRealtime());sample();}
 @Override public void onIsPlayingChanged(boolean playing){sample();}
 @Override public void onPlaybackStateChanged(int state){sample();}
 @Override public void onPlayerError(PlaybackException error){sample();counters.error();android.util.Log.w("PlaybackMetrics","player_error_"+error.errorCode);flush(false);}
 @Override public void onVideoSizeChanged(VideoSize size){counters.height(size.height);}
 private void flush(boolean last){try{JSONObject row=counters.report(SystemClock.elapsedRealtime(),last);if(row!=null)sync.metric(row);}catch(Exception ignored){}}
 void close(){if(closed)return;sample();flush(true);closed=true;handler.removeCallbacks(tick);player.removeListener(this);player.removeAnalyticsListener(this);decoder=null;}
}

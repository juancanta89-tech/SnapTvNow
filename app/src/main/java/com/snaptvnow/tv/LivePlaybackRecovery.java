package com.snaptvnow.tv;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.exoplayer.hls.HlsManifest;

/** A channel-scoped watchdog. Never reuses a failed decoder or changes the channel. */
final class LivePlaybackRecovery implements Player.Listener, AutoCloseable {
  static final class RetryState {
    int failures;
    long delay() { return Math.min(8_000L, 250L << Math.min(failures++, 5)); }
  }
  interface Listener { void reconnect(String reason); }
  private final Player player;
  private final RetryState retries;
  private final Listener listener;
  private final PlaybackTransfer transfer;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final Runnable watchdog=this::inspect;
  private final Runnable reconnect;
  private boolean closed, pending, terminalFailure, videoExpected;
  private String reason;
  private long lastPosition, lastProgress, healthySince;
  private long bufferingSince=-1, lastBufferProgress, buffered, byteCheckpoint, segmentMs;
  private volatile long lastFrame;

  LivePlaybackRecovery(Player player, RetryState retries, Listener listener) {
    this(player,retries,null,listener);
  }
  LivePlaybackRecovery(Player player, RetryState retries, PlaybackTransfer transfer, Listener listener) {
    this.player=player;this.retries=retries;this.listener=listener;
    this.transfer=transfer;
    reconnect=()->{
      if(closed)return;
      pending=false;
      // Recheck a soft timeout: the download/decoder may have recovered during backoff.
      if(wantsPlayback() && (terminalFailure || currentFailure(SystemClock.elapsedRealtime())!=null))listener.reconnect(reason);
    };
    resetProgress();player.addListener(this);handler.postDelayed(watchdog,1_000);
  }
  private boolean wantsPlayback() {
    return player.getPlayWhenReady() && player.getPlaybackSuppressionReason()==Player.PLAYBACK_SUPPRESSION_REASON_NONE;
  }
  private void resetProgress() {
    lastPosition=player.getCurrentPosition();lastProgress=SystemClock.elapsedRealtime();lastFrame=lastProgress;healthySince=0;
    bufferingSince=player.getPlaybackState()==Player.STATE_BUFFERING?lastProgress:-1;
    buffered=player.getTotalBufferedDuration();byteCheckpoint=networkBytes();lastBufferProgress=lastProgress;
  }
  private long networkBytes(){return transfer==null?0:transfer.bytes();}
  void segmentDurationUs(long us){if(us>0)segmentMs=Math.min(60_000,us/1_000);}
  private long quietWindow(){return Math.max(15_000,Math.min(45_000,segmentMs*2+2_000));}
  private long loadingWindow(){return Math.min(90_000,Math.max(45_000,quietWindow()*3));}
  private void beginBuffering(long now){
    if(bufferingSince>=0)return;
    bufferingSince=now;lastBufferProgress=now;buffered=player.getTotalBufferedDuration();byteCheckpoint=networkBytes();healthySince=0;
  }
  private void sampleBuffering(long now){
    beginBuffering(now);
    long available=player.getTotalBufferedDuration(),received=networkBytes();
    // Small playlist refreshes must not disguise a dead media stream.
    if(available>=buffered+250 || received-byteCheckpoint>=32*1024){lastBufferProgress=now;byteCheckpoint=received;}
    buffered=Math.max(buffered,available);
  }
  // Called on the renderer thread; the watchdog reads only this timestamp.
  void videoFrame() { lastFrame=SystemClock.elapsedRealtime(); }
  void videoExpected(boolean expected) { videoExpected=expected;lastFrame=SystemClock.elapsedRealtime(); }
  void failure(String reason) {
    schedule(reason,true);
  }
  private void schedule(String reason,boolean terminal) {
    if(closed || !wantsPlayback())return;
    if(pending){if(terminal&&!terminalFailure){terminalFailure=true;this.reason=reason;}return;}
    this.reason=reason;pending=true;terminalFailure=terminal;healthySince=0;
    handler.postDelayed(reconnect,retries.delay());
  }
  private void inspect() {
    if(closed)return;
    long now=SystemClock.elapsedRealtime();
    if(!wantsPlayback()) {
      handler.removeCallbacks(reconnect);pending=false;resetProgress();
    } else {
      String failure=currentFailure(now);
      if(!pending && failure!=null)schedule(failure,false);
    }
    handler.postDelayed(watchdog,1_000);
  }
  private String currentFailure(long now){
    int state=player.getPlaybackState();long position=player.getCurrentPosition();
    if(state==Player.STATE_ENDED || state==Player.STATE_IDLE)return "live_stopped";
    if(state==Player.STATE_BUFFERING){
      sampleBuffering(now);
      if(now-lastBufferProgress>=quietWindow())return "buffering_no_progress";
      if(now-bufferingSince>=loadingWindow())return "buffering_deadline";
    }else if(state==Player.STATE_READY){
      // READY may arrive between watchdog ticks without a listener callback in tests.
      if(bufferingSince>=0)resetProgress();
      if(Math.abs(position-lastPosition)>=100){lastPosition=position;lastProgress=now;}
      if(now-lastProgress>=12_000)return "playback_stalled";
      if(videoExpected && now-lastFrame>=12_000)return "video_stalled";
      if(player.isPlaying()){
        if(healthySince==0)healthySince=now;
        if(now-healthySince>=30_000)retries.failures=0;
      }
    }
    return null;
  }
  @Override public void onPlaybackStateChanged(int state) {
    if(state==Player.STATE_BUFFERING)beginBuffering(SystemClock.elapsedRealtime());
    else if(state==Player.STATE_READY){
      resetProgress();
      if(pending&&!terminalFailure){handler.removeCallbacks(reconnect);pending=false;}
    }
    else if(state==Player.STATE_ENDED)failure("live_ended");
  }
  @Override public void onTimelineChanged(Timeline timeline,int reason){
    Object manifest=player.getCurrentManifest();
    if(manifest instanceof HlsManifest)segmentDurationUs(((HlsManifest)manifest).mediaPlaylist.targetDurationUs);
  }
  @Override public void onPlayWhenReadyChanged(boolean ready,int reason) {
    if(!ready){handler.removeCallbacks(reconnect);pending=false;}
    resetProgress();
  }
  @Override public void onPlaybackSuppressionReasonChanged(int reason) {resetProgress();}
  @Override public void onPlayerError(PlaybackException error) {failure("player_error_"+error.errorCode);}
  @Override public void close() {
    closed=true;handler.removeCallbacksAndMessages(null);player.removeListener(this);
  }
}

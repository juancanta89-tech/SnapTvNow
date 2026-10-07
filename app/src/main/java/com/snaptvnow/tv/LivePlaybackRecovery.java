package com.snaptvnow.tv;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;

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
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final Runnable watchdog=this::inspect;
  private final Runnable reconnect;
  private boolean closed, pending, videoExpected;
  private String reason;
  private long lastPosition, lastProgress, healthySince;
  private volatile long lastFrame;

  LivePlaybackRecovery(Player player, RetryState retries, Listener listener) {
    this.player=player;this.retries=retries;this.listener=listener;
    reconnect=()->{
      if(closed)return;
      pending=false;
      if(wantsPlayback())listener.reconnect(reason);
    };
    resetProgress();player.addListener(this);handler.postDelayed(watchdog,1_000);
  }
  private boolean wantsPlayback() {
    return player.getPlayWhenReady() && player.getPlaybackSuppressionReason()==Player.PLAYBACK_SUPPRESSION_REASON_NONE;
  }
  private void resetProgress() {
    lastPosition=player.getCurrentPosition();lastProgress=SystemClock.elapsedRealtime();lastFrame=lastProgress;healthySince=0;
  }
  // Called on the renderer thread; the watchdog reads only this timestamp.
  void videoFrame() { lastFrame=SystemClock.elapsedRealtime(); }
  void videoExpected(boolean expected) { videoExpected=expected;lastFrame=SystemClock.elapsedRealtime(); }
  void failure(String reason) {
    if(closed || pending || !wantsPlayback())return;
    this.reason=reason;pending=true;healthySince=0;
    handler.postDelayed(reconnect,retries.delay());
  }
  private void inspect() {
    if(closed)return;
    long now=SystemClock.elapsedRealtime();
    if(!wantsPlayback()) {
      handler.removeCallbacks(reconnect);pending=false;resetProgress();
    } else if(!pending) {
      int state=player.getPlaybackState();long position=player.getCurrentPosition();
      if(state==Player.STATE_ENDED || state==Player.STATE_IDLE)failure("live_stopped");
      else if(state==Player.STATE_BUFFERING) {
        healthySince=0;
        if(now-lastProgress>=8_000)failure("buffering_timeout");
      } else if(state==Player.STATE_READY) {
        if(Math.abs(position-lastPosition)>=100) {lastPosition=position;lastProgress=now;}
        if(now-lastProgress>=12_000)failure("playback_stalled");
        else if(videoExpected && now-lastFrame>=12_000)failure("video_stalled");
        else if(player.isPlaying()) {
          if(healthySince==0)healthySince=now;
          if(now-healthySince>=30_000)retries.failures=0;
        }
      }
    }
    handler.postDelayed(watchdog,1_000);
  }
  @Override public void onPlaybackStateChanged(int state) {
    if(state==Player.STATE_BUFFERING) {lastProgress=SystemClock.elapsedRealtime();healthySince=0;}
    else if(state==Player.STATE_ENDED)failure("live_ended");
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

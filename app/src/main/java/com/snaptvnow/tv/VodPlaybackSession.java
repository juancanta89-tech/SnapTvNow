package com.snaptvnow.tv;

import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.HttpDataSource;

/** Saves the current VOD before teardown and restores it before Media3 prepares the source. */
final class VodPlaybackSession implements Player.Listener {
  static final long CHECKPOINT_INTERVAL_MS = 5_000;
  private final Player player;
  private final PlaybackHistory history;
  private final String account, contentId;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private boolean reachedReady, completed, closed;
  private boolean retryPending;
  private int retries;
  private MediaItem mediaItem;
  private long retryPosition;
  private long requestedStartPosition;
  private final Runnable reconnect = this::reconnect;
  private void reconnect(){
    if(closed||!retryPending||!player.getPlayWhenReady()
        ||player.getPlaybackSuppressionReason()!=Player.PLAYBACK_SUPPRESSION_REASON_NONE)return;
    retryPending=false;reachedReady=false;requestedStartPosition=retryPosition;
    // Reopen the source and its extractor at the same timestamp. Retrying only
    // the failed byte range can retain incomplete MP4 metadata from startup.
    player.setMediaItem(mediaItem,requestedStartPosition);player.prepare();
  }
  private final Runnable checkpoint = new Runnable() {
    @Override public void run() {
      if (closed) return;
      save(false);
      handler.postDelayed(this, CHECKPOINT_INTERVAL_MS);
    }
  };

  VodPlaybackSession(Player player, PlaybackHistory history, String account, String contentId) {
    this.player = player;
    this.history = history;
    this.account = account;
    this.contentId = contentId;
  }

  void start(MediaItem item, long lifecyclePosition, boolean paused) {
    mediaItem=item;
    requestedStartPosition = lifecyclePosition >= 0
        ? lifecyclePosition : history.position(account, contentId);
    player.addListener(this);
    player.setMediaItem(item, requestedStartPosition);
    player.setPlayWhenReady(!paused);
    player.prepare();
    handler.postDelayed(checkpoint, CHECKPOINT_INTERVAL_MS);
  }

  @Override public void onPlaybackStateChanged(int state) {
    if (closed) return;
    if (state == Player.STATE_READY) {
      cancelRecovery();
      reachedReady = true;
      long duration = player.getDuration();
      // A provider may replace the file under an existing ID with a shorter file.
      if (requestedStartPosition > 0 && duration > 0 && requestedStartPosition >= duration) {
        player.seekTo(0);
        history.reset(account, contentId);
      }
      requestedStartPosition = -1;
    } else if (state == Player.STATE_ENDED) {
      cancelRecovery();
      completed = true;
      history.complete(account, contentId);
    }
  }

  @Override public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
    if (!playWhenReady) save(true);
    if(!playWhenReady)handler.removeCallbacks(reconnect);
    else if(retryPending){handler.removeCallbacks(reconnect);handler.postDelayed(reconnect,500);}
  }
  @Override public void onPlaybackSuppressionReasonChanged(int reason){
    if(!closed&&retryPending&&reason==Player.PLAYBACK_SUPPRESSION_REASON_NONE&&player.getPlayWhenReady()){
      handler.removeCallbacks(reconnect);handler.postDelayed(reconnect,500);
    }
  }

  /** Called only after Media3 reports a terminal load error, never during an
   * advancing download. Bounded per viewing; no changes to account or title. */
  boolean recover(PlaybackException error){
    if(closed||completed||mediaItem==null||!retryable(error)){cancelRecovery();return false;}
    if(retryPending)return true;
    if(retries>=2){cancelRecovery();return false;}
    retryPosition=lifecyclePosition();save(true);reachedReady=false;requestedStartPosition=retryPosition;retryPending=true;
    handler.postDelayed(reconnect,++retries==1?500:1500);return true;
  }
  private static boolean retryable(PlaybackException error){
    if(error==null)return false;
    if(error.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        ||error.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)return true;
    if(error.errorCode!=PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)return false;
    Throwable cause=error;for(int depth=0;cause!=null&&depth<12;depth++,cause=cause.getCause()){
      if(cause instanceof HttpDataSource.InvalidResponseCodeException){
        int status=((HttpDataSource.InvalidResponseCodeException)cause).responseCode;
        return status==408||status==429||status>=500&&status<=599;
      }
    }
    return false;
  }
  private void cancelRecovery(){retryPending=false;handler.removeCallbacks(reconnect);}

  @Override public void onPositionDiscontinuity(
      Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
      completed = false;
      save(false);
    }
  }

  private void save(boolean flush) {
    // Preparing/failed startup often reports zero. Preserve the old bookmark until playback is ready.
    if (closed || completed || !reachedReady) return;
    history.save(account, contentId, player.getCurrentPosition(), player.getDuration(), flush);
  }

  void saveNow() {
    save(true);
  }

  long lifecyclePosition() {
    if (completed) return 0;
    if (reachedReady) return Math.max(0, player.getCurrentPosition());
    return Math.max(0, requestedStartPosition);
  }

  void close() {
    if (closed) return;
    save(true);
    closed = true;
    cancelRecovery();
    handler.removeCallbacks(checkpoint);
    player.removeListener(this);
  }
}

package com.snaptvnow.tv;

import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

/** Saves the current VOD before teardown and restores it before Media3 prepares the source. */
final class VodPlaybackSession implements Player.Listener {
  static final long CHECKPOINT_INTERVAL_MS = 5_000;
  private final Player player;
  private final PlaybackHistory history;
  private final String account, contentId;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private boolean reachedReady, completed, closed;
  private long requestedStartPosition;
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
      reachedReady = true;
      long duration = player.getDuration();
      // A provider may replace the file under an existing ID with a shorter file.
      if (requestedStartPosition > 0 && duration > 0 && requestedStartPosition >= duration) {
        player.seekTo(0);
        history.reset(account, contentId);
      }
      requestedStartPosition = -1;
    } else if (state == Player.STATE_ENDED) {
      completed = true;
      history.complete(account, contentId);
    }
  }

  @Override public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
    if (!playWhenReady) save(true);
  }

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
    handler.removeCallbacks(checkpoint);
    player.removeListener(this);
  }
}

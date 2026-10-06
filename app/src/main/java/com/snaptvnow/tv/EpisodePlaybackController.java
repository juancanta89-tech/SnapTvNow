package com.snaptvnow.tv;

import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;

/** One active series player owns one cancelable countdown; no work survives close. */
final class EpisodePlaybackController implements Player.Listener {
  static final int COUNTDOWN_SECONDS = 10;
  interface Listener {
    void onStatus(State state);
    void onNext(Catalog.Item episode);
  }
  static final class State {
    final Catalog.Item next;
    final boolean ended, loading, failed, automatic, cancelled, advancing;
    final int seconds;
    State(EpisodePlaybackController owner) {
      next = owner.next; ended = owner.ended; loading = owner.loading; failed = owner.failed;
      automatic = owner.automatic; cancelled = owner.cancelled; advancing = owner.advancing;
      seconds = owner.seconds;
    }
  }
  private final Player player;
  private final Listener listener;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Catalog.Item next;
  private boolean ended, loading = true, failed, automatic, cancelled, advancing, closed;
  private int seconds;
  private final Runnable advance;
  private final Runnable tick = new Runnable() {
    @Override public void run() {
      if (closed || !ended || loading || failed || !automatic || cancelled || advancing || next == null) return;
      if (--seconds <= 0) { nextNow(); return; }
      emit();
      handler.postDelayed(this, 1000);
    }
  };
  EpisodePlaybackController(Player player, boolean automatic, Listener listener) {
    this.player = player; this.automatic = automatic; this.listener = listener;
    advance = () -> {
      if (closed || !advancing || next == null) return;
      listener.onNext(next);
    };
    player.addListener(this);
  }
  void resolve(Catalog.Item next) {
    if (closed) return;
    this.next = next; loading = false; failed = false;
    if (next == null) stopCountdown();
    beginCountdown(); emit();
  }
  void resolutionFailed() {
    if (closed) return;
    loading = false; failed = true; stopCountdown(); emit();
  }
  void retrying() {
    if (closed) return;
    loading = true; failed = false; stopCountdown(); emit();
  }
  void restoreEnded() {
    if (closed) return;
    ended = true; cancelled = true; stopCountdown(); emit();
  }
  @Override public void onPlaybackStateChanged(int state) {
    if (closed || state != Player.STATE_ENDED || ended) return;
    ended = true; beginCountdown(); emit();
  }
  @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,
      Player.PositionInfo newPosition, int reason) {
    if (closed || reason != Player.DISCONTINUITY_REASON_SEEK) return;
    stopCountdown(); handler.removeCallbacks(advance);
    ended = false; cancelled = false; advancing = false; emit();
  }
  @Override public void onPlayerError(PlaybackException error) {
    if (closed) return;
    stopCountdown(); handler.removeCallbacks(advance);
    ended = false; advancing = false; emit();
  }
  void setAutomatic(boolean automatic) {
    if (closed) return;
    this.automatic = automatic; cancelled = false; stopCountdown();
    beginCountdown(); emit();
  }
  void cancel() {
    if (closed || advancing) return;
    cancelled = true; stopCountdown(); emit();
  }
  void nextNow() {
    if (closed || advancing || loading || failed || next == null) return;
    stopCountdown(); advancing = true; emit();
    // Switch outside the Media3 listener dispatch, after the old bookmark was saved.
    handler.post(advance);
  }
  private void beginCountdown() {
    if (closed || !ended || loading || failed || !automatic || cancelled
        || advancing || next == null || seconds > 0) return;
    seconds = COUNTDOWN_SECONDS;
    handler.postDelayed(tick, 1000);
  }
  private void stopCountdown() { handler.removeCallbacks(tick); seconds = 0; }
  private void emit() { listener.onStatus(new State(this)); }
  State state() { return new State(this); }
  void close() {
    if (closed) return;
    closed = true; stopCountdown(); handler.removeCallbacks(advance); player.removeListener(this);
  }
}

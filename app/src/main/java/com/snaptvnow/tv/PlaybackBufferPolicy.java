package com.snaptvnow.tv;

import androidx.media3.exoplayer.DefaultLoadControl;

/** Separate live/VOD budgets, with an explicit byte target on TV devices. */
final class PlaybackBufferPolicy {
  static DefaultLoadControl create(boolean live) {
    if(live)return new AdaptiveLiveBuffer();
    return new DefaultLoadControl.Builder()
        .setBufferDurationsMs(live?20_000:30_000,live?40_000:60_000,live?LivePlaybackTiming.INITIAL_BUFFER_MS:2_500,live?6_000:5_000)
        .setTargetBufferBytes((live?32:48)*1024*1024)
        .setPrioritizeTimeOverSizeThresholds(false)
        .build();
  }
  private PlaybackBufferPolicy() {}
}

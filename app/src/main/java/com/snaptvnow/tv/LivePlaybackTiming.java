package com.snaptvnow.tv;

/** Initial startup is bounded separately from an established HLS stream. */
final class LivePlaybackTiming {
  static final int INITIAL_BUFFER_MS=1_000;
  // The service has an 8s shared startup budget; allow 1s for the response.
  static long quietWindow(boolean readySeen,long segmentMs) {
    return readySeen?Math.max(15_000,Math.min(45_000,segmentMs*2+2_000)):9_000;
  }
  static long loadingWindow(boolean readySeen,long segmentMs) {
    return Math.min(90_000,Math.max(45_000,quietWindow(readySeen,segmentMs)*3));
  }
  private LivePlaybackTiming() {}
}

package com.snaptvnow.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Local bookmarks for VOD. Never stores the stream URL or the subscriber's password. */
final class PlaybackHistory {
  private final SharedPreferences preferences;

  PlaybackHistory(Context context) {
    preferences = context.getApplicationContext()
        .getSharedPreferences("vod_playback", Context.MODE_PRIVATE);
  }

  long position(String account, String contentId) {
    String key = key(account, contentId);
    long position = Math.max(0, preferences.getLong(key + ".position", 0));
    long duration = preferences.getLong(key + ".duration", 0);
    return duration > 0 && position >= duration ? 0 : position;
  }

  void save(String account, String contentId, long position, long duration, boolean flush) {
    if (position < 0) return;
    String key = key(account, contentId);
    SharedPreferences.Editor editor = preferences.edit().putLong(key + ".position", position);
    // An unknown duration while buffering must not erase a previously known duration.
    if (duration > 0) editor.putLong(key + ".duration", duration);
    if (flush) editor.commit(); else editor.apply();
  }

  void complete(String account, String contentId) {
    String key = key(account, contentId);
    preferences.edit().remove(key + ".position").remove(key + ".duration").commit();
  }

  private static String key(String account, String contentId) {
    // The configured service can change hosts on failover. Keep the same bookmark for its user.
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256")
          .digest((account + "\u0000" + contentId).getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(64);
      for (byte b : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
      return result.toString();
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}

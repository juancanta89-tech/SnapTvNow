package com.snaptvnow.tv;

import android.view.KeyEvent;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TimeBar;

/** Use Media3's millisecond time bar, touch scrubbing and TV transport controls together. */
final class VodPlayerControls {
  static void configure(PlayerView view) {
    view.setUseController(true);
    view.setControllerShowTimeoutMs(5_000);
    view.setControllerAnimationEnabled(false);
    view.setShowRewindButton(true);
    view.setShowFastForwardButton(true);
    view.setShowPreviousButton(false);
    view.setShowNextButton(false);
    view.setShowSubtitleButton(true);
    TimeBar timeBar = view.findViewById(androidx.media3.ui.R.id.exo_progress);
    if (timeBar != null) timeBar.setKeyTimeIncrement(10_000);
    view.setKeepScreenOn(true);
  }

  static boolean dispatchKeyEvent(PlayerView view, KeyEvent event) {
    // Transport keys also work while focus is on our title/back/favorite controls.
    if (view.dispatchMediaKeyEvent(event)) {
      view.showController();
      return true;
    }
    int code = event.getKeyCode();
    boolean dpad = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
        || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
        || code == KeyEvent.KEYCODE_DPAD_CENTER;
    if (dpad && !view.isControllerFullyVisible()) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        view.showController();
        view.requestFocus();
      }
      return true;
    }
    return false;
  }
}

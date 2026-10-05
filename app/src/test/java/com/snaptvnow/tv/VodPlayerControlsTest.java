package com.snaptvnow.tv;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.app.Activity;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import androidx.media3.common.C;
import androidx.media3.common.FlagSet;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.source.SinglePeriodTimeline;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.PlayerView;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/** Exercises the real Media3 view/listeners, not a replacement implementation of its time bar. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class VodPlayerControlsTest {
  private Activity activity;
  private PlayerView view;
  private Player player;
  private final List<Player.Listener> listeners = new ArrayList<>();
  private long duration = 240_000, position = 30_000;

  @Before public void setUp() {
    activity = Robolectric.buildActivity(Activity.class).setup().get();
    player = mock(Player.class);
    when(player.getApplicationLooper()).thenReturn(Looper.getMainLooper());
    when(player.getAvailableCommands()).thenReturn(new Player.Commands.Builder().addAllCommands().build());
    when(player.isCommandAvailable(anyInt())).thenReturn(true);
    when(player.getPlaybackState()).thenReturn(Player.STATE_READY);
    when(player.getPlayWhenReady()).thenReturn(true);
    when(player.isPlaying()).thenReturn(true);
    when(player.getPlaybackParameters()).thenReturn(PlaybackParameters.DEFAULT);
    when(player.getMediaMetadata()).thenReturn(MediaMetadata.EMPTY);
    when(player.getCurrentTracks()).thenReturn(Tracks.EMPTY);
    when(player.getTrackSelectionParameters()).thenReturn(new TrackSelectionParameters.Builder(activity).build());
    when(player.getVideoSize()).thenReturn(VideoSize.UNKNOWN);
    when(player.getCurrentCues()).thenReturn(CueGroup.EMPTY_TIME_ZERO);
    when(player.getSeekBackIncrement()).thenReturn(10_000L);
    when(player.getSeekForwardIncrement()).thenReturn(10_000L);
    when(player.getDuration()).thenAnswer(call -> duration);
    when(player.getContentDuration()).thenAnswer(call -> duration);
    when(player.getCurrentPosition()).thenAnswer(call -> position);
    when(player.getContentPosition()).thenAnswer(call -> position);
    when(player.getContentBufferedPosition()).thenAnswer(call -> position + 10_000);
    when(player.isCurrentMediaItemSeekable()).thenReturn(true);
    when(player.getCurrentTimeline()).thenAnswer(call -> new SinglePeriodTimeline(
        duration == C.TIME_UNSET ? C.TIME_UNSET : duration * 1000,
        true, false, false, null, MediaItem.EMPTY));
    doAnswer(call -> { listeners.add(call.getArgument(0)); return null; })
        .when(player).addListener(any(Player.Listener.class));
    doAnswer(call -> { listeners.remove(call.getArgument(0)); return null; })
        .when(player).removeListener(any(Player.Listener.class));
    view = new PlayerView(activity);
    VodPlayerControls.configure(view);
    activity.setContentView(view);
    view.setPlayer(player);
    // PlayerControlView seeks inside the current item with the single-position overload.
    doAnswer(call -> { position = call.getArgument(0); emit(Player.EVENT_POSITION_DISCONTINUITY); return null; })
        .when(player).seekTo(anyLong());
    view.showController();
    view.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, 1280, 720);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
  }

  @After public void tearDown() {
    view.setPlayer(null);
    activity.finish();
  }

  @Test public void physicalTransportKeysReachPlayerEvenOutsideControllerFocus() {
    assertTrue(VodPlayerControls.dispatchKeyEvent(view,
        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)));
    verify(player).seekForward();
    assertTrue(VodPlayerControls.dispatchKeyEvent(view,
        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_REWIND)));
    verify(player).seekBack();
  }

  @Test public void touchAtMiddleOfTimeBarSeeksToHalfDuration() {
    DefaultTimeBar bar = view.findViewById(androidx.media3.ui.R.id.exo_progress);
    bar.layout(0, 0, 1000, 80);
    MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 500, 40, 0);
    MotionEvent up = MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, 500, 40, 0);
    assertTrue(bar.onTouchEvent(down));
    assertTrue(bar.onTouchEvent(up));
    down.recycle();
    up.recycle();
    verify(player).seekTo(120_000);
  }

  @Test public void dpadOnTimeBarSeeksForwardAndBackwardByTenSeconds() {
    DefaultTimeBar bar = view.findViewById(androidx.media3.ui.R.id.exo_progress);
    bar.requestFocus();
    assertTrue(bar.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT,
        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)));
    bar.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER,
        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
    verify(player).seekTo(40_000);
    assertTrue(bar.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT,
        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)));
    bar.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER,
        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
    verify(player).seekTo(30_000);
  }

  @Test public void lateDurationAndChangedPositionUpdateActualTimeBar() {
    duration = C.TIME_UNSET;
    emit(Player.EVENT_TIMELINE_CHANGED);
    duration = 240_000;
    position = 90_000;
    emit(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY);
    TextView elapsed = view.findViewById(androidx.media3.ui.R.id.exo_position);
    TextView total = view.findViewById(androidx.media3.ui.R.id.exo_duration);
    assertEquals("01:30", elapsed.getText().toString());
    assertEquals("04:00", total.getText().toString());
    DefaultTimeBar bar = view.findViewById(androidx.media3.ui.R.id.exo_progress);
    bar.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT,
        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
    bar.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER,
        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
    verify(player).seekTo(100_000);
  }

  private void emit(int... flags) {
    FlagSet.Builder set = new FlagSet.Builder();
    for (int flag : flags) set.add(flag);
    Player.Events events = new Player.Events(set.build());
    for (Player.Listener listener : new ArrayList<>(listeners)) listener.onEvents(player, events);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
  }
}

package com.snaptvnow.tv;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import android.os.Looper;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class LivePlaybackRecoveryTest {
  private Player player;private LivePlaybackRecovery recovery;
  private LivePlaybackRecovery.RetryState retries;private int reconnects;private String reason;
  @Before public void setUp(){
    player=mock(Player.class);when(player.getPlayWhenReady()).thenReturn(true);
    when(player.getPlaybackState()).thenReturn(Player.STATE_BUFFERING);
    retries=new LivePlaybackRecovery.RetryState();create();
  }
  private void create(){recovery=new LivePlaybackRecovery(player,retries,r->{reconnects++;reason=r;});}
  private void millis(long ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}
  @After public void tearDown(){recovery.close();}
  @Test public void bufferingWithoutPlayerErrorRecoversAfterEightSeconds(){
    millis(8_249);assertEquals(0,reconnects);millis(1);assertEquals(1,reconnects);assertEquals("buffering_timeout",reason);
  }
  @Test public void establishedChannelBufferingRecoversWithinThreeSeconds(){
    recovery.onPlaybackStateChanged(Player.STATE_READY);
    recovery.onPlaybackStateChanged(Player.STATE_BUFFERING);
    millis(3_249);assertEquals(0,reconnects);millis(1);assertEquals(1,reconnects);
  }
  @Test public void recreatedSessionAllowsFullInitialLoadingWindow(){
    recovery.onPlaybackStateChanged(Player.STATE_READY);recovery.close();create();
    millis(3_250);assertEquals(0,reconnects);millis(5_000);assertEquals(1,reconnects);
  }
  @Test public void audioSinkFailureRequestsFreshSessionQuickly(){
    recovery.onPlayerError(new PlaybackException("Audio output failed",null,PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED));millis(249);assertEquals(0,reconnects);millis(1);assertEquals(1,reconnects);
  }
  @Test public void duplicateErrorsQueueOnlyOneReconnect(){
    recovery.failure("audio_sink_error");recovery.failure("player_error");millis(250);assertEquals(1,reconnects);assertEquals(1,retries.failures);
  }
  @Test public void switchingChannelCancelsEveryOldCallback(){
    recovery.failure("player_error");recovery.close();millis(60_000);assertEquals(0,reconnects);verify(player).removeListener(recovery);
  }
  @Test public void userPauseCancelsPendingRetry(){
    recovery.failure("player_error");when(player.getPlayWhenReady()).thenReturn(false);
    recovery.onPlayWhenReadyChanged(false,Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);millis(30_000);assertEquals(0,reconnects);
  }
  @Test public void audioFocusSuppressionDoesNotRestartOrStealFocus(){
    when(player.getPlaybackSuppressionReason()).thenReturn(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS);
    recovery.failure("audio_sink_error");millis(30_000);assertEquals(0,reconnects);
  }
  @Test public void unexpectedLiveEndReconnectsRatherThanStayingBlack(){
    when(player.getPlaybackState()).thenReturn(Player.STATE_ENDED);recovery.onPlaybackStateChanged(Player.STATE_ENDED);
    millis(250);assertEquals(1,reconnects);assertEquals("live_ended",reason);
  }
  @Test public void readyButFrozenPositionIsDetected(){
    when(player.getPlaybackState()).thenReturn(Player.STATE_READY);millis(12_250);assertEquals(1,reconnects);assertEquals("playback_stalled",reason);
  }
  @Test public void frozenVideoIsDetectedEvenWhenPositionMoves(){
    when(player.getPlaybackState()).thenReturn(Player.STATE_READY);recovery.videoExpected(true);
    for(int i=1;i<=12;i++){when(player.getCurrentPosition()).thenReturn(i*1_000L);millis(1_000);}
    millis(250);assertEquals(1,reconnects);assertEquals("video_stalled",reason);
  }
  @Test public void healthyAudioOnlyChannelIsNotMistakenForFrozenVideo(){
    when(player.getPlaybackState()).thenReturn(Player.STATE_READY);when(player.isPlaying()).thenReturn(true);
    for(int i=1;i<=60;i++){when(player.getCurrentPosition()).thenReturn(i*1_000L);millis(1_000);}
    assertEquals(0,reconnects);
  }
  @Test public void healthyVideoAndAudioCanRunForTwoHoursWithoutForcedReconnect(){
    when(player.getPlaybackState()).thenReturn(Player.STATE_READY);when(player.isPlaying()).thenReturn(true);recovery.videoExpected(true);
    for(int i=1;i<=7_200;i++){when(player.getCurrentPosition()).thenReturn(i*1_000L);recovery.videoFrame();millis(1_000);}
    assertEquals(0,reconnects);
  }
  @Test public void successfulRecoveryResetsBackoffAfterThirtyHealthySeconds(){
    retries.failures=5;when(player.getPlaybackState()).thenReturn(Player.STATE_READY);when(player.isPlaying()).thenReturn(true);
    for(int i=1;i<=32;i++){when(player.getCurrentPosition()).thenReturn(i*1_000L);millis(1_000);}
    assertEquals(0,retries.failures);recovery.failure("audio_sink_error");millis(250);assertEquals(1,reconnects);
  }
  @Test public void retryBackoffSurvivesRecreationAndIsBounded(){
    for(int i=0;i<10;i++){long delay=retries.delay();assertTrue(delay>=250 && delay<=8_000);}
    recovery.close();create();assertEquals(10,retries.failures);
  }
  @Test public void bufferingWhichResolvesNormallyDoesNotRebuild(){
    millis(5_000);when(player.getPlaybackState()).thenReturn(Player.STATE_READY);
    for(int i=6;i<=20;i++){when(player.getCurrentPosition()).thenReturn(i*1_000L);millis(1_000);}
    assertEquals(0,reconnects);
  }
}

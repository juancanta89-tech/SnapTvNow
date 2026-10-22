package com.snaptvnow.tv;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.os.Looper;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class VodPlaybackSessionTest {
  private PlaybackHistory history;
  private Player player;
  private VodPlaybackSession session;
  private long position, duration;
  private boolean intended;
  private final MediaItem item = MediaItem.fromUri("https://example.invalid/movie.mp4");

  @Before public void setUp() {
    RuntimeEnvironment.getApplication().getSharedPreferences("vod_playback", 0).edit().clear().commit();
    history = new PlaybackHistory(RuntimeEnvironment.getApplication());
    player = mock(Player.class);
    position = 0;
    duration = 240_000;
    intended=true;
    when(player.getPlayWhenReady()).thenAnswer(call->intended);
    doAnswer(call->{intended=call.getArgument(0);return null;}).when(player).setPlayWhenReady(anyBoolean());
    when(player.getCurrentPosition()).thenAnswer(call -> position);
    when(player.getDuration()).thenAnswer(call -> duration);
    doAnswer(call -> { position = call.getArgument(1); return null; })
        .when(player).setMediaItem(any(MediaItem.class), anyLong());
    doAnswer(call -> { position = call.getArgument(0); return null; })
        .when(player).seekTo(anyLong());
    session = new VodPlaybackSession(player, history, "subscriber-a", "movie17");
  }

  @After public void tearDown() {
    session.close();
  }

  @Test public void reopenMovieStartsFromDurableBookmarkBeforePreparing() {
    history.save("subscriber-a", "movie17", 91_250, 240_000, true);
    session.start(item, -1, false);
    verify(player).setMediaItem(item, 91_250);
    org.mockito.InOrder order = inOrder(player);
    order.verify(player).setMediaItem(item, 91_250);
    order.verify(player).prepare();
  }

  @Test public void closeFlushesLastPositionAndNewHistoryInstanceRestoresIt() {
    session.start(item, -1, false);
    session.onPlaybackStateChanged(Player.STATE_READY);
    position = 103_275;
    session.close();
    assertEquals(103_275, new PlaybackHistory(RuntimeEnvironment.getApplication())
        .position("subscriber-a", "movie17"));
    position = 0;
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));
    assertEquals(103_275, history.position("subscriber-a", "movie17"));
    verify(player).removeListener(session);
  }

  @Test public void checkpointContinuesWhenDurationWasInitiallyUnknown() {
    duration = C.TIME_UNSET;
    session.start(item, -1, false);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
    assertEquals(0, history.position("subscriber-a", "movie17"));
    duration = 240_000;
    session.onPlaybackStateChanged(Player.STATE_READY);
    position = 72_000;
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
    assertEquals(72_000, history.position("subscriber-a", "movie17"));
  }

  @Test public void failedOrInterruptedStartupDoesNotReplaceBookmarkWithZero() {
    history.save("subscriber-a", "movie17", 91_250, 240_000, true);
    session.start(item, -1, false);
    position = 0;
    duration = C.TIME_UNSET;
    assertEquals(91_250, session.lifecyclePosition());
    session.close();
    assertEquals(91_250, history.position("subscriber-a", "movie17"));
  }

  @Test public void pauseFlushesPositionWithoutWaitingForCheckpoint() {
    session.start(item, -1, false);
    session.onPlaybackStateChanged(Player.STATE_READY);
    position = 64_125;
    session.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
    assertEquals(64_125, history.position("subscriber-a", "movie17"));
  }

  @Test public void forwardAndBackwardSeekReplaceTheBookmark() {
    session.start(item, -1, false);
    session.onPlaybackStateChanged(Player.STATE_READY);
    position = 150_000;
    session.onPositionDiscontinuity(null, null, Player.DISCONTINUITY_REASON_SEEK);
    assertEquals(150_000, history.position("subscriber-a", "movie17"));
    position = 40_000;
    session.onPositionDiscontinuity(null, null, Player.DISCONTINUITY_REASON_SEEK);
    assertEquals(40_000, history.position("subscriber-a", "movie17"));
  }

  @Test public void completedContentStartsAtZeroAndCannotBeResavedOnClose() {
    session.start(item, -1, false);
    session.onPlaybackStateChanged(Player.STATE_READY);
    position = duration;
    session.onPlaybackStateChanged(Player.STATE_ENDED);
    assertEquals(0, session.lifecyclePosition());
    session.close();
    assertEquals(0, history.position("subscriber-a", "movie17"));
  }

  @Test public void restartingCompletedContentCanSaveASecondViewing() {
    session.start(item, -1, false);
    session.onPlaybackStateChanged(Player.STATE_READY);
    position = duration;
    session.onPlaybackStateChanged(Player.STATE_ENDED);
    position = 10_000;
    session.onPositionDiscontinuity(null, null, Player.DISCONTINUITY_REASON_SEEK);
    session.close();
    assertEquals(10_000, history.position("subscriber-a", "movie17"));
  }

  @Test public void replacedShorterFileRestartsSafely() {
    history.save("subscriber-a", "movie17", 200_000, 240_000, true);
    session.start(item, -1, false);
    duration = 60_000;
    session.onPlaybackStateChanged(Player.STATE_READY);
    verify(player).seekTo(0);
    assertEquals(0, history.position("subscriber-a", "movie17"));
  }

  @Test public void moviesEpisodesAndAccountsHaveIndependentPositions() {
    history.save("subscriber-a", "movie17", 10_000, 240_000, true);
    history.save("subscriber-a", "episode17", 20_000, 240_000, true);
    history.save("subscriber-b", "movie17", 30_000, 240_000, true);
    assertEquals(10_000, history.position("subscriber-a", "movie17"));
    assertEquals(20_000, history.position("subscriber-a", "episode17"));
    assertEquals(30_000, history.position("subscriber-b", "movie17"));
    assertEquals(0, history.position("subscriber-a", "episode18"));
  }

  @Test public void lifecyclePositionAndPauseOverrideStoredPosition() {
    history.save("subscriber-a", "movie17", 40_000, 240_000, true);
    session.start(item, 45_000, true);
    verify(player).setMediaItem(item, 45_000);
    verify(player).setPlayWhenReady(false);
  }

  @Test public void earlyCheckpointAfterReleaseCannotChangeNextItemsBookmark() {
    session.start(item, -1, false);
    session.onPlaybackStateChanged(Player.STATE_READY);
    position = 50_000;
    session.close();
    history.save("subscriber-a", "episode17", 15_000, 240_000, true);
    position = 70_000;
    session.onPlaybackStateChanged(Player.STATE_READY);
    session.onPositionDiscontinuity(null, null, Player.DISCONTINUITY_REASON_SEEK);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));
    assertEquals(50_000, history.position("subscriber-a", "movie17"));
    assertEquals(15_000, history.position("subscriber-a", "episode17"));
  }

  private PlaybackException httpError(int status){
    HttpDataSource.InvalidResponseCodeException cause=new HttpDataSource.InvalidResponseCodeException(
        status,"Gateway",null,java.util.Collections.emptyMap(),new DataSpec(item.localConfiguration.uri),new byte[0]);
    return new PlaybackException("HTTP failed",cause,PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS);
  }
  private void millis(long ms){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));}

  @Test public void restoredEpisodeRetries522AtTheExactSavedTimeWithoutLosingProgress(){
    session.close();history.remember("subscriber-a",new Catalog.Item("episode18874","Capítulo","Series",item.localConfiguration.uri.toString(),"","","series2822","Serie",15,2));
    history.save("subscriber-a","episode18874",929_000,4_895_000,true);
    PlaybackHistory reopened=new PlaybackHistory(RuntimeEnvironment.getApplication());
    session=new VodPlaybackSession(player,reopened,"subscriber-a","episode18874");
    session.start(item,-1,false);assertTrue(session.recover(httpError(522)));
    position=0;millis(499);verify(player,times(1)).prepare();millis(1);
    verify(player,times(2)).setMediaItem(item,929_000);verify(player,times(2)).prepare();
    millis(5_000);assertEquals(929_000,reopened.position("subscriber-a","episode18874"));
    session.close();assertEquals(929_000,reopened.position("subscriber-a","episode18874"));
  }
  @Test public void playingConnectionFailureReopensAtTheLatestPosition(){
    session.start(item,-1,false);session.onPlaybackStateChanged(Player.STATE_READY);position=103_275;
    assertTrue(session.recover(new PlaybackException("Connection",null,PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)));
    position=0;millis(500);verify(player).setMediaItem(item,103_275);assertEquals(103_275,history.position("subscriber-a","movie17"));
  }
  @Test public void duplicateErrorsDoNotQueueMultipleReopens(){
    session.start(item,-1,false);assertTrue(session.recover(httpError(522)));assertTrue(session.recover(httpError(522)));
    millis(500);verify(player,times(2)).prepare();
  }
  @Test public void permanentFailureHasOnlyTwoAutomaticReopens(){
    session.start(item,-1,false);assertTrue(session.recover(httpError(522)));millis(500);
    assertTrue(session.recover(httpError(524)));millis(1500);
    assertFalse(session.recover(httpError(522)));millis(30_000);verify(player,times(3)).prepare();
  }
  @Test public void closingOrChangingEpisodeCancelsQueuedRecovery(){
    history.save("subscriber-a","movie17",91_250,240_000,true);session.start(item,-1,false);
    assertTrue(session.recover(httpError(522)));session.close();position=0;millis(30_000);
    verify(player,times(1)).prepare();assertEquals(91_250,history.position("subscriber-a","movie17"));
  }
  @Test public void deliberatePauseDefersReconnectUntilTheUserResumes(){
    session.start(item,-1,false);assertTrue(session.recover(httpError(522)));intended=false;
    session.onPlayWhenReadyChanged(false,Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);millis(30_000);verify(player,times(1)).prepare();
    intended=true;session.onPlayWhenReadyChanged(true,Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);millis(500);verify(player,times(2)).prepare();
  }
  @Test public void healthyBufferingNeverStartsRecoveryByItself(){
    session.start(item,-1,false);session.onPlaybackStateChanged(Player.STATE_BUFFERING);millis(90_000);verify(player,times(1)).prepare();
  }
  @Test public void audioFocusSuppressionDefersReconnectUntilFocusReturns(){
    session.start(item,-1,false);assertTrue(session.recover(httpError(522)));
    when(player.getPlaybackSuppressionReason()).thenReturn(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS);
    millis(30_000);verify(player,times(1)).prepare();
    when(player.getPlaybackSuppressionReason()).thenReturn(Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    session.onPlaybackSuppressionReasonChanged(Player.PLAYBACK_SUPPRESSION_REASON_NONE);millis(500);verify(player,times(2)).prepare();
  }
  @Test public void lateReadyCancelsAnUnnecessaryPendingReopen(){
    session.start(item,-1,false);assertTrue(session.recover(httpError(522)));session.onPlaybackStateChanged(Player.STATE_READY);millis(30_000);verify(player,times(1)).prepare();
  }
  @Test public void authMissingFileInvalidSeekAndDecoderErrorsAreNotRetried(){
    session.start(item,-1,false);
    for(int status:new int[]{401,403,404,410,416})assertFalse(session.recover(httpError(status)));
    assertFalse(session.recover(new PlaybackException("Decoder",null,PlaybackException.ERROR_CODE_DECODING_FAILED)));
    millis(30_000);verify(player,times(1)).prepare();
  }
  @Test public void terminalErrorCancelsAnAlreadyQueuedTransientRetry(){
    session.start(item,-1,false);assertTrue(session.recover(httpError(522)));assertFalse(session.recover(httpError(403)));
    millis(30_000);verify(player,times(1)).prepare();
  }
}

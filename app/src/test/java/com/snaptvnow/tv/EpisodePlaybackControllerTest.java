package com.snaptvnow.tv;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import android.os.Looper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
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
@Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class EpisodePlaybackControllerTest {
  private Player player;private EpisodePlaybackController controller;private int starts;private Catalog.Item started;
  private final Catalog.Item next=EpisodeQueueTest.episode("2","series1",1,2);
  @Before public void setUp(){player=mock(Player.class);create(true);}
  private void create(boolean automatic){
    if(controller!=null)controller.close();
    controller=new EpisodePlaybackController(player,automatic,new EpisodePlaybackController.Listener(){
      public void onStatus(EpisodePlaybackController.State state){}
      public void onNext(Catalog.Item episode){starts++;started=episode;}
    });
  }
  @After public void tearDown(){controller.close();}
  private void seconds(int value){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(value));}
  private void end(){controller.resolve(next);controller.onPlaybackStateChanged(Player.STATE_ENDED);}
  @Test public void tenSecondCountdownStartsExactlyOnce(){
    end();assertEquals(10,controller.state().seconds);seconds(9);assertEquals(1,controller.state().seconds);assertEquals(0,starts);
    seconds(1);assertEquals(1,starts);assertSame(next,started);controller.onPlaybackStateChanged(Player.STATE_ENDED);seconds(30);assertEquals(1,starts);
  }
  @Test public void cancelLeavesManualNextAvailable(){
    end();seconds(3);controller.cancel();seconds(30);assertEquals(0,starts);assertTrue(controller.state().cancelled);
    controller.nextNow();seconds(0);assertEquals(1,starts);
  }
  @Test public void preferenceOffRequiresManualNext(){
    create(false);end();seconds(20);assertEquals(0,starts);assertEquals(0,controller.state().seconds);
    controller.nextNow();controller.nextNow();seconds(0);assertEquals(1,starts);
  }
  @Test public void disablingDuringCountdownStopsAndEnablingRestartsTenSeconds(){
    end();seconds(6);controller.setAutomatic(false);seconds(20);assertEquals(0,starts);
    controller.setAutomatic(true);assertEquals(10,controller.state().seconds);seconds(10);assertEquals(1,starts);
  }
  @Test public void closeCancelsCountdownAndAnyPendingManualSwitch(){
    end();seconds(9);controller.close();seconds(10);assertEquals(0,starts);
    create(true);controller.resolve(next);controller.nextNow();controller.close();seconds(0);assertEquals(0,starts);
  }
  @Test public void lastEpisodeNeverStartsAnAutomaticReplay(){
    controller.resolve(null);controller.onPlaybackStateChanged(Player.STATE_ENDED);seconds(30);
    assertEquals(0,starts);assertEquals(0,controller.state().seconds);assertTrue(controller.state().ended);
  }
  @Test public void metadataLoadingAndFailureNeverAdvanceAndCanBeRetried(){
    controller.onPlaybackStateChanged(Player.STATE_ENDED);seconds(30);assertEquals(0,starts);assertTrue(controller.state().loading);
    controller.resolutionFailed();seconds(10);assertEquals(0,starts);
    controller.retrying();controller.resolve(next);assertEquals(10,controller.state().seconds);seconds(10);assertEquals(1,starts);
  }
  @Test public void cancellationBeforeMetadataArrivesRemainsCancelled(){
    controller.onPlaybackStateChanged(Player.STATE_ENDED);controller.cancel();controller.resolve(next);seconds(20);assertEquals(0,starts);
  }
  @Test public void readyBufferingAndPauseDoNotMeanEpisodeEnded(){
    controller.resolve(next);controller.onPlaybackStateChanged(Player.STATE_READY);controller.onPlaybackStateChanged(Player.STATE_BUFFERING);
    seconds(20);assertEquals(0,starts);assertFalse(controller.state().ended);
  }
  @Test public void replayOrSeekingAwayFromEndCancelsCountdown(){
    end();controller.onPositionDiscontinuity(null,null,Player.DISCONTINUITY_REASON_SEEK);seconds(20);
    assertEquals(0,starts);assertFalse(controller.state().ended);
  }
  @Test public void returningFromBackgroundKeepsEndCardWithoutRestartingCountdown(){
    controller.restoreEnded();controller.resolve(next);seconds(20);
    assertTrue(controller.state().ended);assertTrue(controller.state().cancelled);assertEquals(0,starts);
    controller.nextNow();seconds(0);assertEquals(1,starts);
  }
  @Test public void completionHistoryIsSavedBeforeAutomaticNextAndSurvivesClose(){
    RuntimeEnvironment.getApplication().getSharedPreferences("vod_playback",0).edit().clear().commit();
    PlaybackHistory history=new PlaybackHistory(RuntimeEnvironment.getApplication());Catalog.Item current=EpisodeQueueTest.episode("1","series1",1,1);history.remember("alice",current);
    when(player.getCurrentPosition()).thenReturn(120_000L);when(player.getDuration()).thenReturn(120_000L);
    VodPlaybackSession vod=new VodPlaybackSession(player,history,"alice",current.id);vod.start(MediaItem.fromUri(current.url),-1,false);
    vod.onPlaybackStateChanged(Player.STATE_READY);vod.onPlaybackStateChanged(Player.STATE_ENDED);end();
    assertTrue(history.completed("alice",current.id));seconds(10);vod.close();
    assertEquals(1,starts);assertTrue(history.completed("alice",current.id));assertEquals(0,history.position("alice",current.id));
  }
  @Test public void manuallySkippingPreservesPartialEpisodeBookmark(){
    RuntimeEnvironment.getApplication().getSharedPreferences("vod_playback",0).edit().clear().commit();
    PlaybackHistory history=new PlaybackHistory(RuntimeEnvironment.getApplication());Catalog.Item current=EpisodeQueueTest.episode("1","series1",1,1);history.remember("alice",current);
    when(player.getCurrentPosition()).thenReturn(42_000L);when(player.getDuration()).thenReturn(120_000L);
    VodPlaybackSession vod=new VodPlaybackSession(player,history,"alice",current.id);vod.start(MediaItem.fromUri(current.url),-1,false);vod.onPlaybackStateChanged(Player.STATE_READY);
    controller.resolve(next);controller.nextNow();seconds(0);vod.close();
    assertEquals(1,starts);assertEquals(42_000,history.position("alice",current.id));assertFalse(history.completed("alice",current.id));
  }
}

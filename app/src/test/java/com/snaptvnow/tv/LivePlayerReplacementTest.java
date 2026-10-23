package com.snaptvnow.tv;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class LivePlayerReplacementTest {
  public static class TestActivity extends MainActivity {
    ExoPlayer replacement;int creations;
    @Override ExoPlayer createPlayer(){creations++;return replacement;}
  }
  private TestActivity activity;private ExoPlayer oldPlayer,newPlayer;private PlayerView view;
  private Catalog.Item channel=new Catalog.Item("live1","América","TV en vivo","https://example.com/live.m3u8","");
  private Object field(String name)throws Exception {Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
  private void field(String name,Object value)throws Exception {Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
  private void recover()throws Exception {Method m=MainActivity.class.getDeclaredMethod("rebuildLivePlayer",Catalog.Item.class,ExoPlayer.class);m.setAccessible(true);m.invoke(activity,channel,oldPlayer);}
  @Before public void setUp()throws Exception {
    activity=Robolectric.buildActivity(TestActivity.class).get();oldPlayer=mock(ExoPlayer.class);newPlayer=mock(ExoPlayer.class);view=mock(PlayerView.class);
    when(oldPlayer.getPlayWhenReady()).thenReturn(true);when(newPlayer.getPlayWhenReady()).thenReturn(true);
    activity.replacement=newPlayer;field("video",oldPlayer);field("playerView",view);field("currentItem",channel);field("playing",true);field("liveMediaUrl",channel.url);
  }
  @After public void tearDown()throws Exception {
    LivePlaybackRecovery recovery=(LivePlaybackRecovery)field("liveRecovery");if(recovery!=null)recovery.close();
    LiveAudioDiagnostics audio=(LiveAudioDiagnostics)field("audioDiagnostics");if(audio!=null)audio.close();
    ((ExecutorService)field("searchExecutor")).shutdownNow();
  }
  @Test public void releasesOldAudioBeforePreparingReplacementAndPreservesChannelAndControls()throws Exception {
    FrameLayout controls=mock(FrameLayout.class);field("playerControls",controls);recover();
    InOrder order=inOrder(view,oldPlayer,newPlayer);
    order.verify(view).setKeepContentOnPlayerReset(true);order.verify(view).setPlayer(null);order.verify(oldPlayer).release();
    order.verify(view).setPlayer(newPlayer);order.verify(newPlayer).prepare();order.verify(newPlayer).play();
    ArgumentCaptor<MediaItem> media=ArgumentCaptor.forClass(MediaItem.class);verify(newPlayer).setMediaItem(media.capture());
    assertEquals(channel.url,media.getValue().localConfiguration.uri.toString());assertSame(channel,field("currentItem"));
    assertSame(view,field("playerView"));assertSame(controls,field("playerControls"));assertSame(newPlayer,field("video"));assertEquals(1,activity.creations);
  }
  @Test public void staleFailureCannotReplaceTheNewlySelectedChannel()throws Exception {
    field("currentItem",new Catalog.Item("live2","Otro canal","TV en vivo","https://example.com/other", ""));recover();
    assertEquals(0,activity.creations);verify(oldPlayer,never()).release();verifyNoInteractions(newPlayer,view);
  }
  @Test public void deliberatePauseCannotBeOverriddenByRecovery()throws Exception {
    when(oldPlayer.getPlayWhenReady()).thenReturn(false);recover();assertEquals(0,activity.creations);verify(oldPlayer,never()).release();
  }
  @Test public void backgroundOrClosedPlaybackCannotBeRestarted()throws Exception {
    field("playing",false);recover();assertEquals(0,activity.creations);verify(oldPlayer,never()).release();
  }
  @Test public void audioModeChangePreservesDeliberatePauseAndTrackPreference()throws Exception {
    when(oldPlayer.getPlayWhenReady()).thenReturn(false);
    androidx.media3.common.TrackSelectionParameters tracks=new androidx.media3.common.TrackSelectionParameters.Builder(activity).setPreferredAudioLanguage("es").build();
    when(oldPlayer.getTrackSelectionParameters()).thenReturn(tracks);
    Method method=MainActivity.class.getDeclaredMethod("rebuildLivePlayer",Catalog.Item.class,ExoPlayer.class,boolean.class);method.setAccessible(true);method.invoke(activity,channel,oldPlayer,true);
    verify(oldPlayer).release();verify(newPlayer).setTrackSelectionParameters(tracks);verify(newPlayer).setPlayWhenReady(false);verify(newPlayer,never()).play();verify(newPlayer).prepare();assertSame(channel,field("currentItem"));
  }
  @Test public void healthyChannelCleanupReusesPlayerButDetachesOldListenersAndStopsOldSource()throws Exception{
    PlaybackTransferHub hub=new PlaybackTransferHub();field("transferHub",hub);field("playbackTransfer",hub.begin());
    androidx.media3.common.Player.Listener previous=mock(androidx.media3.common.Player.Listener.class);field("playbackListener",previous);
    Method cleanup=MainActivity.class.getDeclaredMethod("releaseVideo",boolean.class);cleanup.setAccessible(true);cleanup.invoke(activity,true);
    InOrder order=inOrder(oldPlayer);order.verify(oldPlayer).removeListener(previous);order.verify(oldPlayer).clearVideoFrameMetadataListener();order.verify(oldPlayer).stop();order.verify(oldPlayer).clearMediaItems();
    verify(oldPlayer,never()).release();assertSame(oldPlayer,field("video"));assertNotNull(field("playbackTransfer"));assertNull(field("playbackListener"));
  }
  @Test public void decoderErrorAndVodCannotUseHealthyLiveReuse()throws Exception{
    field("transferHub",new PlaybackTransferHub());field("playerCompatibleAudio",true);Method account=MainActivity.class.getDeclaredMethod("playbackAccount");account.setAccessible(true);field("playerAccount",account.invoke(activity));
    Method allowed=MainActivity.class.getDeclaredMethod("canReuseLivePlayer",Catalog.Item.class);allowed.setAccessible(true);
    assertTrue((Boolean)allowed.invoke(activity,channel));
    when(oldPlayer.getPlayerError()).thenReturn(new androidx.media3.common.PlaybackException("fixture",null,androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED));assertFalse((Boolean)allowed.invoke(activity,channel));
    when(oldPlayer.getPlayerError()).thenReturn(null);assertFalse((Boolean)allowed.invoke(activity,new Catalog.Item("movie1","Movie","Películas","https://example.invalid/movie.mp4","")));
    field("awaitingLiveRecoveryChoice",true);assertFalse((Boolean)allowed.invoke(activity,channel));
  }

}

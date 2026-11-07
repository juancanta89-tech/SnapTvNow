package com.snaptvnow.tv;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import androidx.media3.common.*;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.ExoPlayer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class LiveRecoveryChoiceTest {
  public static class TestActivity extends MainActivity {
    final ArrayDeque<ExoPlayer> players=new ArrayDeque<>();int creations;
    @Override ExoPlayer createPlayer(){creations++;return players.remove();}
  }
  private TestActivity activity;private ExoPlayer first;
  private Catalog.Item source,target;
  private Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
  private void field(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
  private void play(Catalog.Item item)throws Exception{Method m=MainActivity.class.getDeclaredMethod("play",Catalog.Item.class);m.setAccessible(true);m.invoke(activity,item);}
  private boolean choice(Catalog.Item item,ExoPlayer player)throws Exception{Method m=MainActivity.class.getDeclaredMethod("showLiveRecoveryOptions",Catalog.Item.class,ExoPlayer.class);m.setAccessible(true);return (boolean)m.invoke(activity,item,player);}
  private View button(String tag){return activity.getWindow().getDecorView().findViewWithTag(tag);}
  private ExoPlayer player(){
    ExoPlayer p=mock(ExoPlayer.class);AtomicBoolean ready=new AtomicBoolean(true);
    when(p.getApplicationLooper()).thenReturn(Looper.getMainLooper());when(p.getAvailableCommands()).thenReturn(new Player.Commands.Builder().addAllCommands().build());when(p.isCommandAvailable(anyInt())).thenReturn(true);
    when(p.getPlaybackState()).thenReturn(Player.STATE_BUFFERING);when(p.getPlayWhenReady()).thenAnswer(i->ready.get());doAnswer(i->{ready.set((boolean)i.getArgument(0));return null;}).when(p).setPlayWhenReady(anyBoolean());doAnswer(i->{ready.set(false);return null;}).when(p).pause();doAnswer(i->{ready.set(true);return null;}).when(p).play();
    when(p.getPlaybackParameters()).thenReturn(PlaybackParameters.DEFAULT);when(p.getMediaMetadata()).thenReturn(MediaMetadata.EMPTY);when(p.getCurrentTracks()).thenReturn(Tracks.EMPTY);when(p.getCurrentTimeline()).thenReturn(Timeline.EMPTY);when(p.getTrackSelectionParameters()).thenReturn(new TrackSelectionParameters.Builder(activity).build());when(p.getVideoSize()).thenReturn(VideoSize.UNKNOWN);when(p.getCurrentCues()).thenReturn(CueGroup.EMPTY_TIME_ZERO);
    activity.players.add(p);return p;
  }
  @Before public void setUp()throws Exception{
    activity=Robolectric.buildActivity(TestActivity.class).get();field("prefs",activity.getSharedPreferences("choice-tests",0));field("playbackHistory",new PlaybackHistory(activity));field("accountFavorites",new AccountFavorites(activity));
    source=new Catalog.Item("live1","ESPN 1080","TV en vivo","https://example.invalid/live/account/test/1.m3u8","Deportes").withLiveIdentity("7","espn.mx");target=new Catalog.Item("live2","ESPN 720","TV en vivo","https://example.invalid/live/account/test/2.m3u8","Deportes").withLiveIdentity("7","espn.mx");field("items",Arrays.asList(source,target));first=player();play(source);
  }
  @After public void tearDown()throws Exception{Method m=MainActivity.class.getDeclaredMethod("releaseVideo");m.setAccessible(true);m.invoke(activity);((ExecutorService)field("searchExecutor")).shutdownNow();}
  private void exhaust()throws Exception{
    for(int n=1;n<=5;n++){
      if(n<5)player();((LivePlaybackRecovery)field("liveRecovery")).failure("test_error");
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250L<<(n-1)));
    }
  }
  @Test public void fifthFailureCountsDownThenChangesChannelAutomatically()throws Exception{
    exhaust();assertEquals(5,activity.creations);assertEquals(true,field("awaitingLiveAutoAdvance"));assertSame(source,field("currentItem"));assertNull(field("liveRecovery"));
    ExoPlayer next=player();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2999));assertSame(source,field("currentItem"));
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));assertSame(target,field("currentItem"));assertEquals(6,activity.creations);verify(next).prepare();
  }
  @Test public void deadCandidatesAreSkippedOnceAndSearchStopsWithoutLooping()throws Exception{
    Catalog.Item third=new Catalog.Item("live3","Noticias","TV en vivo","https://example.invalid/live/account/test/3.m3u8","Deportes").withLiveIdentity("7","");
    field("items",Arrays.asList(source,target,third));exhaust();player();player();
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3000));assertSame(target,field("currentItem"));
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(5000));assertSame(third,field("currentItem"));
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(5000));assertEquals(false,field("findingOnline"));assertEquals(7,activity.creations);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2));assertEquals(7,activity.creations);
  }
  @Test public void newerSelectionCancelsTheCountdown()throws Exception{
    exhaust();player();play(target);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3000));assertSame(target,field("currentItem"));assertEquals(6,activity.creations);
  }
  @Test public void backgroundCancelsCountdownAndReturnRestoresItWithoutStartingOldMedia()throws Exception{
    exhaust();activity.onStop();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(4000));assertEquals(5,activity.creations);
    ExoPlayer restored=player();activity.onStart();verify(restored,never()).prepare();assertTrue((boolean)field("awaitingLiveAutoAdvance"));
    player();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3000));assertSame(target,field("currentItem"));
  }
  @Test public void alternativeOnlyPlaysAfterExplicitSelectionAndReleasesOldAudioFirst()throws Exception{
    assertTrue(choice(source,first));ExoPlayer next=player();button("live_720").performClick();assertSame(target,field("currentItem"));assertEquals(false,field("awaitingLiveRecoveryChoice"));org.mockito.InOrder order=inOrder(first,next);order.verify(first).release();order.verify(next).prepare();
  }
  @Test public void unidentifiableAlternativeOffersRetryWithoutA720Button()throws Exception{
    field("items",Arrays.asList(source));choice(source,first);assertNull(button("live_720"));assertNotNull(button("live_back"));ExoPlayer next=player();button("live_retry").performClick();assertSame(source,field("currentItem"));assertEquals(0,((LivePlaybackRecovery.RetryState)field("liveRetries")).recoveryRequests);verify(next).prepare();
  }
  @Test public void oldOptionCannotOverrideANewerChannelSelection()throws Exception{
    choice(source,first);View oldButton=button("live_720");Catalog.Item another=new Catalog.Item("live3","Noticias","TV en vivo","https://example.invalid/live/account/test/3.m3u8","");player();play(another);oldButton.performClick();assertSame(another,field("currentItem"));assertEquals(2,activity.creations);
  }
  @Test public void managedChannelTriesBothTransportsWithinOneViewerSession()throws Exception{
    Catalog.Item managed=new Catalog.Item("live169","ESPN CHI","TV en vivo","https://api.snaptvnow.com/live/test/password/169.m3u8","");
    String viewer=(String)field("playbackViewerId");ExoPlayer continuous=player();play(managed);
    assertEquals("https://api.snaptvnow.com/live/test/password/169.ts",field("liveMediaUrl"));
    ExoPlayer hls=player();((LivePlaybackRecovery)field("liveRecovery")).failure("test_error");Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
    assertEquals(managed.url,field("liveMediaUrl"));assertEquals(viewer,field("playbackViewerId"));
    player();((LivePlaybackRecovery)field("liveRecovery")).failure("test_error");Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
    assertEquals(managed.url,field("liveMediaUrl"));assertTrue(((LivePlaybackRecovery.RetryState)field("liveRetries")).alternateTransportTried);assertEquals(viewer,field("playbackViewerId"));
  }
  @Test public void backgroundReturnRestoresChoiceWithoutPreparingOrStartingMedia()throws Exception{
    choice(source,first);activity.onStop();ExoPlayer restored=player();activity.onStart();assertTrue((boolean)field("awaitingLiveRecoveryChoice"));assertNotNull(button("live_retry"));assertNull(field("liveRecovery"));verify(restored,never()).prepare();verify(restored,never()).play();assertFalse(restored.getPlayWhenReady());
  }
  @Test public void orientationChangePreservesChoiceAndMediaKeysCannotRestartPlayback()throws Exception{
    choice(source,first);ExoPlayer restored=player();activity.onConfigurationChanged(new android.content.res.Configuration(activity.getResources().getConfiguration()));assertNotNull(button("live_720"));verify(restored,never()).prepare();assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_PLAY)));verify(restored,never()).play();
  }
}


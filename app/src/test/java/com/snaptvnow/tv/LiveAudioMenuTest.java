package com.snaptvnow.tv;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class LiveAudioMenuTest {
 public static class TestActivity extends MainActivity {ExoPlayer next;int creations;@Override ExoPlayer createPlayer(){creations++;return next;}}
 private TestActivity activity;private ExoPlayer oldPlayer,next;private SharedPreferences prefs;
 private final Catalog.Item channel=new Catalog.Item("live1","ESPN 1080","TV en vivo","https://example.invalid/live/user/password/1.ts","");
 private void field(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
 private Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
 @Before public void setup()throws Exception{
  activity=Robolectric.buildActivity(TestActivity.class).get();oldPlayer=mock(ExoPlayer.class);next=mock(ExoPlayer.class);activity.next=next;
  prefs=activity.getSharedPreferences("audio-menu-test",Context.MODE_PRIVATE);prefs.edit().clear().commit();
  when(oldPlayer.getPlayWhenReady()).thenReturn(true);when(next.getPlayWhenReady()).thenReturn(true);
  field("prefs",prefs);field("video",oldPlayer);field("currentItem",channel);field("playing",true);field("playerView",mock(PlayerView.class));
 }
 @After public void close()throws Exception{
  AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();if(dialog!=null)dialog.dismiss();
  LivePlaybackRecovery recovery=(LivePlaybackRecovery)field("liveRecovery");if(recovery!=null)recovery.close();
  LiveAudioDiagnostics audio=(LiveAudioDiagnostics)field("audioDiagnostics");if(audio!=null)audio.close();
  ((ExecutorService)field("searchExecutor")).shutdownNow();
 }
 private AlertDialog open()throws Exception{Method m=MainActivity.class.getDeclaredMethod("openLiveAudioOptions",ExoPlayer.class);m.setAccessible(true);m.invoke(activity,oldPlayer);return ShadowAlertDialog.getLatestAlertDialog();}
 @Test public void switchingOutputRetainsCurrentChannelAndReleasesOldPlayer()throws Exception{
  AlertDialog menu=open();assertEquals("Usar audio automático",menu.getListView().getAdapter().getItem(1));menu.getListView().performItemClick(null,1,1);
  assertFalse(prefs.getBoolean(AudioRenderersFactory.COMPATIBLE_KEY,true));assertEquals(1,activity.creations);assertSame(channel,field("currentItem"));assertSame(next,field("video"));verify(oldPlayer).release();verify(next).prepare();
 }
 @Test public void oldMenuCannotChangeAReplacementChannelsAudio()throws Exception{
  AlertDialog menu=open();field("currentItem",new Catalog.Item("live2","Otro","TV en vivo","https://example.invalid/other", ""));field("video",next);
  menu.getListView().performItemClick(null,1,1);assertTrue(prefs.getBoolean(AudioRenderersFactory.COMPATIBLE_KEY,true));assertEquals(0,activity.creations);verify(oldPlayer,never()).release();
 }
}

package com.snaptvnow.tv;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import android.app.Activity;
import android.app.AlertDialog;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class PlayerTrackOptionsTest {
  private Activity activity;
  private Player player;
  private Tracks tracks;
  private TrackSelectionParameters parameters;
  private Tracks.Group group(String id,String mime,String language,String label,boolean supported,boolean selected) {
    Format format=new Format.Builder().setSampleMimeType(mime).setLanguage(language).setLabel(label).build();
    return new Tracks.Group(new TrackGroup(id,format),false,new int[]{supported?C.FORMAT_HANDLED:C.FORMAT_UNSUPPORTED_TYPE},new boolean[]{selected});
  }
  @Before public void setUp() {
    activity=Robolectric.buildActivity(Activity.class).setup().get();player=mock(Player.class);
    tracks=new Tracks(Arrays.asList(group("es",MimeTypes.AUDIO_AAC,"es","Español",true,true),group("en",MimeTypes.AUDIO_AAC,"en","English",true,false),group("sub",MimeTypes.TEXT_VTT,"es","Español",true,false),group("bad",MimeTypes.AUDIO_DTS,"de","Deutsch",false,false),group("video",MimeTypes.VIDEO_H264,null,null,true,true)));
    parameters=new TrackSelectionParameters.Builder(activity).build();
    when(player.getCurrentTracks()).thenAnswer(call->tracks);
    when(player.getTrackSelectionParameters()).thenAnswer(call->parameters);
    when(player.getPlaybackState()).thenReturn(Player.STATE_READY);
    doAnswer(call->{parameters=call.getArgument(0);return null;}).when(player).setTrackSelectionParameters(any());
  }
  @After public void tearDown(){activity.finish();}
  @Test public void listsRealAudioAndSubtitleTracksSeparately() {
    List<PlayerTrackOptions.Choice> audio=PlayerTrackOptions.choices(tracks,C.TRACK_TYPE_AUDIO);
    List<PlayerTrackOptions.Choice> subtitles=PlayerTrackOptions.choices(tracks,C.TRACK_TYPE_TEXT);
    assertEquals(3,audio.size());assertEquals("English",audio.get(1).label);
    assertEquals(1,subtitles.size());assertEquals("Español",subtitles.get(0).label);
    assertFalse(audio.get(2).supported);
  }
  @Test public void switchingAudioPreservesSubtitleSelectionAndDoesNotRestartPlayback() {
    PlayerTrackOptions.Choice subtitle=PlayerTrackOptions.choices(tracks,C.TRACK_TYPE_TEXT).get(0);
    assertTrue(PlayerTrackOptions.select(player,C.TRACK_TYPE_TEXT,subtitle));
    PlayerTrackOptions.Choice english=PlayerTrackOptions.choices(tracks,C.TRACK_TYPE_AUDIO).get(1);
    assertTrue(PlayerTrackOptions.select(player,C.TRACK_TYPE_AUDIO,english));
    assertTrue(parameters.overrides.containsKey(subtitle.group));assertTrue(parameters.overrides.containsKey(english.group));
    verify(player,never()).seekTo(anyLong());verify(player,never()).setMediaItem(any(MediaItem.class));verify(player,never()).prepare();verify(player,never()).pause();
  }
  @Test public void subtitlesCanBeEnabledAndDisabledWithoutChangingAudio() {
    PlayerTrackOptions.Choice audio=PlayerTrackOptions.choices(tracks,C.TRACK_TYPE_AUDIO).get(1);
    PlayerTrackOptions.select(player,C.TRACK_TYPE_AUDIO,audio);PlayerTrackOptions.subtitlesOff(player);
    assertTrue(parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT));assertTrue(parameters.overrides.containsKey(audio.group));
    PlayerTrackOptions.select(player,C.TRACK_TYPE_TEXT,PlayerTrackOptions.choices(tracks,C.TRACK_TYPE_TEXT).get(0));
    assertFalse(parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT));assertTrue(parameters.overrides.containsKey(audio.group));
  }
  @Test public void rejectsUnsupportedAndStaleTracks() {
    List<PlayerTrackOptions.Choice> audio=PlayerTrackOptions.choices(tracks,C.TRACK_TYPE_AUDIO);
    assertFalse(PlayerTrackOptions.select(player,C.TRACK_TYPE_AUDIO,audio.get(2)));
    tracks=Tracks.EMPTY;assertFalse(PlayerTrackOptions.select(player,C.TRACK_TYPE_AUDIO,audio.get(0)));
    verify(player,never()).setTrackSelectionParameters(any());
  }
  @Test public void automaticAudioClearsOnlyAudioOverride() {
    PlayerTrackOptions.Choice audio=PlayerTrackOptions.choices(tracks,C.TRACK_TYPE_AUDIO).get(1);
    PlayerTrackOptions.Choice text=PlayerTrackOptions.choices(tracks,C.TRACK_TYPE_TEXT).get(0);
    PlayerTrackOptions.select(player,C.TRACK_TYPE_AUDIO,audio);PlayerTrackOptions.select(player,C.TRACK_TYPE_TEXT,text);
    PlayerTrackOptions.automatic(player,C.TRACK_TYPE_AUDIO);
    assertFalse(parameters.overrides.containsKey(audio.group));assertTrue(parameters.overrides.containsKey(text.group));
  }
  @Test public void realDialogClickSelectsEnglishTrack() {
    AlertDialog dialog=PlayerTrackOptions.show(activity,player,C.TRACK_TYPE_AUDIO,()->{});
    assertEquals("English",dialog.getListView().getAdapter().getItem(2));
    dialog.getListView().performItemClick(dialog.getListView().getChildAt(2),2,2);
    TrackSelectionOverride selected=parameters.overrides.get(PlayerTrackOptions.choices(tracks,C.TRACK_TYPE_AUDIO).get(1).group);
    assertNotNull(selected);assertEquals(Integer.valueOf(0),selected.trackIndices.get(0));assertFalse(dialog.isShowing());
  }
  @Test public void missingSubtitleTracksShowsAnExplanationWithoutChangingPlayer() {
    tracks=Tracks.EMPTY;AlertDialog dialog=PlayerTrackOptions.show(activity,player,C.TRACK_TYPE_TEXT,()->{});
    android.widget.TextView message=dialog.findViewById(android.R.id.message);
    assertTrue(message.getText().toString().contains("no ofrece subtítulos"));
    verify(player,never()).setTrackSelectionParameters(any());dialog.dismiss();
  }
}

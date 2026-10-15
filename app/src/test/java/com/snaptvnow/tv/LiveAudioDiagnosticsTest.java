package com.snaptvnow.tv;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import java.util.Collections;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class LiveAudioDiagnosticsTest {
 @Test public void unsupportedMp2IsDistinguishedFromMissingAudio()throws Exception{
  ExoPlayer player=mock(ExoPlayer.class);
  Format mp2=new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_MPEG_L2).setChannelCount(2).setSampleRate(48000).setLabel("private account title").build();
  when(player.getCurrentTracks()).thenReturn(new Tracks(Collections.singletonList(new Tracks.Group(new TrackGroup("secret-group-id",mp2),false,new int[]{C.FORMAT_UNSUPPORTED_SUBTYPE},new boolean[]{false}))));
  LiveAudioDiagnostics diag=new LiveAudioDiagnostics(player);JSONObject report=new JSONObject(diag.report("1.0.20",true));
  JSONObject track=report.getJSONArray("tracks").getJSONObject(0);assertEquals(MimeTypes.AUDIO_MPEG_L2,track.getString("mime"));assertFalse(track.getBoolean("supported"));assertFalse(track.getBoolean("selected"));
  assertFalse(report.toString().contains("private account title"));assertFalse(report.toString().contains("secret-group-id"));
  when(player.getCurrentTracks()).thenReturn(Tracks.EMPTY);assertEquals(0,new JSONObject(diag.report("1.0.20",true)).getJSONArray("tracks").length());diag.close();verify(player).removeAnalyticsListener(diag);
 }
 @Test public void reportsDecoderAndSinkErrorsWithoutExceptionContents()throws Exception{
  ExoPlayer player=mock(ExoPlayer.class);when(player.getCurrentTracks()).thenReturn(Tracks.EMPTY);
  LiveAudioDiagnostics diag=new LiveAudioDiagnostics(player);diag.onAudioDecoderInitialized(null,"ffmpegmp3",0,0);diag.onAudioPositionAdvancing(null,0);
  diag.onAudioSinkError(null,new Exception("https://private.example/live/user/password/token"));
  diag.onAudioUnderrun(null,1000,200,300);JSONObject report=new JSONObject(diag.report("1.0.20",true));
  assertEquals("ffmpegmp3",report.getString("decoder"));assertTrue(report.getBoolean("audioClockAdvanced"));assertEquals(1,report.getInt("sinkErrors"));assertEquals(1,report.getInt("underruns"));assertFalse(report.toString().contains("private.example"));diag.close();
 }
}

package com.snaptvnow.tv;

import android.os.Build;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local, opt-in report: contains no URL, account, token or unique device ID. */
final class LiveAudioDiagnostics implements AnalyticsListener,AutoCloseable {
 private final ExoPlayer player;private String decoder="unknown";private int underruns,sinkErrors;private boolean advancing;
 LiveAudioDiagnostics(ExoPlayer player){this.player=player;player.addAnalyticsListener(this);}
 @Override public void onAudioDecoderInitialized(EventTime time,String name,long initialized,long duration){decoder=safe(name);}
 @Override public void onAudioPositionAdvancing(EventTime time,long start){advancing=true;}
 @Override public void onAudioUnderrun(EventTime time,int size,long duration,long elapsed){underruns++;}
 @Override public void onAudioSinkError(EventTime time,Exception error){sinkErrors++;}
 String report(String version,boolean compatible){
  try{
   JSONArray audio=new JSONArray();
   for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==C.TRACK_TYPE_AUDIO)
    for(int i=0;i<group.length&&audio.length()<24;i++){
     Format format=group.getTrackFormat(i);
     audio.put(new JSONObject().put("mime",safe(format.sampleMimeType)).put("channels",format.channelCount)
       .put("sampleRate",format.sampleRate).put("supported",group.isTrackSupported(i)).put("selected",group.isTrackSelected(i)));
    }
   return new JSONObject().put("format","SNAP-native-audio-diagnostic").put("appVersion",version)
    .put("manufacturer",safe(Build.MANUFACTURER)).put("model",safe(Build.MODEL)).put("sdk",Build.VERSION.SDK_INT)
    .put("compatible",compatible).put("decoder",decoder).put("audioClockAdvanced",advancing)
    .put("underruns",underruns).put("sinkErrors",sinkErrors).put("volume",player.getVolume())
    .put("suppressionReason",player.getPlaybackSuppressionReason()).put("tracks",audio).toString(2);
  }catch(Exception ignored){return "No se pudo preparar el diagnóstico de audio.";}
 }
 private static String safe(String value){return value!=null&&value.matches("[A-Za-z0-9_./+() -]{1,120}")?value:"unknown";}
 @Override public void close(){player.removeAnalyticsListener(this);}
}

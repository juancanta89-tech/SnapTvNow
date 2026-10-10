package com.snaptvnow.tv;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;

/** Persist language preference, never an override tied to a previous title's track group. */
final class PlayerTrackPreferences {
 static void apply(Player player,NativeProfileSync profile){
  String audio=profile.preference("audio"),subtitle=profile.preference("subtitle");if(audio==null&&subtitle==null)return;
  TrackSelectionParameters.Builder builder=player.getTrackSelectionParameters().buildUpon();
  if(audio!=null){builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO).setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,false).setPreferredAudioLanguages(audio.matches("[a-z]{2,3}")?new String[]{audio}:new String[0]);}
  if(subtitle!=null){builder.clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,subtitle.equals("off")).setPreferredTextLanguages(subtitle.matches("[a-z]{2,3}")?new String[]{subtitle}:new String[0]);}
  player.setTrackSelectionParameters(builder.build());
 }
 static String language(String raw){if(raw==null)return "default";String language=raw.toLowerCase(java.util.Locale.ROOT).replace('_','-').split("-")[0];return language.matches("[a-z]{2,3}")&&!language.equals("und")?language:"default";}
}

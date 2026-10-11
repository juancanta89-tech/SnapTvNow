package com.snaptvnow.tv;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;

/** Persist language preference, never an override tied to a previous title's track group. */
final class PlayerTrackPreferences {
 static void apply(Player player,NativeProfileSync profile){
  String audio=profile.preference("audio"),subtitle=profile.preference("subtitle");if(audio==null&&subtitle==null)return;
  TrackSelectionParameters current=player.getTrackSelectionParameters();
  TrackSelectionParameters.Builder builder=current.buildUpon();
  if(audio!=null)builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO,false).setPreferredAudioLanguages(audio.matches("[a-z]{2,3}")?new String[]{audio}:new String[0]);
  if(subtitle!=null)builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT,subtitle.equals("off")).setPreferredTextLanguages(subtitle.matches("[a-z]{2,3}")?new String[]{subtitle}:new String[0]);
  TrackSelectionParameters desired=builder.build();
  // A profile refresh must not undo the exact track selected for the current title.
  if(audio!=null&&(!current.preferredAudioLanguages.equals(desired.preferredAudioLanguages)||current.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO)))builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO);
  if(subtitle!=null&&(!current.preferredTextLanguages.equals(desired.preferredTextLanguages)||current.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)!=desired.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)))builder.clearOverridesOfType(C.TRACK_TYPE_TEXT);
  TrackSelectionParameters next=builder.build();if(!next.equals(current))player.setTrackSelectionParameters(next);
 }
 static String language(String raw){if(raw==null)return "default";String language=raw.toLowerCase(java.util.Locale.ROOT).replace('_','-').split("-")[0];return language.matches("[a-z]{2,3}")&&!language.equals("und")?language:"default";}
}

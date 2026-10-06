package com.snaptvnow.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Toast;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Select only the media's real tracks, without rebuilding the player or seeking. */
final class PlayerTrackOptions {
  static final class Choice {
    final TrackGroup group;
    final int index;
    final String label;
    final boolean supported, selected;
    Choice(TrackGroup group,int index,String label,boolean supported,boolean selected) {
      this.group=group;this.index=index;this.label=label;this.supported=supported;this.selected=selected;
    }
  }
  static List<Choice> choices(Tracks tracks,int type) {
    List<Choice> result=new ArrayList<>();
    for(Tracks.Group group:tracks.getGroups()) {
      if(group.getType()!=type) continue;
      for(int i=0;i<group.length;i++) {
        Format format=group.getTrackFormat(i);
        String label=format.label;
        if(label==null || label.trim().isEmpty()) {
          String language=format.language;
          if(language!=null && !language.isEmpty() && !language.equals("und"))
            label=Locale.forLanguageTag(language.replace('_','-')).getDisplayLanguage(new Locale("es"));
          if(label==null || label.isEmpty()) label=(type==C.TRACK_TYPE_AUDIO?"Audio":"Subtítulo")+" "+(result.size()+1);
        }
        if(type==C.TRACK_TYPE_AUDIO && format.channelCount>0) label+=" · "+format.channelCount+" canales";
        boolean supported=group.isTrackSupported(i);
        if(!supported) label+=" · No compatible";
        result.add(new Choice(group.getMediaTrackGroup(),i,label,supported,group.isTrackSelected(i)));
      }
    }
    return result;
  }
  static boolean select(Player player,int type,Choice choice) {
    if(!choice.supported) return false;
    // Reject a stale dialog if playback changed to a different title while it was open.
    boolean available=false;
    for(Choice current:choices(player.getCurrentTracks(),type))
      if(current.supported && current.group.equals(choice.group) && current.index==choice.index) available=true;
    if(!available) return false;
    player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
        .setTrackTypeDisabled(type,false).setOverrideForType(new TrackSelectionOverride(choice.group,choice.index)).build());
    return true;
  }
  static void automatic(Player player,int type) {
    player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
        .setTrackTypeDisabled(type,false).clearOverridesOfType(type).build());
  }
  static void subtitlesOff(Player player) {
    player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build());
  }
  static AlertDialog show(Activity activity,Player player,int type,Runnable dismissed) {
    String title=type==C.TRACK_TYPE_AUDIO?"Audio":"Subtítulos";
    List<Choice> tracks=choices(player.getCurrentTracks(),type);
    if(tracks.isEmpty()) {
      String message=player.getPlaybackState()==Player.STATE_READY
          ? "Este título no ofrece "+(type==C.TRACK_TYPE_AUDIO?"pistas de audio.":"subtítulos.")
          : "El video está cargando. Las pistas estarán disponibles cuando termine de cargar.";
      AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(title).setMessage(message).setPositiveButton("Cerrar",null).create();
      dialog.setOnDismissListener(d->dismissed.run());dialog.show();return dialog;
    }
    int offset=type==C.TRACK_TYPE_TEXT?2:1;
    String[] labels=new String[tracks.size()+offset];
    labels[0]=type==C.TRACK_TYPE_TEXT?"Desactivados":"Automático";
    if(offset==2) labels[1]="Automático";
    TrackSelectionParameters parameters=player.getTrackSelectionParameters();
    int selected=parameters.disabledTrackTypes.contains(type)?0:offset-1;
    for(int i=0;i<tracks.size();i++) { labels[i+offset]=tracks.get(i).label; if(!parameters.disabledTrackTypes.contains(type) && tracks.get(i).selected)selected=i+offset; }
    AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(title).setSingleChoiceItems(labels,selected,null)
        .setNegativeButton("Cerrar",null).create();
    dialog.setOnDismissListener(d->dismissed.run());dialog.show();
    dialog.getListView().setOnItemClickListener((parent,view,index,id)->{
      if(index<offset) { if(type==C.TRACK_TYPE_TEXT && index==0)subtitlesOff(player);else automatic(player,type); }
      else if(!select(player,type,tracks.get(index-offset))) {
        Toast.makeText(activity,"Esta pista no está disponible en este dispositivo.",Toast.LENGTH_LONG).show();return;
      }
      dialog.dismiss();
    });
    return dialog;
  }
}

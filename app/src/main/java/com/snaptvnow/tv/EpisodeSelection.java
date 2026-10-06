package com.snaptvnow.tv;

import java.util.List;

final class EpisodeSelection {
  static int recommended(List<Catalog.Item> episodes,PlaybackHistory history,String account){
    String parent=episodes.isEmpty()?"":episodes.get(0).seriesId;
    PlaybackHistory.Entry entry=history.lastSeries(account,parent);
    if(entry!=null){
      for(int i=0;i<episodes.size();i++)if(episodes.get(i).id.equals(entry.item.id))return entry.completed&&i+1<episodes.size()?i+1:i;
    }
    for(int i=0;i<episodes.size();i++)if(history.position(account,episodes.get(i).id)>0)return i;
    return 0;
  }
  static String[] labels(List<Catalog.Item> episodes,PlaybackHistory history,String account){
    String[] labels=new String[episodes.size()];
    for(int i=0;i<labels.length;i++){
      Catalog.Item episode=episodes.get(i);long position=history.position(account,episode.id);
      String state=position>0?"Continuar · "+PlaybackHistory.time(position)+"\n":history.completed(account,episode.id)?"✓ Visto\n":"";
      labels[i]=state+episode.title;
    }
    return labels;
  }
  private EpisodeSelection(){}
}

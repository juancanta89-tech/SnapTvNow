package com.snaptvnow.tv;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Upgrade recovery belongs only to the encrypted saved account after successful reauthentication.
 * Sources remain untouched. Catalog IDs AND titles/episode identity must agree before copying. */
final class LocalHistoryRecovery {
  interface Source {
    List<Catalog.Item> movies() throws Exception;
    List<Catalog.Item> series() throws Exception;
    List<Catalog.Item> episodes(Catalog.Item series) throws Exception;
  }
  private final List<String> accounts,legacy;
  private final String target;
  private LocalHistoryRecovery(String target,List<String> accounts,List<String> legacy){this.target=target;this.accounts=accounts;this.legacy=legacy;}
  static LocalHistoryRecovery verified(String savedUser,String savedPassword,String currentUser,String currentPassword,String currentServer,List<String> previousServers){
    if(savedUser==null||currentUser==null||savedPassword==null||!savedPassword.equals(currentPassword)||!savedUser.trim().equalsIgnoreCase(currentUser.trim()))return null;
    String target=AccountFavorites.scope(currentServer,currentUser);LinkedHashSet<String> scopes=new LinkedHashSet<>();
    for(String server:previousServers)if(server!=null)scopes.add(AccountFavorites.scope(server,savedUser));
    List<String> legacy=new ArrayList<>();legacy.add(savedUser.trim());if(!savedUser.trim().equals(savedUser.trim().toLowerCase(Locale.ROOT)))legacy.add(savedUser.trim().toLowerCase(Locale.ROOT));
    scopes.addAll(legacy);scopes.remove(target);return new LocalHistoryRecovery(target,new ArrayList<>(scopes),legacy);
  }
  boolean available(PlaybackHistory history){for(String account:accounts)if(!history.recoveryEntries(account).isEmpty())return true;return false;}
  static boolean sameTitle(String a,String b){return a!=null&&b!=null&&a.trim().replaceAll("\\s+"," ").equalsIgnoreCase(b.trim().replaceAll("\\s+"," "));}
  private static boolean sameItem(Catalog.Item old,Catalog.Item current){
    if(!old.id.equals(current.id))return false;
    if(old.profile!=null||current.profile!=null)return old.profile!=null&&current.profile!=null&&old.profile.key().equals(current.profile.key())&&old.profile.episodeId.equals(current.profile.episodeId);
    if(old.id.startsWith("movie"))return sameTitle(old.title,current.title);
    return old.seriesId.equals(current.seriesId)&&sameTitle(old.seriesTitle,current.seriesTitle)
        &&old.seasonNumber==current.seasonNumber&&old.episodeNumber==current.episodeNumber&&sameTitle(old.title,current.title);
  }
  int recover(PlaybackHistory history,Source catalog)throws Exception{
    List<Catalog.Item> movies=null,series=null;Map<String,List<Catalog.Item>> chapters=new LinkedHashMap<>();int restored=0;
    for(String account:accounts)for(PlaybackHistory.Entry entry:history.recoveryEntries(account)){
      Catalog.Item match=null;
      if(entry.item.id.startsWith("movie")){
        if(movies==null)movies=catalog.movies();for(Catalog.Item item:movies)if(sameItem(entry.item,item)){match=item;break;}
      }else{
        if(series==null)series=catalog.series();
        for(Catalog.Item parent:series)if(parent.id.equals(entry.item.seriesId)&&sameTitle(parent.title,entry.item.seriesTitle)){
          if(!chapters.containsKey(parent.id))chapters.put(parent.id,catalog.episodes(parent));
          for(Catalog.Item item:chapters.get(parent.id))if(sameItem(entry.item,item)){match=item;break;}break;
        }
      }
      if(match!=null&&history.restoreVerified(target,entry,match))restored++;
    }
    return restored;
  }
  int restoreLoadedLegacy(PlaybackHistory history,List<Catalog.Item> verifiedItems){
    int count=0;for(Catalog.Item item:verifiedItems)for(String account:legacy)if(history.restoreLegacyPosition(account,target,item)){count++;break;}return count;
  }
}

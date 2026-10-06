package com.snaptvnow.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONObject;

/** Local bookmarks for VOD. Never stores the stream URL or the subscriber's password. */
final class PlaybackHistory {
  private final SharedPreferences preferences;

  PlaybackHistory(Context context) {
    preferences = context.getApplicationContext()
        .getSharedPreferences("vod_playback", Context.MODE_PRIVATE);
  }

  long position(String account, String contentId) {
    String key = key(account, contentId);
    long position = Math.max(0, preferences.getLong(key + ".position", 0));
    long duration = preferences.getLong(key + ".duration", 0);
    return duration > 0 && position >= duration ? 0 : position;
  }

  /** Metadata contains IDs and names only; playback URLs are resolved with the current login. */
  void remember(String account, Catalog.Item item) {
    if(!item.id.startsWith("movie")&&!item.id.startsWith("episode"))return;
    try {
      String extension=android.net.Uri.parse(item.url).getLastPathSegment();
      extension=extension!=null&&extension.contains(".")?extension.substring(extension.lastIndexOf('.')+1):"mp4";
      if(!extension.matches("[A-Za-z0-9]{1,12}"))extension="mp4";
      JSONObject data=new JSONObject().put("account",key(account,"history-account"))
          .put("id",item.id).put("title",item.title).put("category",item.category)
          .put("description",item.description).put("extension",extension)
          .put("seriesId",item.seriesId).put("seriesTitle",item.seriesTitle)
          .put("season",item.seasonNumber).put("episode",item.episodeNumber);
      preferences.edit().putString(key(account,item.id)+".metadata",data.toString()).apply();
    }catch(Exception ignored){ /* A malformed optional title must not interrupt playback. */ }
  }

  void save(String account, String contentId, long position, long duration, boolean flush) {
    if (position < 0) return;
    String key = key(account, contentId);
    SharedPreferences.Editor editor = preferences.edit().putLong(key + ".position", position)
        .putBoolean(key+".completed",false).putLong(key+".updated",nextTime());
    // An unknown duration while buffering must not erase a previously known duration.
    if (duration > 0) editor.putLong(key + ".duration", duration);
    if (flush) editor.commit(); else editor.apply();
  }

  void complete(String account, String contentId) {
    String key = key(account, contentId);
    preferences.edit().remove(key + ".position").putBoolean(key+".completed",true)
        .putLong(key+".updated",nextTime()).commit();
  }

  void reset(String account,String contentId){
    String key=key(account,contentId);
    preferences.edit().remove(key+".position").remove(key+".duration")
        .putBoolean(key+".completed",false).putLong(key+".updated",nextTime()).commit();
  }

  boolean completed(String account,String contentId){return preferences.getBoolean(key(account,contentId)+".completed",false);}

  static final class Entry {
    final Catalog.Item item;
    final String extension;
    final long position,duration,updated;
    final boolean completed;
    Entry(Catalog.Item item,String extension,long position,long duration,long updated,boolean completed){this.item=item;this.extension=extension;this.position=position;this.duration=duration;this.updated=updated;this.completed=completed;}
    String title(){return item.seriesTitle.isEmpty()?item.title:item.seriesTitle;}
    String detail(){
      String episode=item.seasonNumber>0&&item.episodeNumber>0?String.format(Locale.ROOT,"T%02d · E%02d",item.seasonNumber,item.episodeNumber):item.title;
      String progress=completed?"Capítulo visto · ver episodios":"Continuar desde "+time(position)+(duration>0?" de "+time(duration):"");
      return item.id.startsWith("episode")?episode+"\n"+progress:progress;
    }
  }

  List<Entry> recent(String account){
    List<Entry> entries=new ArrayList<>();String accountKey=key(account,"history-account");
    for(java.util.Map.Entry<String,?> stored:preferences.getAll().entrySet()){
      if(!stored.getKey().endsWith(".metadata")||!(stored.getValue() instanceof String))continue;
      try{
        JSONObject data=new JSONObject((String)stored.getValue());if(!accountKey.equals(data.optString("account")))continue;
        String id=data.getString("id"),k=key(account,id);long updated=preferences.getLong(k+".updated",0);
        if(updated==0)continue;boolean done=completed(account,id);if(done&&id.startsWith("movie"))continue;
        Catalog.Item item=new Catalog.Item(id,data.getString("title"),data.getString("category"),"",data.optString("description"),"",data.optString("seriesId"),data.optString("seriesTitle"),data.optInt("season"),data.optInt("episode"));
        entries.add(new Entry(item,data.optString("extension","mp4"),position(account,id),preferences.getLong(k+".duration",0),updated,done));
      }catch(Exception ignored){ /* Skip an unreadable entry; other bookmarks remain available. */ }
    }
    entries.sort((a,b)->Long.compare(b.updated,a.updated));
    List<Entry> recent=new ArrayList<>();Set<String> groups=new HashSet<>();
    for(Entry entry:entries){String group=entry.item.seriesId.isEmpty()?entry.item.id:entry.item.seriesId;if(groups.add(group))recent.add(entry);if(recent.size()==100)break;}
    return recent;
  }

  private long nextTime(){long time=Math.max(System.currentTimeMillis(),preferences.getLong("clock",0)+1);preferences.edit().putLong("clock",time).apply();return time;}
  static String time(long ms){long seconds=Math.max(0,ms)/1000;return seconds>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60):String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}

  private static String key(String account, String contentId) {
    // The configured service can change hosts on failover. Keep the same bookmark for its user.
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256")
          .digest((account + "\u0000" + contentId).getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(64);
      for (byte b : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
      return result.toString();
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}

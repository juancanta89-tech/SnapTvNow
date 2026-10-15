package com.snaptvnow.tv;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Admin curated titles. Playback always uses the managed service, never page URLs. */
final class ExclusiveCatalog {
  static final String SECTION="Exclusivo de SNAPTV";
  private static boolean valid(String id){return id!=null&&id.matches("[a-z0-9][a-z0-9_-]{1,150}");}
  static boolean item(String id){return id!=null&&id.matches("(?:movie|series|episode)-exclusive-[a-z0-9][a-z0-9_-]{1,150}");}
  static String rawId(String id){return item(id)?id.replaceFirst("^(?:movie|series|episode)-exclusive-",""):"";}
  static String extension(String value){return value!=null&&value.matches("m3u8|mp4|mkv|webm|m4v|mov")?value:"m3u8";}
  static String playback(String server,String user,String pass,String id,String ext){
    if(!ProfileReference.managed(server)||!valid(id))throw new IllegalArgumentException("Título no disponible");
    return server.replaceAll("/$","")+"/exclusive/"+Uri.encode(user)+"/"+Uri.encode(pass)+"/"+id+"."+extension(ext);
  }
  static List<Catalog.Item> parse(String server,String user,String pass,JSONObject response){
    List<Catalog.Item> result=new ArrayList<>();if(!ProfileReference.managed(server))return result;
    JSONArray rows=response.optJSONArray("items");if(rows==null)return result;
    for(int i=0;i<rows.length();i++){
      JSONObject row=rows.optJSONObject(i);if(row==null)continue;String id=row.optString("exclusive_id"),type=row.optString("type");
      if(!valid(id)||!type.matches("movie|series"))continue;
      String path=type.equals("movie")?playback(server,user,pass,id,row.optString("container_extension")):"";
      String art=row.optString(type.equals("movie")?"stream_icon":"cover","");
      result.add(new Catalog.Item(type+"-exclusive-"+id,row.optString("name","Sin título"),type.equals("movie")?"Películas":"Series",path,row.optString("plot","Exclusivo de SNAPTV"),art).withProfile(ProfileReference.parse(row.optJSONObject("smn_profile"))));
    }
    return result;
  }
  static List<Catalog.Item> episodes(String server,String user,String pass,Catalog.Item series,JSONObject response){
    List<Catalog.Item> result=new ArrayList<>();JSONObject seasons=response.optJSONObject("episodes");
    if(!ProfileReference.managed(server)||!series.id.startsWith("series-exclusive-")||seasons==null)return result;
    for(java.util.Iterator<String> keys=seasons.keys();keys.hasNext();){JSONArray rows=seasons.optJSONArray(keys.next());if(rows==null)continue;
      for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;String id=row.optString("exclusive_id");int season=row.optInt("season"),episode=row.optInt("episode_num");
        if(!valid(id)||season<1||season>100||episode<1||episode>1000)continue;
        result.add(new Catalog.Item("episode-exclusive-"+id,row.optString("title","Episodio "+episode),"Series",playback(server,user,pass,id,row.optString("container_extension")),"Temporada "+season,series.artwork,series.id,series.title,season,episode).withProfile(ProfileReference.parse(row.optJSONObject("smn_profile"))));
      }
    }
    java.util.Collections.sort(result,EpisodeQueue.ORDER);return result;
  }
}

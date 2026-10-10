package com.snaptvnow.tv;
import org.json.JSONObject;
import java.net.URL;

/** Stable web identity, distinct from Xtream's globally mapped playback ID. */
final class ProfileReference {
 final String type,id,server,episodeId;
 ProfileReference(String type,String id,String server,String episodeId){this.type=type;this.id=id;this.server=server;this.episodeId=episodeId;}
 String key(){return type+":"+server+":"+id;}
 JSONObject json() throws Exception {JSONObject j=new JSONObject().put("type",type).put("id",id).put("server",server);if(!episodeId.isEmpty())j.put("episodeId",episodeId);return j;}
 static ProfileReference parse(JSONObject j){
  if(j==null)return null;String type=j.optString("type"),id=j.optString("id"),server=j.optString("server"),episode=j.optString("episodeId");
  if(!type.matches("movie|series|live")||!id.matches("[A-Za-z0-9_.:-]{1,180}")||!server.matches("[A-Za-z0-9_-]{1,80}")||!episode.isEmpty()&&!episode.matches("[A-Za-z0-9_.:-]{1,180}"))return null;
  return new ProfileReference(type,id,server,episode);
 }
 static boolean managed(String server){try{URL u=new URL(server);return u.getProtocol().equals("https")&&u.getHost().equalsIgnoreCase("api.snaptvnow.com")&&(u.getPort()==-1||u.getPort()==443)&&u.getUserInfo()==null&&u.getQuery()==null&&u.getRef()==null&&u.getPath().matches("/?");}catch(Exception ignored){return false;}}
}

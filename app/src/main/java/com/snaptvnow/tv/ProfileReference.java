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
 static boolean managed(String server){return managedOrigin(server)!=null;}
 static String managedOrigin(String server){
  try{
   URL url=new URL(server);
   if(!url.getProtocol().equals("https")||url.getPort()!=-1&&url.getPort()!=443||url.getUserInfo()!=null||url.getQuery()!=null||url.getRef()!=null||!url.getPath().matches("/?"))return null;
   if(url.getHost().equalsIgnoreCase("api.snaptvnow.com"))return "https://api.snaptvnow.com";
   if(url.getHost().equalsIgnoreCase("snapmovienow-edge.juancanta89.workers.dev"))return "https://snapmovienow-edge.juancanta89.workers.dev";
  }catch(Exception ignored){/* Only an official HTTPS endpoint can receive profile credentials. */}
  return null;
 }
}

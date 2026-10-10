package com.snaptvnow.tv;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Read-only client access for a subscriber line. Never place reseller credentials here. */
public final class XtreamClient {
  public static volatile String SERVER;
  private final String username,password,sourceServer;
  public final String expires,maxConnections;
  public XtreamClient(String username,String password,String expires,String maxConnections){this(SERVER,username,password,expires,maxConnections);}
  private XtreamClient(String server,String username,String password,String expires,String maxConnections){this.sourceServer=server;this.username=username;this.password=password;this.expires=expires;this.maxConnections=maxConnections;}
  String username(){return username;}
  String password(){return password;}
  String server(){return sourceServer;}
  private String request(String action,String extra) throws Exception {return request(sourceServer,username,password,action,extra,9000,13000,20_000_000);}
  private static String request(String username,String password,String action,String extra) throws Exception {
    return request(SERVER,username,password,action,extra,9000,13000,20_000_000);
  }
  private static String request(String server,String username,String password,String action,String extra,int connectMs,int readMs,int maxBytes) throws Exception {
    if(server==null)throw new Exception("No hay servidor configurado");
    Uri.Builder b=Uri.parse(server+"/player_api.php").buildUpon().appendQueryParameter("username",username).appendQueryParameter("password",password);
    if(!action.isEmpty())b.appendQueryParameter("action",action);
    if(extra!=null)b.appendQueryParameter(action.equals("get_series_info")?"series_id":"category_id",extra);
    URL url=new URL(b.build().toString());
    HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(connectMs);c.setReadTimeout(readMs);c.setRequestProperty("Accept","application/json");
    try {
      if(c.getResponseCode()!=200)throw new Exception("Servidor no disponible (HTTP "+c.getResponseCode()+")");
      ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
      try(InputStream in=c.getInputStream()){while((n=in.read(buf))!=-1){if(out.size()+n>maxBytes)throw new Exception("Respuesta demasiado grande");out.write(buf,0,n);}}
      return out.toString("UTF-8");
    }finally{c.disconnect();}
  }
  public static XtreamClient login(String username,String password) throws Exception {
    JSONObject root=new JSONObject(request(username,password,"",null));JSONObject info=root.optJSONObject("user_info");
    if(info==null||info.optInt("auth",0)!=1)throw new Exception("Usuario o contraseña de la línea no válidos");
    String status=info.optString("status","");if(!status.equalsIgnoreCase("Active"))throw new Exception("La línea está "+status);
    return new XtreamClient(SERVER,username,password,info.optString("exp_date",""),info.optString("max_connections",""));
  }
  public static XtreamClient loginAny(List<String> servers,String username,String password) throws Exception {
    if(servers.isEmpty())throw new Exception("No hay servidores habilitados");
    boolean rejected=false;String inactiveStatus=null;
    for(String server:servers){
      try{
        JSONObject root=new JSONObject(request(server,username,password,"",null,3000,4500,256_000));
        JSONObject info=root.optJSONObject("user_info");
        if(info==null||info.optInt("auth",0)!=1){rejected=true;continue;}
        String status=info.optString("status","");
        if(!status.equalsIgnoreCase("Active")){inactiveStatus=status;continue;}
        return new XtreamClient(server,username,password,info.optString("exp_date",""),info.optString("max_connections",""));
      }catch(Exception ignored){}
    }
    if(inactiveStatus!=null)throw new Exception("La línea está "+inactiveStatus);
    throw new Exception(rejected?"Ningún servidor aceptó esta línea. Revisa el usuario y la contraseña.":"No se encontró un servidor disponible. Inténtalo de nuevo.");
  }
  public XtreamClient reconnect(List<String> servers) throws Exception {return loginAny(servers,username,password);}
  public static final class Group {public final String id,name;public Group(String id,String name){this.id=id;this.name=name;}}
  public List<Group> categories(String section) throws Exception {
    String action=section.equals("Películas")?"get_vod_categories":section.equals("Series")?"get_series_categories":"get_live_categories";
    JSONArray list=new JSONArray(request(action,null));List<Group> groups=new ArrayList<>();
    for(int i=0;i<list.length();i++){JSONObject g=list.optJSONObject(i);if(g==null)continue;String id=g.optString("category_id","");if(id.matches("[0-9]+"))groups.add(new Group(id,g.optString("category_name","Categoría "+id)));}
    return groups;
  }
  public List<Catalog.Item> loadCategory(String section,String categoryId,String categoryName) throws Exception {
    String server=sourceServer;
    String action=section.equals("Películas")?"get_vod_streams":section.equals("Series")?"get_series":"get_live_streams";
    JSONArray list=new JSONArray(request(server,username,password,action,categoryId,9000,30000,40_000_000));
    java.util.Map<String,String> names=new java.util.HashMap<>();names.put(categoryId,categoryName);
    return parseItems(server,username,password,section,list,names);
  }
  List<Catalog.Item> loadAll(CatalogSearch.Scope scope) throws Exception {
    // Omitting category_id asks Xtream for the entire section, including unopened folders.
    String server=sourceServer;
    String action=scope==CatalogSearch.Scope.MOVIES?"get_vod_streams":scope==CatalogSearch.Scope.SERIES?"get_series":"get_live_streams";
    JSONArray list=new JSONArray(request(server,username,password,action,null,9000,30000,40_000_000));
    java.util.Map<String,String> names=new java.util.HashMap<>();
    String categories=scope==CatalogSearch.Scope.MOVIES?"get_vod_categories":scope==CatalogSearch.Scope.SERIES?"get_series_categories":"get_live_categories";
    JSONArray folders=new JSONArray(request(server,username,password,categories,null,9000,13000,20_000_000));
    for(int i=0;i<folders.length();i++){JSONObject folder=folders.optJSONObject(i);if(folder!=null)names.put(folder.optString("category_id"),folder.optString("category_name","Carpeta"));}
    return parseItems(server,username,password,scope.section,list,names);
  }
  static List<Catalog.Item> parseItems(String server,String username,String password,String section,JSONArray list,java.util.Map<String,String> categoryNames) {
    String kind=section.equals("Películas")?"movie":section.equals("Series")?"series":"live";
    List<Catalog.Item> out=new ArrayList<>();
    for(int k=0;k<list.length();k++){
      JSONObject j=list.optJSONObject(k);if(j==null)continue;
      String id=j.optString(kind.equals("series")?"series_id":"stream_id","");if(!id.matches("[0-9]+"))continue;
      String title=j.optString("name","Sin título");String ext=j.optString("container_extension","mp4").replaceAll("[^A-Za-z0-9]","");if(ext.isEmpty())ext="mp4";
      String path=kind.equals("series")?"":server+"/"+kind+"/"+Uri.encode(username)+"/"+Uri.encode(password)+"/"+id+"."+(kind.equals("live")?"m3u8":ext);
      String artwork=j.optString(kind.equals("series")?"cover":"stream_icon","");if(kind.equals("movie"))artwork=j.optString("stream_icon",j.optString("cover",""));
      String folder=categoryNames.get(j.optString("category_id",""));if(folder==null)folder=section;
      out.add(new Catalog.Item(kind+id,title,section,path,folder,artwork).withProfile(ProfileReference.managed(server)?ProfileReference.parse(j.optJSONObject("smn_profile")):null));
    }
    return out;
  }
  public List<Catalog.Item> episodes(Catalog.Item series) throws Exception {
    String id=series.id.replace("series","");JSONObject root=new JSONObject(request("get_series_info",id));
    return parseEpisodes(sourceServer,username,password,series,root);
  }
  static List<Catalog.Item> parseEpisodes(String server,String user,String pass,Catalog.Item series,JSONObject root){
    JSONObject seasons=root.optJSONObject("episodes");List<Catalog.Item> result=new ArrayList<>();if(seasons==null)return result;
    for(java.util.Iterator<String> keys=seasons.keys();keys.hasNext();){String season=keys.next();JSONArray arr=seasons.optJSONArray(season);if(arr==null)continue;
      for(int k=0;k<arr.length();k++){JSONObject e=arr.optJSONObject(k);if(e==null)continue;String ep=e.optString("id","");if(!ep.matches("[0-9]+"))continue;
        String ext=e.optString("container_extension","mp4").replaceAll("[^A-Za-z0-9]","");if(ext.isEmpty())ext="mp4";
        int seasonNumber=e.optInt("season",numericSeason(season)),episodeNumber=e.optInt("episode_num",k+1);
        String path=server+"/series/"+Uri.encode(user)+"/"+Uri.encode(pass)+"/"+ep+"."+ext;
        result.add(new Catalog.Item("episode"+ep,e.optString("title",series.title+" · Episodio "+episodeNumber),"Series",path,"Temporada "+season,series.artwork,series.id,series.title,seasonNumber,episodeNumber).withProfile(ProfileReference.managed(server)?ProfileReference.parse(e.optJSONObject("smn_profile")):null));
      }
    }
    java.util.Collections.sort(result,EpisodeQueue.ORDER);return result;
  }
  private static int numericSeason(String season){try{return Integer.parseInt(season);}catch(Exception ignored){return 0;}}
  Catalog.Item resume(PlaybackHistory.Entry entry) throws Exception {
    Catalog.Item item=entry.item;String kind=item.id.startsWith("movie")?"movie":item.id.startsWith("episode")?"series":"";
    String id=item.id.replaceFirst("^(movie|episode)","");if(kind.isEmpty()||!id.matches("[0-9]+")||sourceServer==null)throw new Exception("Título no disponible");
    String ext=entry.extension.matches("[A-Za-z0-9]{1,12}")?entry.extension:"mp4";
    String path=sourceServer+"/"+kind+"/"+Uri.encode(username)+"/"+Uri.encode(password)+"/"+id+"."+ext;
    return new Catalog.Item(item.id,item.title,item.category,path,item.description,item.artwork,item.seriesId,item.seriesTitle,item.seasonNumber,item.episodeNumber).withProfile(item.profile);
  }
  public String epg(Catalog.Item channel) throws Exception {
    if(!channel.id.startsWith("live"))return "Guía no disponible";
    Uri.Builder b=Uri.parse(sourceServer+"/player_api.php").buildUpon().appendQueryParameter("username",username).appendQueryParameter("password",password).appendQueryParameter("action","get_short_epg").appendQueryParameter("limit","24").appendQueryParameter("stream_id",channel.id.substring(4));
    HttpURLConnection c=(HttpURLConnection)new URL(b.build().toString()).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(9000);c.setReadTimeout(13000);
    try {if(c.getResponseCode()!=200)return "Guía no disponible";ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;try(InputStream in=c.getInputStream()){while((n=in.read(buf))!=-1){if(out.size()+n>100000)throw new Exception("Guía demasiado grande");out.write(buf,0,n);}}JSONObject root=new JSONObject(out.toString("UTF-8"));return EpgGuide.format(root.optJSONArray("epg_listings"),System.currentTimeMillis());}finally{c.disconnect();}
  }
}

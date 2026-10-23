package com.snaptvnow.tv;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Read-only client access for a subscriber line. Never place reseller credentials here. */
public final class XtreamClient {
  interface LoginRequest { String authenticate(String server,String username,String password) throws Exception; }
  public interface LoginProgress { void onAttempt(int number,int total); }
  static final class HttpFailure extends Exception {
    final int status;
    HttpFailure(int status){super("Respuesta del servidor (HTTP "+status+")");this.status=status;}
  }
  public static volatile String SERVER;
  private final String username,password,sourceServer;
  public final String expires,maxConnections;
  public XtreamClient(String username,String password,String expires,String maxConnections){this(SERVER,username,password,expires,maxConnections);}
  XtreamClient(String server,String username,String password,String expires,String maxConnections){this.sourceServer=server;this.username=username;this.password=password;this.expires=expires;this.maxConnections=maxConnections;}
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
    if(extra!=null)b.appendQueryParameter((action.equals("get_series_info")||action.equals("get_exclusive_series_info"))?"series_id":"category_id",extra);
    try(okhttp3.Response response=SharedNetwork.execute(new okhttp3.Request.Builder().url(b.build().toString()).header("Accept","application/json").build(),connectMs,readMs)){
      if(response.code()!=200)throw new HttpFailure(response.code());
      return SharedNetwork.text(response,maxBytes);
    }
  }

  public static XtreamClient login(String username,String password) throws Exception {
    JSONObject root=new JSONObject(request(username,password,"",null));JSONObject info=root.optJSONObject("user_info");
    if(info==null||info.optInt("auth",0)!=1)throw new Exception("Usuario o contraseña de la línea no válidos");
    String status=info.optString("status","");if(!status.equalsIgnoreCase("Active"))throw new Exception("La línea está "+status);
    return new XtreamClient(SERVER,username,password,info.optString("exp_date",""),info.optString("max_connections",""));
  }
  public static XtreamClient loginAny(List<String> servers,String username,String password) throws Exception {
    return loginAny(servers,username,password,null);
  }
  public static XtreamClient loginAny(List<String> servers,String username,String password,LoginProgress progress) throws Exception {
    return attemptLogin(servers,username,password,(server,user,pass)->request(server,user,pass,"",null,5000,8000,256_000),progress);
  }
  static XtreamClient attemptLogin(List<String> servers,String username,String password,LoginRequest transport,LoginProgress progress) throws Exception {
    if(servers.isEmpty())throw new Exception("No hay servidores habilitados");
    boolean rejected=false,denied=false,limited=false;String inactiveStatus=null;
    for(int index=0;index<servers.size();index++){
      if(progress!=null)progress.onAttempt(index+1,servers.size());
      try{
        String server=AppConfig.validate(servers.get(index));
        JSONObject root=new JSONObject(transport.authenticate(server,username,password));
        JSONObject info=root.optJSONObject("user_info");
        if(info==null)continue;
        if(info.optInt("auth",0)!=1){rejected=true;continue;}
        String status=info.optString("status","");
        if(!status.equalsIgnoreCase("Active")){inactiveStatus=status;continue;}
        return new XtreamClient(server,username,password,info.optString("exp_date",""),info.optString("max_connections",""));
      }catch(HttpFailure error){
        if(error.status==401)rejected=true;
        else if(error.status==403)denied=true;
        else if(error.status==429)limited=true;
      }catch(Exception ignored){}
    }
    if(inactiveStatus!=null)throw new Exception("La línea está "+inactiveStatus);
    if(limited)throw new Exception("Demasiados intentos de acceso. Espera unos minutos y vuelve a entrar.");
    if(denied&&!rejected)throw new Exception("Los servidores rechazaron el acceso. Contacta a tu administrador.");
    throw new Exception(rejected?"Ningún servidor aceptó esta línea. Revisa el usuario y la contraseña.":"No se encontró un servidor disponible. Inténtalo de nuevo.");
  }
  public XtreamClient reconnect(List<String> servers) throws Exception {return loginAny(reconnectServers(servers,sourceServer),username,password);}
  static List<String> reconnectServers(List<String> enabled,String previous){
    List<String> ordered=new ArrayList<>(enabled);
    if(previous!=null&&ordered.remove(previous))ordered.add(0,previous);
    return ordered;
  }
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
    List<Catalog.Item> result=parseItems(server,username,password,scope.section,list,names);
    if(ProfileReference.managed(server)&&scope!=CatalogSearch.Scope.CHANNELS){
      try{for(Catalog.Item item:exclusives())if(item.category.equals(scope.section))result.add(item);}catch(Exception ignored){ /* The exclusive screen offers a separate retry. */ }
    }
    return result;
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
      out.add(new Catalog.Item(kind+id,title,section,path,folder,artwork)
        .withLiveIdentity(kind.equals("live")?j.optString("category_id",""):"",kind.equals("live")?j.optString("epg_channel_id",""):"")
        .withProfile(ProfileReference.managed(server)?ProfileReference.parse(j.optJSONObject("smn_profile")):null));
    }
    return out;
  }
  public List<Catalog.Item> exclusives() throws Exception {
    if(!ProfileReference.managed(sourceServer))return new ArrayList<>();
    return ExclusiveCatalog.parse(sourceServer,username,password,new JSONObject(request("get_exclusive_catalog",null)));
  }
  public List<Catalog.Item> episodes(Catalog.Item series) throws Exception {
    if(ExclusiveCatalog.item(series.id))return ExclusiveCatalog.episodes(sourceServer,username,password,series,new JSONObject(request("get_exclusive_series_info",ExclusiveCatalog.rawId(series.id))));
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
    if(ExclusiveCatalog.item(item.id)&&!item.id.startsWith("series"))return new Catalog.Item(item.id,item.title,item.category,ExclusiveCatalog.playback(sourceServer,username,password,ExclusiveCatalog.rawId(item.id),entry.extension),item.description,item.artwork,item.seriesId,item.seriesTitle,item.seasonNumber,item.episodeNumber).withProfile(item.profile);
    String id=item.id.replaceFirst("^(movie|episode)","");if(kind.isEmpty()||!id.matches("[0-9]+")||sourceServer==null)throw new Exception("Título no disponible");
    String ext=entry.extension.matches("[A-Za-z0-9]{1,12}")?entry.extension:"mp4";
    String path=sourceServer+"/"+kind+"/"+Uri.encode(username)+"/"+Uri.encode(password)+"/"+id+"."+ext;
    return new Catalog.Item(item.id,item.title,item.category,path,item.description,item.artwork,item.seriesId,item.seriesTitle,item.seasonNumber,item.episodeNumber).withProfile(item.profile);
  }
  public String epg(Catalog.Item channel) throws Exception {
    if(!channel.id.startsWith("live"))return "Guía no disponible";
    Uri.Builder b=Uri.parse(sourceServer+"/player_api.php").buildUpon().appendQueryParameter("username",username).appendQueryParameter("password",password).appendQueryParameter("action","get_short_epg").appendQueryParameter("limit","24").appendQueryParameter("stream_id",channel.id.substring(4));
    try(okhttp3.Response response=SharedNetwork.execute(new okhttp3.Request.Builder().url(b.build().toString()).header("Accept","application/json").build(),9000,13000)){
      if(response.code()!=200)return "Guía no disponible";
      return EpgGuide.format(new JSONObject(SharedNetwork.text(response,100000)).optJSONArray("epg_listings"),System.currentTimeMillis());
    }
  }
}

package com.snaptvnow.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** The public endpoint contains a server address only, never subscriber credentials. */
final class AppConfig {
  private static final String CONFIG_URL="https://snaptvnow-control.juancanta89.chatgpt.site/api/app-config";
  private static final String PREF="app_config", SERVER="last_server", SERVERS="servers", WORKING="working_server";

  static List<String> load(Context context) throws Exception {
    SharedPreferences prefs=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
    try {
      HttpURLConnection connection=(HttpURLConnection)new URL(CONFIG_URL).openConnection();
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);
      connection.setRequestProperty("Accept","application/json");
      try {
        if(connection.getResponseCode()!=200)throw new Exception("Configuración no disponible");
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int count;
        try(InputStream in=connection.getInputStream()){
          while((count=in.read(buffer))!=-1){
            if(out.size()+count>4096)throw new Exception("Configuración inválida");
            out.write(buffer,0,count);
          }
        }
        JSONObject config=new JSONObject(out.toString("UTF-8"));
        JSONArray list=config.optJSONArray("servers");
        if(list==null){list=new JSONArray();list.put(config.getString("server"));}
        if(list.length()<1||list.length()>8)throw new Exception("Lista de servidores inválida");
        LinkedHashSet<String> unique=new LinkedHashSet<>();
        for(int i=0;i<list.length();i++)unique.add(validate(list.getString(i)));
        List<String> servers=new ArrayList<>(unique);
        prefs.edit().putString(SERVERS,new JSONArray(servers).toString()).putString(SERVER,servers.get(0)).apply();
        return servers;
      }finally{connection.disconnect();}
    }catch(Exception error){
      String saved=prefs.getString(SERVERS,null);
      if(saved!=null){
        JSONArray list=new JSONArray(saved);List<String> servers=new ArrayList<>();
        for(int i=0;i<list.length()&&i<8;i++)try{servers.add(validate(list.getString(i)));}catch(Exception ignored){}
        if(!servers.isEmpty())return servers;
      }
      String last=prefs.getString(SERVER,null);
      if(last!=null)try{List<String> single=new ArrayList<>();single.add(validate(last));return single;}catch(Exception ignored){}
      List<String> own=new ArrayList<>();own.add("https://api.snaptvnow.com");return own;
    }
  }
  static void rememberWorking(Context context,String server){context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(WORKING,server).apply();}
  static String lastWorking(Context context,List<String> servers){
    String last=context.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(WORKING,null);
    return last!=null&&servers.contains(last)?last:servers.get(0);
  }
  static String validate(String raw) throws Exception {
    URL url=new URL(raw.trim());
    if(!url.getProtocol().equals("https"))throw new Exception("Se requiere HTTPS para proteger tu cuenta");
    if(url.getHost().isEmpty()||url.getUserInfo()!=null||!url.getPath().matches("/?")
      ||url.getQuery()!=null||url.getRef()!=null)throw new Exception("Dirección de servidor inválida");
    return url.toString().replaceAll("/$","");
  }
}

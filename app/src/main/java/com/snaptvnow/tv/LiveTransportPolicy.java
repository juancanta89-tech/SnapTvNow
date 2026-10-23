package com.snaptvnow.tv;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/** Managed live channels support both transports. Learn only from a rendered frame. */
final class LiveTransportPolicy {
  private final Map<String,String> working=new LinkedHashMap<>();
  private final Map<String,androidx.media3.common.MediaItem> prepared=new LinkedHashMap<>();
  // Metadata only: no MediaSource.prepare, fetch, decoder or extra provider slot.
  void prepare(String original){mediaItem(original,preferred(original));}
  androidx.media3.common.MediaItem mediaItem(String original,String played){
    androidx.media3.common.MediaItem item=prepared.get(original);
    if(item==null||!item.localConfiguration.uri.toString().equals(played)){
      item=androidx.media3.common.MediaItem.fromUri(played);prepared.remove(original);prepared.put(original,item);
      while(prepared.size()>8)prepared.remove(prepared.keySet().iterator().next());
    }
    return item;
  }
  private android.content.SharedPreferences preferences;
  private String salt;
  private static final long TTL=7L*24*3600000;
  void attach(android.content.SharedPreferences prefs){preferences=prefs;salt=prefs.getString("salt",null);if(salt==null){salt=java.util.UUID.randomUUID().toString();prefs.edit().putString("salt",salt).apply();}}
  private String key(String url){
    try{byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest((salt+"|v2|"+replace(url,"ts")).getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder out=new StringBuilder("route:");for(byte b:hash)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}catch(Exception e){return null;}
  }
  private String learned(String url){
    if(preferences==null||!managed(url))return null;
    String key=key(url);if(key==null)return null;
    String value=preferences.getString(key,"");String[] parts=value.split(":");
    try{long age=System.currentTimeMillis()-Long.parseLong(parts[1]);if(parts.length==2&&age>=0&&age<TTL&&(parts[0].equals("ts")||parts[0].equals("m3u8")))return replace(url,parts[0]);}catch(Exception ignored){}
    if(!value.isEmpty())preferences.edit().remove(key).apply();return null;
  }
  private void prune(){
    if(preferences==null)return;
    java.util.List<java.util.Map.Entry<String,?>> rows=new java.util.ArrayList<>();
    for(java.util.Map.Entry<String,?> row:preferences.getAll().entrySet())if(row.getKey().startsWith("route:"))rows.add(row);
    rows.sort(java.util.Comparator.comparingLong(row->{try{return Long.parseLong(String.valueOf(row.getValue()).split(":")[1]);}catch(Exception e){return 0L;}}));
    android.content.SharedPreferences.Editor edit=preferences.edit();for(int i=0;i<rows.size()-64;i++)edit.remove(rows.get(i).getKey());edit.apply();
  }
  void failed(String original,String played){prepared.remove(original);if(!managed(original))return;String known=working.get(original);if(known!=null&&known.equals(played))working.remove(original);if(preferences!=null){String key=key(original);if(key!=null)preferences.edit().remove(key).apply();}}

  private static boolean managed(String url){
    try{URI u=URI.create(url);return "https".equals(u.getScheme())&&"api.snaptvnow.com".equalsIgnoreCase(u.getHost())&&(u.getPort()==-1||u.getPort()==443)&&u.getUserInfo()==null&&u.getQuery()==null&&u.getFragment()==null&&u.getRawPath().matches("/live/[^/]+/[^/]+/[0-9]+\\.(m3u8|ts)");}catch(Exception ignored){return false;}
  }
  String preferred(String url){if(!managed(url))return url;String saved=working.get(url);if(saved==null)saved=learned(url);return saved!=null?saved:replace(url,"ts");}
  static String alternative(String url){return managed(url)?replace(url,url.endsWith(".ts")?"m3u8":"ts"):null;}
  private static String replace(String url,String ext){return url.substring(0,url.lastIndexOf('.')+1)+ext;}
  void rendered(String original,String played){
    if(!managed(original)||!managed(played)||!replace(original,"ts").equals(replace(played,"ts")))return;
    working.remove(original);working.put(original,played);while(working.size()>64)working.remove(working.keySet().iterator().next());
    if(preferences!=null){String key=key(original);if(key!=null){preferences.edit().putString(key,(played.endsWith(".ts")?"ts":"m3u8")+":"+System.currentTimeMillis()).apply();prune();}}

  }
}

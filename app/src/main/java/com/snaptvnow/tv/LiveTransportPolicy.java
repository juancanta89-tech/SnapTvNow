package com.snaptvnow.tv;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/** Managed live channels support both transports. Learn only from a rendered frame. */
final class LiveTransportPolicy {
  private final Map<String,String> working=new LinkedHashMap<>();
  private static boolean managed(String url){
    try{URI u=URI.create(url);return "https".equals(u.getScheme())&&"api.snaptvnow.com".equalsIgnoreCase(u.getHost())&&(u.getPort()==-1||u.getPort()==443)&&u.getUserInfo()==null&&u.getQuery()==null&&u.getFragment()==null&&u.getRawPath().matches("/live/[^/]+/[^/]+/[0-9]+\\.(m3u8|ts)");}catch(Exception ignored){return false;}
  }
  String preferred(String url){return working.containsKey(url)?working.get(url):managed(url)?replace(url,"ts"):url;}
  static String alternative(String url){return managed(url)?replace(url,url.endsWith(".ts")?"m3u8":"ts"):null;}
  private static String replace(String url,String ext){return url.substring(0,url.lastIndexOf('.')+1)+ext;}
  void rendered(String original,String played){
    if(!managed(original)||!managed(played)||!replace(original,"ts").equals(replace(played,"ts")))return;
    working.remove(original);working.put(original,played);while(working.size()>64)working.remove(working.keySet().iterator().next());
  }
}

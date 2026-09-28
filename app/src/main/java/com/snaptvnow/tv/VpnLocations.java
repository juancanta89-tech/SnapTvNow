package com.snaptvnow.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Public location labels only. Profiles and service credentials never appear in this response. */
final class VpnLocations {
  private static final String URL_STRING="https://snaptvnow-control.juancanta89.chatgpt.site/api/vpn-locations";
  private static final String CONNECT_URL="https://snaptvnow-control.juancanta89.chatgpt.site/api/vpn-connect";
  static final class Location {
    final String id,name,country,city,protocol;
    Location(JSONObject json) throws Exception {
      id=json.getString("id");name=json.getString("name");country=json.getString("country");
      city=json.getString("city");protocol=json.getString("protocol");
      if(id.length()!=36||name.length()>80||country.length()>80||city.length()>80||!protocol.matches("UDP|TCP"))
        throw new Exception("Ubicación VPN inválida");
    }
    String label(){return country+" · "+city+" ("+protocol+")";}
  }
  static List<Location> load() throws Exception {
    HttpURLConnection connection=(HttpURLConnection)new URL(URL_STRING).openConnection();
    connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
    connection.setInstanceFollowRedirects(false);
    try {
      if(connection.getResponseCode()!=200)throw new Exception("No se pudieron cargar las ubicaciones VPN");
      ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int count;
      try(InputStream in=connection.getInputStream()){
        while((count=in.read(buffer))!=-1){
          if(out.size()+count>8192)throw new Exception("Lista VPN inválida");
          out.write(buffer,0,count);
        }
      }
      JSONArray rows=new JSONObject(out.toString("UTF-8")).getJSONArray("locations");
      if(rows.length()>8)throw new Exception("Lista VPN inválida");
      List<Location> result=new ArrayList<>();
      for(int i=0;i<rows.length();i++)result.add(new Location(rows.getJSONObject(i)));
      return result;
    } finally {connection.disconnect();}
  }
  static final class Config {
    final String profile,username,password;
    Config(String profile,String username,String password){this.profile=profile;this.username=username;this.password=password;}
  }
  static Config config(Location location,String username,String password) throws Exception {
    HttpURLConnection connection=(HttpURLConnection)new URL(CONNECT_URL).openConnection();
    connection.setRequestMethod("POST");connection.setDoOutput(true);
    connection.setConnectTimeout(7000);connection.setReadTimeout(19000);
    connection.setInstanceFollowRedirects(false);
    connection.setRequestProperty("Content-Type","application/json");
    connection.setRequestProperty("Cache-Control","no-store");
    byte[] request=new JSONObject().put("id",location.id).put("username",username).put("password",password).toString().getBytes("UTF-8");
    connection.setFixedLengthStreamingMode(request.length);
    try {
      try(OutputStream out=connection.getOutputStream()){out.write(request);}
      if(connection.getResponseCode()!=200)throw new Exception(connection.getResponseCode()==401?"La línea no pudo autorizar el VPN.":"El perfil VPN no está disponible ("+connection.getResponseCode()+").");
      ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
      try(InputStream in=connection.getInputStream()){
        while((count=in.read(buffer))!=-1){if(out.size()+count>45000)throw new Exception("Perfil VPN demasiado grande");out.write(buffer,0,count);}
      }
      JSONObject json=new JSONObject(out.toString("UTF-8"));
      String profile=json.getString("profile"),vpnUser=json.getString("vpnUsername"),vpnPass=json.getString("vpnPassword");
      if(profile.length()>32768||profile.indexOf('\0')>=0||vpnUser.length()>200||vpnPass.length()>200||
          vpnUser.contains("\n")||vpnPass.contains("\n")||vpnUser.contains("\r")||vpnPass.contains("\r"))throw new Exception("Perfil VPN inválido");
      if(!java.util.regex.Pattern.compile("(?m)^\\s*auth-user-pass\\s*$").matcher(profile).find())throw new Exception("Falta autenticación OpenVPN");
      return new Config(profile,vpnUser,vpnPass);
    }finally{connection.disconnect();}
  }
}

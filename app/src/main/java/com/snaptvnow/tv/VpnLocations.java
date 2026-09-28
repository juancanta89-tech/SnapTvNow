package com.snaptvnow.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Public location labels only. Profiles and service credentials never appear in this response. */
final class VpnLocations {
  private static final String URL_STRING="https://snaptvnow-control.juancanta89.chatgpt.site/api/vpn-locations";
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
}

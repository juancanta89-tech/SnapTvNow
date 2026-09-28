package com.snaptvnow.tv;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class DirectMessages {
  private static final String ENDPOINT="https://snaptvnow-control.juancanta89.chatgpt.site/api/direct-messages";
  static JSONObject request(XtreamClient client,String action,int id) throws Exception {
    JSONObject payload=new JSONObject().put("action",action).put("username",client.username()).put("password",client.password());
    if(id>0)payload.put("id",id);
    HttpURLConnection connection=(HttpURLConnection)new URL(ENDPOINT).openConnection();
    connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setInstanceFollowRedirects(false);
    connection.setConnectTimeout(6000);connection.setReadTimeout(13000);
    connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
    connection.setRequestProperty("Accept","application/json");
    try {
      byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);
      try(OutputStream output=connection.getOutputStream()){output.write(bytes);}
      if(connection.getResponseCode()!=200)throw new Exception("No se pudo registrar la respuesta. Inténtalo de nuevo.");
      ByteArrayOutputStream result=new ByteArrayOutputStream();byte[] buffer=new byte[2048];int n;
      try(InputStream input=connection.getInputStream()){while((n=input.read(buffer))!=-1){if(result.size()+n>20000)throw new Exception("Respuesta inválida");result.write(buffer,0,n);}}
      return new JSONObject(result.toString("UTF-8"));
    }finally{connection.disconnect();}
  }
}

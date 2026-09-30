package com.snaptvnow.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

final class Advertisement {
  private static final String BASE="https://snaptvnow-control.juancanta89.chatgpt.site";
  static final class Slide {
    final String id;final int seconds;
    Slide(String id,int seconds){this.id=id;this.seconds=seconds;}
  }
  static List<Slide> playlist() throws Exception {
    byte[] bytes=download(BASE+"/api/ads",20_000);
    JSONArray array=new JSONObject(new String(bytes,"UTF-8")).getJSONArray("slides");
    List<Slide> slides=new ArrayList<>();
    for(int i=0;i<array.length()&&i<8;i++){
      JSONObject item=array.optJSONObject(i);if(item==null)continue;
      String id=item.optString("id","");int seconds=item.optInt("seconds",5);
      if((id.equals("legacy")||id.matches("[a-f0-9-]{36}"))&&seconds>=2&&seconds<=60)slides.add(new Slide(id,seconds));
    }
    return slides;
  }
  static Bitmap image(Slide slide) throws Exception {
    byte[] bytes=download(BASE+"/api/ad-image?id="+slide.id,4_000_000);
    BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
    BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);options.inJustDecodeBounds=false;
    options.inSampleSize=Math.max(1,Math.max(options.outWidth/1200,options.outHeight/900));
    Bitmap result=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
    if(result==null)throw new Exception("Imagen inválida");
    return result;
  }
  private static byte[] download(String address,int limit) throws Exception {
    HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
    connection.setConnectTimeout(5000);connection.setReadTimeout(7000);connection.setInstanceFollowRedirects(false);
    try {
      if(connection.getResponseCode()!=200)throw new Exception("Publicidad no disponible");
      ByteArrayOutputStream data=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
      try(InputStream in=connection.getInputStream()){while((n=in.read(buffer))!=-1){if(data.size()+n>limit)throw new Exception("Respuesta demasiado grande");data.write(buffer,0,n);}}
      return data.toByteArray();
    }finally{connection.disconnect();}
  }
}

package com.snaptvnow.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small bounded image loader for artwork supplied by the subscriber catalog. */
final class Artwork {
  private static final ExecutorService workers=Executors.newFixedThreadPool(3);
  private static final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(6*1024*1024){
    @Override protected int sizeOf(String key,Bitmap value){return value.getByteCount();}
  };
  static void into(ImageView view,String address){
    if(address==null||!(address.startsWith("https://")||address.startsWith("http://")))return;
    view.setTag(address);
    Bitmap stored=cache.get(address);if(stored!=null){view.setImageBitmap(stored);return;}
    workers.execute(()->{
      HttpURLConnection connection=null;
      try{
        connection=(HttpURLConnection)new URL(address).openConnection();connection.setConnectTimeout(6000);connection.setReadTimeout(7000);
        if(connection.getResponseCode()!=200)return;
        if(connection.getContentLength()>1_500_000)return;
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;
        try(InputStream stream=connection.getInputStream()){
          while((n=stream.read(block))!=-1){if(out.size()+n>1_500_000)return;out.write(block,0,n);}
        }
        byte[] bytes=out.toByteArray();BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(options.outWidth<=0||options.outHeight<=0)return;
        options.inJustDecodeBounds=false;options.inSampleSize=Math.max(1,Math.max(options.outWidth/480,options.outHeight/640));
        Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(bitmap==null)return;cache.put(address,bitmap);
        view.post(()->{if(address.equals(view.getTag()))view.setImageBitmap(bitmap);});
      }catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}
    });
  }
  private Artwork(){}
}

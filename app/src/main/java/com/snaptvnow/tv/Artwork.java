package com.snaptvnow.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.Context;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
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
    if(!valid(address))return;
    view.setTag(address);Bitmap stored=cache.get(address);if(stored!=null){view.setImageBitmap(stored);return;}
    workers.execute(()->{
      Bitmap bitmap=load(address);if(bitmap!=null)view.post(()->{if(address.equals(view.getTag()))view.setImageBitmap(bitmap);});
    });
  }
  /** Store only bounded private thumbnail pixels, never provider URLs or credentials. */
  static void rememberHistory(Context context,String key,String address){
    if(!valid(address))return;
    workers.execute(()->{Bitmap bitmap=load(address);if(bitmap!=null)storeHistory(context,key,bitmap);});
  }
  static void intoHistory(ImageView view,String key,String address){
    String tag="history:"+key;view.setTag(tag);
    workers.execute(()->{
      Bitmap stored=BitmapFactory.decodeFile(historyFile(view.getContext(),key).getPath());
      if(stored!=null)view.post(()->{if(tag.equals(view.getTag()))view.setImageBitmap(stored);});
      if(valid(address)){
        Bitmap fresh=load(address);if(fresh==null)return;storeHistory(view.getContext(),key,fresh);
        view.post(()->{if(tag.equals(view.getTag()))view.setImageBitmap(fresh);});
      }
    });
  }
  static File historyFile(Context context,String key){
    if(!key.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid artwork key");
    return new File(new File(context.getCacheDir(),"continue_artwork"),key+".jpg");
  }
  static synchronized void storeHistory(Context context,String key,Bitmap bitmap){
    File target=historyFile(context,key),directory=target.getParentFile(),temporary=new File(directory,key+".tmp");
    try{
      if(!directory.isDirectory()&&!directory.mkdirs())return;
      Bitmap thumbnail=bitmap;int width=bitmap.getWidth(),height=bitmap.getHeight();
      float scale=Math.min(1f,320f/Math.max(width,height));
      if(scale<1)thumbnail=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(width*scale)),Math.max(1,Math.round(height*scale)),true);
      try(FileOutputStream out=new FileOutputStream(temporary)){if(!thumbnail.compress(Bitmap.CompressFormat.JPEG,75,out))return;}
      if(!temporary.renameTo(target))return;target.setLastModified(System.currentTimeMillis());
      File[] covers=directory.listFiles((folder,name)->name.endsWith(".jpg"));
      if(covers!=null&&covers.length>32){java.util.Arrays.sort(covers,(a,b)->Long.compare(a.lastModified(),b.lastModified()));for(int i=0;i<covers.length-32;i++)covers[i].delete();}
    }catch(Exception ignored){}finally{temporary.delete();}
  }
  private static boolean valid(String address){return address!=null&&(address.startsWith("https://")||address.startsWith("http://"));}
  private static Bitmap load(String address){
    Bitmap stored=cache.get(address);if(stored!=null)return stored;
    HttpURLConnection connection=null;
    try{
        connection=(HttpURLConnection)new URL(address).openConnection();connection.setConnectTimeout(6000);connection.setReadTimeout(7000);
        if(connection.getResponseCode()!=200)return null;
        if(connection.getContentLength()>1_500_000)return null;
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;
        try(InputStream stream=connection.getInputStream()){
          while((n=stream.read(block))!=-1){if(out.size()+n>1_500_000)return null;out.write(block,0,n);}
        }
        byte[] bytes=out.toByteArray();BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(options.outWidth<=0||options.outHeight<=0)return null;
        options.inJustDecodeBounds=false;options.inSampleSize=Math.max(1,Math.max(options.outWidth/480,options.outHeight/640));
        Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(bitmap!=null)cache.put(address,bitmap);return bitmap;
      }catch(Exception ignored){return null;}finally{if(connection!=null)connection.disconnect();}
  }
  private Artwork(){}
}

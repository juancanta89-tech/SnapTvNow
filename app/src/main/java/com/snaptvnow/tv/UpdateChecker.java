package com.snaptvnow.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/** Automatic offers are throttled; an explicit check always reaches the panel. */
final class UpdateChecker {
  static final String ENDPOINT="https://snaptvnow-control.juancanta89.chatgpt.site/api/app-update";
  private static final AtomicBoolean automaticChecking=new AtomicBoolean();
  interface Callback { void completed(Update update,String error); }
  static final class Update {
    final int code; final String name,url,notes,sha256; final long size;
    Update(int code,String name,String url,String notes,String sha256,long size){this.code=code;this.name=name;this.url=url;this.notes=notes;this.sha256=sha256;this.size=size;}
    void open(Activity activity){AppUpdateActivity.open(activity,url,code,name,sha256,size);}
  }
  static final class Task {
    volatile boolean cancelled; volatile HttpURLConnection connection;
    void cancel(){cancelled=true;HttpURLConnection active=connection;if(active!=null)active.disconnect();}
  }
  static long installedCode(Activity activity) throws Exception {return UpdateInstaller.version(activity.getPackageManager().getPackageInfo(activity.getPackageName(),0));}
  static String installedName(Activity activity){
    try{String name=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName;return name==null?"No disponible":name;}
    catch(Exception ignored){return "No disponible";}
  }
  static Update parse(JSONObject response,long installed) throws IOException {
    if(!response.has("update")||(!response.isNull("update")&&!(response.opt("update") instanceof JSONObject)))throw new IOException("El panel no devolvió una respuesta válida. Reintenta.");
    JSONObject data=response.optJSONObject("update");if(data==null)return null;
    long code=data.optLong("versionCode",0);String name=data.optString("versionName","").trim(),url=data.optString("url",""),notes=data.optString("notes",""),hash=data.optString("sha256","");long size=data.optLong("size",0);
    if(code<1||code>Integer.MAX_VALUE||name.isEmpty()||name.length()>40||notes.length()>300||size<0||size>UpdateDownload.MAX_BYTES||(!hash.isEmpty()&&!hash.matches("[a-fA-F0-9]{64}")))throw new IOException("Los datos de la actualización no son válidos.");
    UpdateDownload.apkUrl(url);
    return code<=installed?null:new Update((int)code,name,url,notes,hash.toLowerCase(java.util.Locale.ROOT),size);
  }
  static Update fetch(Task task,long installed) throws Exception {
    HttpURLConnection connection=(HttpURLConnection)new URL(ENDPOINT).openConnection();task.connection=connection;
    try {
      if(task.cancelled)throw new IOException("Comprobación cancelada.");
      connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(10000);connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("Cache-Control","no-cache");
      if(connection.getResponseCode()!=200)throw new IOException("No se pudo comprobar la actualización. Revisa tu conexión y reintenta.");
      ByteArrayOutputStream bytes=new ByteArrayOutputStream();
      try(InputStream stream=connection.getInputStream()){byte[] buffer=new byte[1024];int count;while((count=stream.read(buffer))!=-1){if(task.cancelled||bytes.size()+count>8192)throw new IOException("No se pudo leer la actualización.");bytes.write(buffer,0,count);}}
      return parse(new JSONObject(bytes.toString("UTF-8")),installed);
    }finally{task.connection=null;connection.disconnect();}
  }
  static Task manual(Activity activity,Callback callback){
    Task task=new Task();
    new Thread(()->{
      Update result=null;String error=null;
      try{result=fetch(task,installedCode(activity));if(!task.cancelled)activity.getSharedPreferences("app_updates",Activity.MODE_PRIVATE).edit().putLong("checked_at",System.currentTimeMillis()).apply();}
      catch(Exception failure){error=failure instanceof IOException?failure.getMessage():"No se pudo comprobar la actualización. Reintenta.";}
      final Update update=result;final String reason=error;
      activity.runOnUiThread(()->{if(!task.cancelled&&!activity.isFinishing()&&!activity.isDestroyed())callback.completed(update,reason);});
    },"snaptvnow-check-update").start();return task;
  }
  static void check(Activity activity){
    SharedPreferences preferences=activity.getSharedPreferences("app_updates",Activity.MODE_PRIVATE);
    if(System.currentTimeMillis()-preferences.getLong("checked_at",0)<60L*60*1000||!automaticChecking.compareAndSet(false,true))return;
    new Thread(()->{
      try {
        Update update=fetch(new Task(),installedCode(activity));preferences.edit().putLong("checked_at",System.currentTimeMillis()).apply();
        if(update==null||System.currentTimeMillis()-preferences.getLong("dismissed_"+update.code,0)<24L*60*60*1000)return;
        activity.runOnUiThread(()->{
          if(activity.isFinishing()||activity.isDestroyed()||!activity.hasWindowFocus())return;
          new AlertDialog.Builder(activity).setTitle("Actualización disponible · "+update.name).setMessage(update.notes+"\n\nSNAPTVNOW descargará la actualización. Al terminar, confirma la instalación en tu dispositivo.")
            .setNegativeButton("Más tarde",(dialog,which)->preferences.edit().putLong("dismissed_"+update.code,System.currentTimeMillis()).apply())
            .setPositiveButton("Descargar e instalar",(dialog,which)->update.open(activity)).show();
        });
      }catch(Exception ignored){}finally{automaticChecking.set(false);}
    },"snaptvnow-update-offer").start();
  }
}

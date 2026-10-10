package com.snaptvnow.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Public update metadata never contains subscriber credentials. Installation remains an Android user decision. */
final class UpdateChecker {
  private static final String ENDPOINT="https://snaptvnow-control.juancanta89.chatgpt.site/api/app-update";
  private static boolean checking;

  static void check(Activity activity) {
    SharedPreferences preferences=activity.getSharedPreferences("app_updates",Activity.MODE_PRIVATE);
    long now=System.currentTimeMillis();
    if(checking||now-preferences.getLong("checked_at",0)<60L*60*1000)return;
    checking=true;
    new Thread(()->{
      try {
        HttpURLConnection connection=(HttpURLConnection)new URL(ENDPOINT).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
        try {
          if(connection.getResponseCode()!=200)return;
          ByteArrayOutputStream bytes=new ByteArrayOutputStream();
          try(InputStream stream=connection.getInputStream()){
            byte[] buffer=new byte[1024];int count;
            while((count=stream.read(buffer))!=-1){
              if(bytes.size()+count>4096)return;
              bytes.write(buffer,0,count);
            }
          }
          JSONObject update=new JSONObject(bytes.toString("UTF-8")).optJSONObject("update");
          preferences.edit().putLong("checked_at",System.currentTimeMillis()).apply();
          if(update==null)return;
          int code=update.optInt("versionCode",0);
          int installed=Build.VERSION.SDK_INT>=28
            ?(int)activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).getLongVersionCode()
            :activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionCode;
          if(code<=installed)return;
          String name=update.optString("versionName",""),notes=update.optString("notes","");
          String link=update.optString("url","");
          UpdateDownload.apkUrl(link);
          long lastDismissed=preferences.getLong("dismissed_"+code,0);
          if(System.currentTimeMillis()-lastDismissed<24L*60*60*1000)return;
          activity.runOnUiThread(()->{
            if(activity.isFinishing()||activity.isDestroyed())return;
            new AlertDialog.Builder(activity).setTitle("Actualización disponible · "+name)
              .setMessage(notes+"\n\nSNAPTVNOW descargará la actualización. Al terminar, confirma la instalación en tu dispositivo.")
              .setNegativeButton("Más tarde",(dialog,which)->preferences.edit().putLong("dismissed_"+code,System.currentTimeMillis()).apply())
              .setPositiveButton("Descargar", (dialog,which)->{
                AppUpdateActivity.open(activity,link,code,name);
              }).show();
          });
        }finally{connection.disconnect();}
      }catch(Exception ignored){}finally{checking=false;}
    }).start();
  }
}

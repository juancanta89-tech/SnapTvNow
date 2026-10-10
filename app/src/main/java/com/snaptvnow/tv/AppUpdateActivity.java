package com.snaptvnow.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

/** A user-started update screen; no browser, Google services, or storage permission required. */
public class AppUpdateActivity extends Activity {
  static final String URL_EXTRA="update_url",CODE_EXTRA="update_code",NAME_EXTRA="update_name";
  private static final int INSTALL_REQUEST=201,PERMISSION_REQUEST=202;
  private String link;
  private int code,generation;
  private File apk;
  private TextView message;
  private ProgressBar progress;
  private Button action,close;
  private UpdateDownload download;
  private boolean ready,foreground,autoInstall=true,awaitingPermission;
  private long lastProgressTime;

  static void open(Activity activity,String url,int versionCode,String versionName){
    activity.startActivity(new Intent(activity,AppUpdateActivity.class)
      .putExtra(URL_EXTRA,url).putExtra(CODE_EXTRA,versionCode).putExtra(NAME_EXTRA,versionName));
  }
  @Override protected void onCreate(Bundle state){
    super.onCreate(state);
    link=getIntent().getStringExtra(URL_EXTRA);code=getIntent().getIntExtra(CODE_EXTRA,0);
    if(state!=null){autoInstall=state.getBoolean("auto_install",false);awaitingPermission=state.getBoolean("awaiting_permission",false);}
    buildScreen(getIntent().getStringExtra(NAME_EXTRA));
    try {
      if(code<=0||link==null)throw new IOException("No se pudo leer la actualización publicada.");
      UpdateDownload.apkUrl(link);
    }catch(IOException failure){fail(failure.getMessage());return;}
    SharedPreferences saved=preferences();
    String name=saved.getString("file","");
    boolean cached=saved.getInt("code",0)==code&&link.equals(saved.getString("url",""))
      &&name.matches("update-[0-9]+-[a-f0-9-]+\\.apk");
    apk=new File(getFilesDir(),"updates/"+(cached?name:"update-"+code+"-"+UUID.randomUUID()+".apk"));
    beginDownload();
  }
  private SharedPreferences preferences(){return getSharedPreferences("app_update_download",MODE_PRIVATE);}
  private void buildScreen(String name){
    LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
    layout.setGravity(Gravity.CENTER_VERTICAL);layout.setPadding(dp(32),dp(24),dp(32),dp(24));
    layout.setBackgroundColor(Color.rgb(6,22,39));
    TextView title=new TextView(this);title.setText("Actualizar SNAPTVNOW"+(name==null?"":" · "+name));
    title.setTextSize(26);title.setTextColor(Color.WHITE);layout.addView(title);
    message=new TextView(this);message.setTextSize(18);message.setTextColor(Color.rgb(185,210,222));
    message.setPadding(0,dp(24),0,dp(20));layout.addView(message);
    progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
    progress.setMax(100);progress.setIndeterminate(true);layout.addView(progress);
    action=new Button(this);action.setText("Descargando…");action.setEnabled(false);
    action.setOnClickListener(view->{if(ready)attemptInstall();else if(apk!=null){autoInstall=true;beginDownload();}});
    layout.addView(action,new LinearLayout.LayoutParams(-1,dp(56)));
    close=new Button(this);close.setText("Cancelar");close.setOnClickListener(view->finish());
    layout.addView(close,new LinearLayout.LayoutParams(-1,dp(56)));
    setContentView(layout);close.requestFocus();
  }
  protected void beginDownload(){
    if(download!=null)return;
    ready=false;lastProgressTime=0;
    action.setEnabled(false);action.setText("Descargando…");close.setText("Cancelar");
    progress.setVisibility(View.VISIBLE);progress.setIndeterminate(true);
    message.setText("Descargando la actualización. Puedes cancelar en cualquier momento.");
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    int current=++generation;
    UpdateDownload task=new UpdateDownload();download=task;
    new Thread(()->{
      try {
        boolean cached=apk.isFile()&&preferences().getInt("code",0)==code
          &&link.equals(preferences().getString("url",""));
        if(!cached)task.download(link,apk,(bytes,total)->postProgress(current,bytes,total));
        runOnUiThread(()->{if(valid(current))message.setText("Comprobando la actualización…");});
        verifyArchive(apk,code);
        // A stopped/destroyed screen never opens an installer from the background.
        runOnUiThread(()->{if(valid(current)){
          preferences().edit().putInt("code",code).putString("url",link).putString("file",apk.getName()).apply();
          download=null;ready=true;progress.setVisibility(View.GONE);
          getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
          message.setText("Actualización descargada. Confirma la instalación para actualizar SNAPTVNOW.");
          action.setEnabled(true);action.setText("Instalar actualización");close.setText("Más tarde");action.requestFocus();
          continueInstallation();
        }});
      }catch(IOException failure){
        runOnUiThread(()->{if(valid(current)){
          apk.delete();preferences().edit().clear().apply();
          download=null;fail(failure.getMessage());
        }});
      }
    },"snaptvnow-update").start();
  }
  protected void verifyArchive(File file,int expectedCode) throws IOException {UpdateInstaller.verify(this,file,expectedCode);}
  private boolean valid(int current){return current==generation&&!isFinishing()&&!isDestroyed();}
  private void postProgress(int current,long bytes,long total){
    long now=System.nanoTime();
    if(bytes>0&&bytes!=total&&now-lastProgressTime<250_000_000L)return;
    lastProgressTime=now;
    runOnUiThread(()->{if(valid(current)){
      progress.setIndeterminate(total<=0);
      if(total>0)progress.setProgress((int)Math.min(100,bytes*100/total));
      message.setText(total>0
        ?String.format(Locale.ROOT,"Descargando… %d%% · %.1f de %.1f MB",Math.min(100,bytes*100/total),bytes/1048576d,total/1048576d)
        :String.format(Locale.ROOT,"Descargando… %.1f MB",bytes/1048576d));
    }});
  }
  private void fail(String reason){
    ready=false;autoInstall=false;
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    progress.setVisibility(View.GONE);message.setText(reason==null?"No se pudo descargar la actualización. Reintenta.":reason);
    action.setText("Reintentar");action.setEnabled(apk!=null);close.setText("Volver");action.requestFocus();
  }
  private void attemptInstall(){
    if(!ready||!foreground)return;
    autoInstall=false;
    if(UpdateInstaller.needsPermission(this)){
      message.setText("Permite que SNAPTVNOW instale actualizaciones. En Fire TV: Mi Fire TV → Opciones para desarrolladores → Instalar apps desconocidas → SNAPTVNOW. Después vuelve a esta pantalla.");
      action.setText("Permitir instalación");
      awaitingPermission=true;
      try{startActivityForResult(UpdateInstaller.permissionIntent(this),PERMISSION_REQUEST);}
      catch(ActivityNotFoundException|SecurityException unavailable){
        try{startActivityForResult(new Intent(Settings.ACTION_SETTINGS),PERMISSION_REQUEST);}
        catch(ActivityNotFoundException|SecurityException ignored){awaitingPermission=false;}
      }
      return;
    }
    action.setText("Instalar actualización");
    try{startActivityForResult(UpdateInstaller.installIntent(this,apk),INSTALL_REQUEST);}
    catch(ActivityNotFoundException|SecurityException|IllegalArgumentException failure){
      message.setText("No se pudo abrir el instalador del dispositivo. Comprueba el permiso para instalar apps y vuelve a intentarlo.");
    }
  }
  private void continueInstallation(){
    if(!ready||!foreground)return;
    if(awaitingPermission){
      awaitingPermission=false;
      if(!UpdateInstaller.needsPermission(this))attemptInstall();
    }else if(autoInstall){autoInstall=false;attemptInstall();}
  }
  @Override protected void onResume(){
    super.onResume();foreground=true;
    continueInstallation();
  }
  @Override protected void onPause(){foreground=false;super.onPause();}
  @Override protected void onSaveInstanceState(Bundle state){
    state.putBoolean("auto_install",autoInstall);state.putBoolean("awaiting_permission",awaitingPermission);
    super.onSaveInstanceState(state);
  }
  @Override protected void onDestroy(){
    generation++;if(download!=null){download.cancel();download=null;}
    super.onDestroy();
  }
  private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}

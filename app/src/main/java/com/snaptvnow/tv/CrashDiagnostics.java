package com.snaptvnow.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/** Private, credential-free report for crashes while starting the VPN engine. */
final class CrashDiagnostics {
  private static final String PREFS="vpn_diagnostics";
  private static volatile boolean installed;

  static void install(Context context){
    if(installed)return;
    installed=true;
    Context app=context.getApplicationContext();
    Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
      try{
        StringBuilder report=new StringBuilder("Android ").append(Build.VERSION.SDK_INT)
          .append(" · Hilo ").append(thread.getName()).append('\n')
          .append(error.getClass().getName()).append('\n');
        Throwable current=error;
        for(int cause=0;current!=null&&cause<3;cause++,current=current.getCause()){
          if(cause>0)report.append("Causa: ").append(current.getClass().getName()).append('\n');
          StackTraceElement[] frames=current.getStackTrace();
          for(int i=0;i<Math.min(frames.length,14);i++){
            StackTraceElement f=frames[i];
            report.append(f.getClassName()).append('.').append(f.getMethodName())
              .append(':').append(f.getLineNumber()).append('\n');
          }
        }
        app.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
          .putString("crash",report.substring(0,Math.min(report.length(),5000))).commit();
      }catch(Throwable ignored){}
      if(previous!=null)previous.uncaughtException(thread,error);
      else android.os.Process.killProcess(android.os.Process.myPid());
    });
  }
  static void starting(Context context){context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    .edit().putBoolean("starting",true).putString("state","INICIANDO").commit();}
  static void state(Context context,String code){context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    .edit().putString("state",code).apply();}
  static void finished(Context context){context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    .edit().putBoolean("starting",false).apply();}
  static String consume(Context context){
    SharedPreferences prefs=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    String crash=prefs.getString("crash",null);boolean starting=prefs.getBoolean("starting",false);
    String lastState=prefs.getString("state","DESCONOCIDO");
    prefs.edit().remove("crash").putBoolean("starting",false).apply();
    if(crash!=null)return "La app se cerró. Último estado VPN: "+lastState+". Copia este diagnóstico para revisar el error:\n\n"+crash;
    return starting?"La app se cerró durante el inicio de la VPN. Último estado: "+lastState+". No hubo una excepción Java registrada; se necesita el registro de Android para conocer la causa exacta.":null;
  }
}

package com.snaptvnow.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/** Private, credential-free crash evidence, retained until explicitly acknowledged. */
final class CrashDiagnostics {
  private static final String PREFS="vpn_diagnostics";
  private static volatile boolean installed;

  static void install(Context context){
    if(installed)return;
    installed=true;
    Context app=context.getApplicationContext();
    Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
      try{record(app,thread,error);}catch(Throwable ignored){}
      if(previous!=null)previous.uncaughtException(thread,error);
      else android.os.Process.killProcess(android.os.Process.myPid());
    });
  }
  static synchronized void record(Context context,Thread thread,Throwable error){
        SharedPreferences prefs=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        long at=Math.max(System.currentTimeMillis(),prefs.getLong("crash_at",0)+1);
        StringBuilder report=new StringBuilder("Registro ").append(at).append(" · Android ").append(Build.VERSION.SDK_INT)
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
        prefs.edit().putLong("crash_at",at).remove("acknowledged_crash")
          .putString("crash",report.substring(0,Math.min(report.length(),5000))).commit();
  }
  static void starting(Context context){context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    .edit().putBoolean("starting",true).putString("state","INICIANDO").commit();}
  static void state(Context context,String code){context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    .edit().putString("state",code).apply();}
  static void finished(Context context){context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    .edit().putBoolean("starting",false).apply();}
  static synchronized String consume(Context context){
    SharedPreferences prefs=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    String crash=prefs.getString("crash",null);boolean starting=prefs.getBoolean("starting",false);
    String lastState=prefs.getString("state","DESCONOCIDO");
    if(crash!=null&&!crash.equals(prefs.getString("acknowledged_crash",null)))return "La app registró un cierre inesperado. Estado VPN registrado: "+lastState+". Copia este diagnóstico para revisar el error:\n\n"+crash;
    return starting?"La app se cerró durante el inicio de la VPN. Último estado: "+lastState+". No hubo una excepción Java registrada; se necesita el registro de Android para conocer la causa exacta.":null;
  }
  static synchronized void acknowledge(Context context,String displayed){
    SharedPreferences prefs=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    String crash=prefs.getString("crash",null);
    if(displayed==null||!(crash!=null?displayed.endsWith("\n\n"+crash):displayed.equals(consume(context))))return;
    prefs.edit().putString("acknowledged_crash",crash==null?"":crash).putBoolean("starting",false).commit();
  }
  static String report(Context context){
    SharedPreferences prefs=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    String crash=prefs.getString("crash",null),version="No disponible";
    try{version=context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName;}catch(Exception ignored){}
    return "Versión instalada: "+version+"\nDispositivo: "+Build.MANUFACTURER+" "+Build.MODEL+" · Android "+Build.VERSION.SDK_INT+"\n\n"
      +(crash==null?"No hay una excepción Java registrada. Eso no descarta un cierre nativo o del sistema.":"Última excepción Java conservada:\n"+crash)
      +"\n\n"+ProcessExitDiagnostics.report(context);
  }
}

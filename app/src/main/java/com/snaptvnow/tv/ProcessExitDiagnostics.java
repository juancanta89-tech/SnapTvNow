package com.snaptvnow.tv;

import android.content.Context;
import android.os.Build;

/** OS evidence for this package only. No raw descriptions, traces, URLs or credentials. */
final class ProcessExitDiagnostics {
  static String report(Context context){
    if(Build.VERSION.SDK_INT<30)return "Este sistema no ofrece el historial de cierres de Android 11 o posterior; el motivo del cierre no está confirmado.";
    try{return Api30.report(context);}catch(Exception|LinkageError unavailable){return "El sistema no permitió consultar el historial de cierres; el motivo no está confirmado.";}
  }
  static String reason(int value){
    switch(value){
      case 1:return "Salida solicitada por el proceso";case 2:return "Terminación por señal";
      case 3:return "Memoria insuficiente";case 4:return "Excepción Java";case 5:return "Fallo de código nativo";
      case 6:return "Aplicación sin responder (ANR)";case 7:return "Fallo de inicialización";
      case 8:return "Cambio de permisos";case 9:return "Exceso de recursos";
      case 10:return "Solicitud del usuario o del sistema; también puede corresponder a una actualización en sistemas anteriores";
      case 11:return "Usuario detenido";case 12:return "Terminación de una dependencia";
      case 16:return "Reemplazo de la APK por una actualización";
      default:return "Otro motivo del sistema ("+value+")";
    }
  }
  private static final class Api30 {
    static String report(Context context){
      android.app.ActivityManager manager=(android.app.ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
      if(manager==null)return "Historial de cierres no disponible.";
      java.util.List<android.app.ApplicationExitInfo> exits=manager.getHistoricalProcessExitReasons(context.getPackageName(),0,3);
      if(exits.isEmpty())return "El sistema no devolvió cierres anteriores; eso no confirma su ausencia.";
      StringBuilder out=new StringBuilder("Últimas terminaciones registradas por Android (pueden incluir la instalación o una salida normal):");
      java.text.DateFormat date=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",java.util.Locale.ROOT);
      for(android.app.ApplicationExitInfo exit:exits)out.append('\n').append(date.format(new java.util.Date(exit.getTimestamp())))
        .append(" · ").append(reason(exit.getReason())).append(" · código ").append(exit.getStatus());
      return out.toString();
    }
  }
  private ProcessExitDiagnostics(){}
}

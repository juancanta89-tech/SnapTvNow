package com.snaptvnow.autotext;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneId;

final class Scheduler {
    static PendingIntent pending(Context c,long id) { Intent i=new Intent(c,AlarmReceiver.class).putExtra("id",id);return PendingIntent.getBroadcast(c,(int)id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); }
    static void cancel(Context c,long id) { ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pending(c,id)); }
    static void schedule(Context c,Store.Task t) {
        if(!"Programar".equals(t.type)||!Store.PENDING.equals(t.status)||t.at<=System.currentTimeMillis())return;
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi=pending(c,t.id);
        if(Build.VERSION.SDK_INT>=31 && am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,t.at,pi);
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,t.at,pi);
    }
    static void restore(Context c) { for(Store.Task t:new Store(c).tasks()) if("Programar".equals(t.type)&&Store.PENDING.equals(t.status)) { if(t.at>System.currentTimeMillis())schedule(c,t);else {t.status=Store.FAILED;t.error="La hora pasó mientras el equipo estaba apagado";new Store(c).save(t);} } }
    static long next(long old,String rule) {
        ZonedDateTime d=Instant.ofEpochMilli(old).atZone(ZoneId.systemDefault());
        do { switch(rule) {case "Cada hora": d=d.plusHours(1);break;case "Diario":d=d.plusDays(1);break;case "Semanal":d=d.plusWeeks(1);break;case "Mensual":d=d.plusMonths(1);break;case "Anual":d=d.plusYears(1);break;default:return 0;} } while(d.toInstant().toEpochMilli()<=System.currentTimeMillis());
        return d.toInstant().toEpochMilli();
    }
}

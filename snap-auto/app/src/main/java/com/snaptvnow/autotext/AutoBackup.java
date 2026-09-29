package com.snaptvnow.autotext;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.Calendar;

/** User-selected document tree, seven rotating daily SQLite snapshots. */
final class AutoBackup {
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("backup_schedule",Context.MODE_PRIVATE);}
    static boolean configured(Context c){return prefs(c).getString("tree","").length()>0;}
    static void schedule(Context c){
        Intent intent=new Intent(c,Receiver.class);PendingIntent pi=PendingIntent.getBroadcast(c,99713,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarms=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(!configured(c)){alarms.cancel(pi);return;}
        Calendar next=Calendar.getInstance();next.add(Calendar.DAY_OF_MONTH,1);next.set(Calendar.HOUR_OF_DAY,3);next.set(Calendar.MINUTE,0);next.set(Calendar.SECOND,0);next.set(Calendar.MILLISECOND,0);
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.getTimeInMillis(),pi);
    }
    static void backup(Context c)throws Exception {
        String location=prefs(c).getString("tree","");if(location.isEmpty())throw new IllegalStateException("Elige una carpeta de respaldo primero");
        Uri tree=Uri.parse(location);String filename="snap_auto_dia_"+Calendar.getInstance().get(Calendar.DAY_OF_WEEK)+".sqlite3";
        Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));Uri target=null;
        try(Cursor cursor=c.getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
            if(cursor!=null)while(cursor.moveToNext())if(filename.equals(cursor.getString(1))){target=DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0));break;}
        }
        if(target==null)target=DocumentsContract.createDocument(c.getContentResolver(),parent,"application/x-sqlite3",filename);
        if(target==null)throw new IllegalStateException("No se pudo crear la copia en esa carpeta");
        File source=new Store(c).snapshot();
        try(FileInputStream in=new FileInputStream(source);OutputStream out=c.getContentResolver().openOutputStream(target,"wt")){
            if(out==null)throw new IllegalStateException("No se pudo escribir el respaldo");Store.copy(in,out);
            prefs(c).edit().putLong("last_ok",System.currentTimeMillis()).putString("last_error","").apply();
        }catch(Exception ex){prefs(c).edit().putString("last_error",ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage()).apply();throw ex;}
        finally{source.delete();}
    }
    public static final class Receiver extends BroadcastReceiver {
        @Override public void onReceive(Context c,Intent intent){PendingResult result=goAsync();new Thread(()->{try{backup(c);}catch(Exception ignored){}finally{schedule(c);result.finish();}},"snap-backup").start();}
    }
}

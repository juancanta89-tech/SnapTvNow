package com.snaptvnow.autotext;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

final class Notices {
    static void assisted(Context c,Store.Task t) {
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("assisted","Mensajes listos",NotificationManager.IMPORTANCE_HIGH));
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        Intent i=new Intent(c,MainActivity.class).putExtra("open_task",t.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(c,(int)t.id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(c,"assisted").setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle("Mensaje listo para WhatsApp").setContentText("Toca para abrir y enviar a "+t.recipient).setContentIntent(pi).setAutoCancel(true).build();nm.notify((int)t.id,n);
    }
}

package com.snaptvnow.autotext;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;
import java.util.ArrayList;

final class Messaging {
    static boolean valid(String phone) { return phone!=null && phone.matches("\\+?[0-9]{7,15}"); }
    static String render(String body,String name) { return body.replace("{NOMBRE}",name==null?"":name); }
    static void send(Context c,Store.Task t,String phone,String body,boolean scheduled) {
        Store db=new Store(c);
        if(!valid(phone)) {db.log(t.id,phone,Store.FAILED,"Número inválido");if(scheduled)fail(c,t,"Número inválido");return;}
        if(c.checkSelfPermission(Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED){db.log(t.id,phone,Store.FAILED,"Falta permiso SEND_SMS");if(scheduled)fail(c,t,"Falta permiso SEND_SMS");return;}
        try {
            SmsManager sms=SmsManager.getDefault();
            ArrayList<String> parts=sms.divideMessage(body);
            if(parts.isEmpty())throw new IllegalArgumentException("Mensaje vacío");
            String job=t.id+"_"+System.nanoTime();
            ArrayList<PendingIntent> sent=new ArrayList<>();
            for(int n=0;n<parts.size();n++) {
                Intent i=new Intent(c,SmsResultReceiver.class).putExtra("id",t.id).putExtra("phone",phone).putExtra("job",job).putExtra("total",parts.size()).putExtra("scheduled",scheduled);
                i.setAction("com.snaptvnow.autotext.SENT."+job+"."+n);
                sent.add(PendingIntent.getBroadcast(c,0,i,PendingIntent.FLAG_ONE_SHOT|PendingIntent.FLAG_IMMUTABLE));
            }
            sms.sendMultipartTextMessage(phone,null,parts,sent,null);
        } catch(Exception e) {db.log(t.id,phone,Store.FAILED,e.getClass().getSimpleName());if(scheduled)fail(c,t,"No se pudo iniciar el SMS");}
    }
    static void fail(Context c,Store.Task t,String reason) { t.status=Store.FAILED;t.error=reason;new Store(c).save(t); }
}

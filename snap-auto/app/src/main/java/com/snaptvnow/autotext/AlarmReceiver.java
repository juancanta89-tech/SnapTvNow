package com.snaptvnow.autotext;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        Store db=new Store(c); Store.Task t=db.get(i.getLongExtra("id",-1));
        if(t==null||!Store.PENDING.equals(t.status)||!"Programar".equals(t.type))return;
        if(t.at>System.currentTimeMillis()+60000) {Scheduler.schedule(c,t);return;}
        if("WhatsApp".equals(t.channel)) {
            Notices.assisted(c,t);
            t.status="Acción necesaria";db.save(t);db.log(t.id,t.recipient,"Acción necesaria","Abrir WhatsApp para enviar");
        } else if("SMS".equals(t.channel)&&db.claimScheduledSms(t.id,t.at)) {
            Messaging.send(c,t,t.recipient,Messaging.render(t.body,t.name),true);
        }
    }
}

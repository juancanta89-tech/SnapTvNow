package com.snaptvnow.autotext;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import java.time.LocalTime;

public final class IncomingSmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        if(c.checkSelfPermission(Manifest.permission.RECEIVE_SMS)!=PackageManager.PERMISSION_GRANTED)return;
        SmsMessage[] parts=Telephony.Sms.Intents.getMessagesFromIntent(i);if(parts==null||parts.length==0)return;
        String phone=parts[0].getOriginatingAddress();if(!Messaging.valid(phone))return;
        StringBuilder message=new StringBuilder();for(SmsMessage part:parts)message.append(part.getMessageBody());
        Store db=new Store(c);
        for(Store.Task t:db.tasks()) {
            if(!Store.PENDING.equals(t.status)||!"SMS".equals(t.channel))continue;
            if(!"Responder".equals(t.type)&&!"Reenviar".equals(t.type))continue;
            if(!t.recipient.isEmpty()&&!t.recipient.equals(phone))continue;
            if(!t.keyword.isEmpty()&&!message.toString().toLowerCase().contains(t.keyword.toLowerCase()))continue;
            if(!inWindow(t.start,t.end))continue;
            if(!db.throttle(t.id,phone,Math.max(1,t.cooldown)))continue;
            if("Responder".equals(t.type)) Messaging.send(c,t,phone,t.body,false);
            else if(Messaging.valid(t.body)&&!t.body.equals(phone)) Messaging.send(c,t,t.body,"SMS recibido de "+phone+": "+message,false);
        }
    }
    static boolean inWindow(String a,String b) { if(a.isEmpty()||b.isEmpty())return true;try {LocalTime now=LocalTime.now(),start=LocalTime.parse(a),end=LocalTime.parse(b);return start.isBefore(end)?(!now.isBefore(start)&&now.isBefore(end)):(!now.isBefore(start)||now.isBefore(end));}catch(Exception e){return false;} }
}

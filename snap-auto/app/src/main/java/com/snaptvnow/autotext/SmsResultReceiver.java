package com.snaptvnow.autotext;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public final class SmsResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        String job=i.getStringExtra("job"),phone=i.getStringExtra("phone");if(job==null)return;
        int total=i.getIntExtra("total",1);long id=i.getLongExtra("id",-1);
        SharedPreferences p=c.getSharedPreferences("sms_results",Context.MODE_PRIVATE);
        int count=p.getInt(job+".count",0)+1, bad=p.getInt(job+".bad",0)+(getResultCode()==Activity.RESULT_OK?0:1);
        if(count<total) {p.edit().putInt(job+".count",count).putInt(job+".bad",bad).apply();return;}
        p.edit().remove(job+".count").remove(job+".bad").apply();
        Store db=new Store(c);boolean success=bad==0;
        db.log(id,phone,success?Store.DONE:Store.FAILED,success?"Aceptado por la red móvil":"Error de envío: "+getResultCode());
        if(i.getBooleanExtra("scheduled",false)) {Store.Task t=db.get(id);if(t==null)return;
            if(success) {long next=Scheduler.next(t.at,t.repeat);if(next>0) {t.at=next;t.status=Store.PENDING;Scheduler.schedule(c,t);}else t.status=Store.DONE;t.error="";}
            else {t.status=Store.FAILED;t.error="La red móvil rechazó el SMS";}
            db.save(t);
        }
    }
}

package com.snaptvnow.autotext;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

/** Reads Auto Text's observed 2026 backup schema. Never executes a restored task. */
final class AutoTextImporter {
    static final class Summary {
        int originals, sms, whatsapp, replies, missing, recipients, templates, groups, created;
        String fingerprint;
    }
    private static boolean hasColumns(SQLiteDatabase db,String table,String... required) {
        java.util.HashSet<String> names=new java.util.HashSet<>();
        try(Cursor c=db.rawQuery("PRAGMA table_info(\""+table+"\")",null)) {while(c.moveToNext())names.add(c.getString(1));}
        for(String name:required)if(!names.contains(name))return false;
        return true;
    }
    private static void validate(SQLiteDatabase db) {
        try(Cursor c=db.rawQuery("PRAGMA quick_check",null)) {if(!c.moveToFirst()||!"ok".equalsIgnoreCase(c.getString(0)))throw new IllegalArgumentException("SQLite dañado");}
        if(!hasColumns(db,"futy","id","content","recipient","feature_type","scheduled_time","status")||!hasColumns(db,"message_template","id","content","type")||!hasColumns(db,"group_recipient","id","name","recipient"))
            throw new IllegalArgumentException("No es un backup compatible de Auto Text");
    }
    private static String fingerprint(File file)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] bytes=new byte[65536];
        try(FileInputStream in=new FileInputStream(file)){int n;while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);}
        StringBuilder s=new StringBuilder();for(byte b:digest.digest())s.append(String.format(java.util.Locale.US,"%02x",b&255));return s.toString();
    }
    private static int count(SQLiteDatabase db,String table){try(Cursor c=db.rawQuery("SELECT count(*) FROM "+table,null)){c.moveToFirst();return c.getInt(0);}}
    static Summary preview(File file)throws Exception {
        Summary result=new Summary();result.fingerprint=fingerprint(file);
        try(SQLiteDatabase source=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)) {
            validate(source);result.originals=count(source,"futy");result.templates=count(source,"message_template");result.groups=count(source,"group_recipient");
            try(Cursor c=source.query("futy",new String[]{"feature_type","recipient"},null,null,null,null,null)) {
                while(c.moveToNext()) {String type=c.getString(0);if("schedule_sms".equals(type))result.sms++;else if("schedule_whatsapp".equals(type))result.whatsapp++;else result.replies++;
                    if(type!=null&&type.startsWith("schedule_")){int valid=0;for(String[] part:recipients(c.getString(1)))if(Messaging.valid(part[1]))valid++;result.recipients+=valid;if(valid==0)result.missing++;}
                }
            }
        }
        return result;
    }
    static java.util.List<String[]> recipients(String raw) {
        java.util.List<String[]> out=new java.util.ArrayList<>();
        if(raw==null)return out;
        for(String segment:raw.split(";;;",-1)) {
            String[] parts=segment.split(",,,",-1);if(parts.length<2)continue;
            String digits=parts[1].replaceAll("[^0-9]","");
            String phone=parts[1].trim().startsWith("+")?"+"+digits:digits;
            out.add(new String[]{parts[0].trim(),phone});
        }
        return out;
    }
    private static String rawRow(Cursor c)throws Exception {
        JSONObject o=new JSONObject();
        for(int n=0;n<c.getColumnCount();n++) {
            Object value;
            switch(c.getType(n)) {case Cursor.FIELD_TYPE_NULL:value=JSONObject.NULL;break;case Cursor.FIELD_TYPE_INTEGER:value=c.getLong(n);break;case Cursor.FIELD_TYPE_FLOAT:value=c.getDouble(n);break;default:value=c.getString(n);}
            o.put(c.getColumnName(n),value);
        }
        return o.toString();
    }
    private static void archive(SQLiteDatabase dest,Cursor c,String table,String hash)throws Exception {
        ContentValues v=new ContentValues();v.put("fingerprint",hash);v.put("source_table",table);v.put("source_id",c.getLong(c.getColumnIndexOrThrow("id")));v.put("payload",rawRow(c));dest.insertOrThrow("legacy_rows",null,v);
    }
    private static void contact(SQLiteDatabase dest,String name,String phone) {
        if(!Messaging.valid(phone))return;ContentValues v=new ContentValues();v.put("name",name.isEmpty()?"Auto Text":name);v.put("phone",phone);dest.insertWithOnConflict("contacts",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    static void backfillLinks(SQLiteDatabase database) {
        try(Cursor rows=database.rawQuery("SELECT fingerprint,source_id,payload FROM legacy_rows WHERE source_table='futy'",null)) {
            while(rows.moveToNext()) {
                try {
                    JSONObject o=new JSONObject(rows.getString(2));String feature=o.optString("feature_type");
                    if(!"schedule_sms".equals(feature)&&!"schedule_whatsapp".equals(feature))continue;
                    String channel="schedule_sms".equals(feature)?"SMS":"WhatsApp";
                    long at=Long.parseLong(o.optString("scheduled_time","0"));String body=o.optString("content","");
                    String state=o.optString("status");String status="succeed".equals(state)?Store.DONE:"failed".equals(state)?Store.FAILED:Store.PAUSED;
                    java.util.List<String[]> people=recipients(o.optString("recipient"));boolean valid=false;for(String[] p:people)if(Messaging.valid(p[1]))valid=true;
                    if(!valid)people=java.util.Collections.singletonList(new String[]{"","Sin número"});
                    for(String[] p:people) {
                        if(valid&&!Messaging.valid(p[1]))continue;
                        ContentValues values=new ContentValues();values.put("source_fingerprint",rows.getString(0));values.put("source_id",rows.getLong(1));
                        database.update("tasks",values,"source_fingerprint='' AND type='Programar' AND channel=? AND recipient=? AND body=? AND at_ms=? AND status=?",new String[]{channel,p[1],body,""+at,status});
                    }
                }catch(Exception ignored){/* Preserve an unrecognized legacy row in the archive. */}
            }
        }
    }
    static Summary importFile(Store store,File file)throws Exception {
        Summary result=preview(file);
        SQLiteDatabase dest=store.getWritableDatabase();
        try(Cursor c=dest.rawQuery("SELECT 1 FROM legacy_imports WHERE fingerprint=?",new String[]{result.fingerprint})) {if(c.moveToFirst())throw new IllegalArgumentException("Este backup de Auto Text ya fue importado");}
        try(SQLiteDatabase source=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)) {
            validate(source);dest.beginTransaction();
            try {
                ContentValues importInfo=new ContentValues();importInfo.put("fingerprint",result.fingerprint);importInfo.put("imported_at",System.currentTimeMillis());importInfo.put("source_count",result.originals);dest.insertOrThrow("legacy_imports",null,importInfo);
                try(Cursor c=source.query("futy",null,null,null,null,null,"id")) {
                    while(c.moveToNext()) {
                        archive(dest,c,"futy",result.fingerprint);
                        String feature=c.getString(c.getColumnIndexOrThrow("feature_type"));
                        if(!"schedule_sms".equals(feature)&&!"schedule_whatsapp".equals(feature))continue;
                        long id=c.getLong(c.getColumnIndexOrThrow("id"));String state=c.getString(c.getColumnIndexOrThrow("status"));
                        String rawAt=c.getString(c.getColumnIndexOrThrow("scheduled_time"));long at=0;try{at=Long.parseLong(rawAt);}catch(Exception ignored){}
                        String body=c.getString(c.getColumnIndexOrThrow("content"));if(body==null)body="";
                        java.util.List<String[]> parsed=recipients(c.getString(c.getColumnIndexOrThrow("recipient")));
                        int valid=0;for(String[] pair:parsed)if(Messaging.valid(pair[1]))valid++;
                        if(valid==0)parsed=java.util.Collections.singletonList(new String[]{"Auto Text #"+id,"Sin número"});
                        for(String[] pair:parsed) {
                            if(valid>0&&!Messaging.valid(pair[1]))continue;
                            Store.Task task=new Store.Task();task.type="Programar";task.channel="schedule_sms".equals(feature)?"SMS":"WhatsApp";task.recipient=pair[1];task.name=pair[0];task.body=body;task.at=at;task.repeat="Nunca";
                            task.status="succeed".equals(state)?Store.DONE:"failed".equals(state)?Store.FAILED:
                                    valid>0&&Messaging.valid(pair[1])&&at>System.currentTimeMillis()&&"running".equals(state)&&!body.trim().isEmpty()?Store.PENDING:Store.PAUSED;
                            task.keyword="";task.start="";task.end="";task.cooldown=60;
                            task.error=valid==0?"Auto Text: falta el número; edita esta tarea":"WhatsApp".equals(task.channel)?"A la hora programada deberás confirmar el envío en WhatsApp":"";
                            task.sourceFingerprint=result.fingerprint;task.sourceId=id;
                            dest.insertOrThrow("tasks",null,task.values());result.created++;
                            if(Messaging.valid(pair[1]))contact(dest,pair[0],pair[1]);
                        }
                    }
                }
                try(Cursor c=source.query("group_recipient",null,null,null,null,null,null)) {while(c.moveToNext()){archive(dest,c,"group_recipient",result.fingerprint);for(String[] pair:recipients(c.getString(c.getColumnIndexOrThrow("recipient"))))contact(dest,pair[0],pair[1]);}}
                try(Cursor c=source.query("message_template",null,null,null,null,null,null)) {while(c.moveToNext()){archive(dest,c,"message_template",result.fingerprint);ContentValues v=new ContentValues();v.put("title","Auto Text #"+c.getLong(c.getColumnIndexOrThrow("id")));v.put("body",c.getString(c.getColumnIndexOrThrow("content")));dest.insertOrThrow("templates",null,v);}}
                dest.setTransactionSuccessful();
            } finally {dest.endTransaction();}
        }
        return result;
    }
}

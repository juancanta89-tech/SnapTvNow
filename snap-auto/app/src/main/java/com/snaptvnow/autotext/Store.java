package com.snaptvnow.autotext;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

final class Store extends SQLiteOpenHelper {
    private final Context app;
    private static final String FORMAT="snap-auto-sqlite-v1";
    static final String PENDING="Pendiente", DONE="Enviado", FAILED="Fallido", PAUSED="Pausado";
    Store(Context c) { super(c,"snap_auto.db",null,5); app=c.getApplicationContext(); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tasks (_id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, channel TEXT NOT NULL, recipient TEXT NOT NULL, name TEXT NOT NULL DEFAULT '', body TEXT NOT NULL, at_ms INTEGER NOT NULL DEFAULT 0, repeat_rule TEXT NOT NULL DEFAULT 'Nunca', status TEXT NOT NULL DEFAULT 'Pendiente', keyword TEXT NOT NULL DEFAULT '', window_start TEXT NOT NULL DEFAULT '', window_end TEXT NOT NULL DEFAULT '', cooldown INTEGER NOT NULL DEFAULT 60, last_error TEXT NOT NULL DEFAULT '', source_fingerprint TEXT NOT NULL DEFAULT '', source_id INTEGER NOT NULL DEFAULT 0, pinned INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE contacts (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL UNIQUE)");
        db.execSQL("CREATE TABLE templates (_id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, body TEXT NOT NULL)");
        db.execSQL("CREATE TABLE history (_id INTEGER PRIMARY KEY AUTOINCREMENT, task_id INTEGER NOT NULL, phone TEXT NOT NULL, at_ms INTEGER NOT NULL, status TEXT NOT NULL, detail TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE throttle (rule_id INTEGER NOT NULL, phone TEXT NOT NULL, at_ms INTEGER NOT NULL, PRIMARY KEY(rule_id,phone))");
        marker(db);
        legacyTables(db);
    }
    private void marker(SQLiteDatabase db) { db.execSQL("CREATE TABLE IF NOT EXISTS app_metadata (name TEXT PRIMARY KEY, value TEXT NOT NULL)");db.execSQL("INSERT OR REPLACE INTO app_metadata (name,value) VALUES ('format','"+FORMAT+"')"); }
    private void legacyTables(SQLiteDatabase db) {db.execSQL("CREATE TABLE IF NOT EXISTS legacy_imports (fingerprint TEXT PRIMARY KEY, imported_at INTEGER NOT NULL, source_count INTEGER NOT NULL)");db.execSQL("CREATE TABLE IF NOT EXISTS legacy_rows (_id INTEGER PRIMARY KEY AUTOINCREMENT, fingerprint TEXT NOT NULL, source_table TEXT NOT NULL, source_id INTEGER NOT NULL, payload TEXT NOT NULL)");}
    @Override public void onUpgrade(SQLiteDatabase db,int old,int now) { if(old<1||old>now)throw new IllegalStateException("Migration required");if(old<2)marker(db);if(old<3)legacyTables(db);if(old<4){db.execSQL("ALTER TABLE tasks ADD COLUMN source_fingerprint TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE tasks ADD COLUMN source_id INTEGER NOT NULL DEFAULT 0");AutoTextImporter.backfillLinks(db);}if(old<5)db.execSQL("ALTER TABLE tasks ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0"); }
    static class Task {
        long id, at, sourceId; int cooldown; boolean pinned; String type,channel,recipient,name,body,repeat,status,keyword,start,end,error,sourceFingerprint;
        static Task from(Cursor c) {
            Task t=new Task(); t.id=c.getLong(c.getColumnIndexOrThrow("_id")); t.at=c.getLong(c.getColumnIndexOrThrow("at_ms"));
            t.cooldown=c.getInt(c.getColumnIndexOrThrow("cooldown"));
            t.type=get(c,"type"); t.channel=get(c,"channel"); t.recipient=get(c,"recipient"); t.name=get(c,"name"); t.body=get(c,"body"); t.repeat=get(c,"repeat_rule"); t.status=get(c,"status"); t.keyword=get(c,"keyword"); t.start=get(c,"window_start"); t.end=get(c,"window_end"); t.error=get(c,"last_error");t.sourceFingerprint=get(c,"source_fingerprint");t.sourceId=c.getLong(c.getColumnIndexOrThrow("source_id"));t.pinned=c.getInt(c.getColumnIndexOrThrow("pinned"))!=0; return t;
        }
        static String get(Cursor c,String k) { return c.getString(c.getColumnIndexOrThrow(k)); }
        ContentValues values() {
            ContentValues v=new ContentValues(); v.put("type",type); v.put("channel",channel); v.put("recipient",recipient); v.put("name",name); v.put("body",body); v.put("at_ms",at); v.put("repeat_rule",repeat); v.put("status",status); v.put("keyword",keyword); v.put("window_start",start); v.put("window_end",end); v.put("cooldown",cooldown); v.put("last_error",error);v.put("source_fingerprint",sourceFingerprint==null?"":sourceFingerprint);v.put("source_id",sourceId);v.put("pinned",pinned?1:0); return v;
        }
    }
    synchronized long save(Task t) { if(t.id==0) return getWritableDatabase().insertOrThrow("tasks",null,t.values()); getWritableDatabase().update("tasks",t.values(),"_id=?",new String[]{""+t.id}); return t.id; }
    synchronized Task get(long id) { try(Cursor c=getReadableDatabase().query("tasks",null,"_id=?",new String[]{""+id},null,null,null)) { return c.moveToFirst()?Task.from(c):null; } }
    synchronized List<Task> tasks() { List<Task> out=new ArrayList<>(); try(Cursor c=getReadableDatabase().query("tasks",null,null,null,null,null,"pinned DESC, CASE status WHEN 'Pendiente' THEN 0 WHEN 'Acción necesaria' THEN 1 WHEN 'Pausado' THEN 2 WHEN 'Enviado' THEN 3 ELSE 4 END, CASE WHEN status='Pendiente' THEN at_ms END ASC, CASE WHEN status!='Pendiente' THEN at_ms END DESC")) { while(c.moveToNext())out.add(Task.from(c)); } return out; }
    synchronized List<Task> activateImportedFutureOnce() {
        SQLiteDatabase database=getWritableDatabase();
        try(Cursor c=database.rawQuery("SELECT 1 FROM app_metadata WHERE name='legacy_activation_v1'",null)){if(c.moveToFirst())return new ArrayList<>();}
        List<Task> activated=new ArrayList<>();database.beginTransaction();
        try {
            try(Cursor c=database.query("tasks",null,"source_fingerprint!='' AND status=? AND at_ms>?",new String[]{PAUSED,""+System.currentTimeMillis()},null,null,"at_ms ASC")) {
                while(c.moveToNext()){Task t=Task.from(c);if(!"Programar".equals(t.type)||!Messaging.valid(t.recipient)||t.body.trim().isEmpty())continue;t.status=PENDING;t.error="WhatsApp".equals(t.channel)?"A la hora programada deberás confirmar el envío en WhatsApp":"";activated.add(t);}
            }
            for(Task t:activated)database.update("tasks",t.values(),"_id=?",new String[]{""+t.id});
            ContentValues marker=new ContentValues();marker.put("name","legacy_activation_v1");marker.put("value","done");database.insertWithOnConflict("app_metadata",null,marker,SQLiteDatabase.CONFLICT_REPLACE);database.setTransactionSuccessful();
        }finally{database.endTransaction();}
        return activated;
    }
    /** Repair older imports from their preserved original rows without changing edited numbers. */
    synchronized List<Task> repairImportedRecipients() {
        SQLiteDatabase database=getWritableDatabase();List<Task> scheduled=new ArrayList<>();
        try(Cursor marker=database.rawQuery("SELECT 1 FROM app_metadata WHERE name='recipient_repair_v1'",null)) {if(marker.moveToFirst())return scheduled;}
        database.beginTransaction();
        try {
            try(Cursor rows=database.rawQuery("SELECT fingerprint,source_id,source_table,payload FROM legacy_rows WHERE source_table IN ('futy','group_recipient') ORDER BY _id",null)) {
                while(rows.moveToNext()) {
                    try {
                        org.json.JSONObject original=new org.json.JSONObject(rows.getString(3));
                        List<String[]> people=AutoTextImporter.recipients(original.optString("recipient"));
                        List<String[]> valid=new ArrayList<>();
                        for(String[] person:people)if(Messaging.valid(person[1])) {
                            valid.add(person);
                            ContentValues contact=new ContentValues();contact.put("name",person[0].isEmpty()?"Auto Text":person[0]);contact.put("phone",person[1]);
                            database.insertWithOnConflict("contacts",null,contact,SQLiteDatabase.CONFLICT_IGNORE);
                        }
                        if(!"futy".equals(rows.getString(2)))continue;
                        String feature=original.optString("feature_type");
                        if(!"schedule_sms".equals(feature)&&!"schedule_whatsapp".equals(feature))continue;
                        String channel="schedule_sms".equals(feature)?"SMS":"WhatsApp";
                        long at;try{at=Long.parseLong(original.optString("scheduled_time"));}catch(Exception badTime){continue;}
                        String body=original.optString("content","");
                        try(Cursor tasks=database.query("tasks",null,"source_fingerprint=? AND source_id=?",new String[]{rows.getString(0),String.valueOf(rows.getLong(1))},null,null,null)) {
                            while(tasks.moveToNext())repairOne(database,Task.from(tasks),valid,original,scheduled);
                        }
                        // v1.1 and v1.2 stored the rows before source IDs were attached.
                        try(Cursor tasks=database.query("tasks",null,"source_fingerprint='' AND type='Programar' AND channel=? AND body=? AND at_ms=?",new String[]{channel,body,String.valueOf(at)},null,null,null)) {
                            while(tasks.moveToNext()) {
                                Task t=Task.from(tasks);
                                boolean match=false;for(String[] person:valid)if(person[1].equals(t.recipient))match=true;
                                if(!match&&!(valid.size()==1&&!Messaging.valid(t.recipient)))continue;
                                t.sourceFingerprint=rows.getString(0);t.sourceId=rows.getLong(1);
                                repairOne(database,t,valid,original,scheduled);
                            }
                        }
                    }catch(Exception ignored){/* A malformed archived row stays untouched. */}
                }
            }
            ContentValues marker=new ContentValues();marker.put("name","recipient_repair_v1");marker.put("value","done");
            database.insertWithOnConflict("app_metadata",null,marker,SQLiteDatabase.CONFLICT_REPLACE);
            database.setTransactionSuccessful();
        } finally {database.endTransaction();}
        return scheduled;
    }
    private static void repairOne(SQLiteDatabase database,Task t,List<String[]> people,org.json.JSONObject original,List<Task> scheduled) {
        boolean changed=false;
        for(String[] person:people)if(person[1].equals(t.recipient)) {
            if(t.name.isEmpty()&&!person[0].isEmpty()){t.name=person[0];changed=true;}
            break;
        }
        if(!Messaging.valid(t.recipient)&&people.size()==1) {
            t.recipient=people.get(0)[1];t.name=people.get(0)[0];changed=true;
            if(PAUSED.equals(t.status)&&t.error.contains("falta el número")&&"running".equals(original.optString("status"))&&t.at>System.currentTimeMillis()&&!t.body.trim().isEmpty()) {
                t.status=PENDING;t.error="WhatsApp".equals(t.channel)?"Confirma el envío cuando llegue el aviso":"";scheduled.add(t);
            }
        }
        if(changed||!t.sourceFingerprint.isEmpty())database.update("tasks",t.values(),"_id=?",new String[]{String.valueOf(t.id)});
    }
    synchronized void delete(long id) { getWritableDatabase().delete("tasks","_id=?",new String[]{""+id}); }
    synchronized void log(long id,String phone,String status,String detail) { ContentValues v=new ContentValues(); v.put("task_id",id); v.put("phone",phone); v.put("at_ms",System.currentTimeMillis()); v.put("status",status); v.put("detail",detail); getWritableDatabase().insert("history",null,v); }
    synchronized boolean throttle(long id,String phone,int minutes) { long now=System.currentTimeMillis(); try(Cursor c=getReadableDatabase().query("throttle",new String[]{"at_ms"},"rule_id=? AND phone=?",new String[]{""+id,phone},null,null,null)) { if(c.moveToFirst() && now-c.getLong(0)<minutes*60000L)return false; } ContentValues v=new ContentValues();v.put("rule_id",id);v.put("phone",phone);v.put("at_ms",now);getWritableDatabase().insertWithOnConflict("throttle",null,v,SQLiteDatabase.CONFLICT_REPLACE);return true; }
    synchronized void addContact(String name,String phone) { ContentValues v=new ContentValues();v.put("name",name);v.put("phone",phone);getWritableDatabase().insertWithOnConflict("contacts",null,v,SQLiteDatabase.CONFLICT_REPLACE); }
    synchronized List<String[]> contacts() { List<String[]> l=new ArrayList<>();try(Cursor c=getReadableDatabase().query("contacts",new String[]{"name","phone"},null,null,null,null,"name")){while(c.moveToNext())l.add(new String[]{c.getString(0),c.getString(1)});}return l; }
    synchronized void addTemplate(String title,String body) { ContentValues v=new ContentValues();v.put("title",title);v.put("body",body);getWritableDatabase().insert("templates",null,v); }
    synchronized List<String[]> templates() { List<String[]> l=new ArrayList<>();try(Cursor c=getReadableDatabase().query("templates",new String[]{"title","body"},null,null,null,null,"_id DESC")){while(c.moveToNext())l.add(new String[]{c.getString(0),c.getString(1)});}return l; }
    synchronized List<String[]> history() {List<String[]> l=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT h.phone,h.status,h.detail,h.at_ms,t.type FROM history h LEFT JOIN tasks t ON h.task_id=t._id ORDER BY h._id DESC LIMIT 200",null)){while(c.moveToNext())l.add(new String[]{c.getString(0),c.getString(1),c.getString(2),""+c.getLong(3),c.getString(4)});}return l;}
    synchronized int legacyCount() {try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM legacy_rows WHERE source_table='futy'",null)){c.moveToFirst();return c.getInt(0);}}
    synchronized List<String[]> unsupportedAutoText() {List<String[]> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM legacy_rows WHERE source_table='futy' ORDER BY source_id",null)){while(c.moveToNext()){try{org.json.JSONObject o=new org.json.JSONObject(c.getString(0));String type=o.optString("feature_type");if(type.startsWith("schedule_"))continue;out.add(new String[]{o.optString("title","Auto Text"),type,o.optString("content"),o.optString("keyword"),o.optString("status")});}catch(Exception ignored){}}}return out;}
    static void copy(InputStream in,OutputStream out) throws java.io.IOException {byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}
    synchronized File snapshot() throws Exception {
        SQLiteDatabase database=getWritableDatabase();
        database.disableWriteAheadLogging();
        File snapshot=File.createTempFile("snap-auto-backup-",".sqlite3",app.getCacheDir());
        database.beginTransaction();
        try(InputStream in=new FileInputStream(app.getDatabasePath("snap_auto.db"));OutputStream out=new FileOutputStream(snapshot)) {copy(in,out);}
        finally {database.endTransaction();}
        try(SQLiteDatabase check=SQLiteDatabase.openDatabase(snapshot.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)) {validate(check);}
        return snapshot;
    }
    static void validate(SQLiteDatabase source) throws Exception {
        try(Cursor c=source.rawQuery("PRAGMA quick_check",null)) {if(!c.moveToFirst()||!"ok".equalsIgnoreCase(c.getString(0)))throw new IllegalArgumentException("Base de datos dañada");}
        try(Cursor c=source.rawQuery("SELECT value FROM app_metadata WHERE name='format'",null)) {if(!c.moveToFirst()||!FORMAT.equals(c.getString(0)))throw new IllegalArgumentException("SQLite de otra aplicación: se necesita analizar una muestra para importarla");}
        catch(android.database.sqlite.SQLiteException e) {throw new IllegalArgumentException("SQLite de otra aplicación: se necesita analizar una muestra para importarla",e);}
    }
    synchronized int restore(File file) throws Exception {
        try(SQLiteDatabase source=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)) {
            validate(source);
            SQLiteDatabase dest=getWritableDatabase();
            dest.beginTransaction();
            try {
                for(String table:new String[]{"history","throttle","tasks","contacts","templates","legacy_rows","legacy_imports"})dest.delete(table,null,null);
                for(String table:new String[]{"contacts","templates","tasks","history","throttle","legacy_imports","legacy_rows"}) {
                    if(!hasTable(source,table))continue;
                    try(Cursor c=source.query(table,null,null,null,null,null,null)) {
                        while(c.moveToNext()) {
                            ContentValues values=new ContentValues();
                            for(int n=0;n<c.getColumnCount();n++) {
                                String key=c.getColumnName(n);int type=c.getType(n);
                                if(type==Cursor.FIELD_TYPE_NULL)values.putNull(key);
                                else if(type==Cursor.FIELD_TYPE_INTEGER)values.put(key,c.getLong(n));
                                else if(type==Cursor.FIELD_TYPE_STRING)values.put(key,c.getString(n));
                                else if(type==Cursor.FIELD_TYPE_BLOB)values.put(key,c.getBlob(n));
                                else values.put(key,c.getDouble(n));
                            }
                            if("tasks".equals(table)&&PENDING.equals(values.getAsString("status"))) {
                                String phone=values.getAsString("recipient");Long at=values.getAsLong("at_ms");
                                if(!Messaging.valid(phone)||at==null||at<=System.currentTimeMillis())values.put("status",PAUSED);
                            }
                            dest.insertOrThrow(table,null,values);
                        }
                    }
                }
                dest.setTransactionSuccessful();
            } finally {dest.endTransaction();}
            return tasks().size();
        }
    }
    private static boolean hasTable(SQLiteDatabase database,String name) {try(Cursor c=database.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{name})){return c.moveToFirst();}}
}

package com.snaptvnow.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Account-bound, offline-first synchronization. No passwords, tokens or media URLs are persisted here. */
final class NativeProfileSync {
 interface Remote {JSONObject call(String operation,JSONObject extra) throws Exception;}
 private final SharedPreferences preferences;
 private final String account;
 private Remote remote;
 private final Runnable changed;
 private final ExecutorService executor=Executors.newSingleThreadExecutor();
 private final Map<String,JSONObject> records=new LinkedHashMap<>(),pending=new LinkedHashMap<>();
 private boolean closed,scheduled;
 private long lastTime;
 NativeProfileSync(Context context,String account,Remote remote,Runnable changed){
  preferences=context.getApplicationContext().getSharedPreferences("native_profile",Context.MODE_PRIVATE);this.account=account;this.remote=remote;this.changed=changed;
  read(preferences.getString(account+".records","[]"),records);read(preferences.getString(account+".pending","[]"),pending);lastTime=preferences.getLong(account+".clock",0);
 }
 static NativeProfileSync connect(Context context,XtreamClient client,String server,Runnable changed){
  if(!ProfileReference.managed(server))return null;
  return new NativeProfileSync(context,AccountFavorites.scope(server,client.username()),new ApiRemote(client),changed);
 }
 private static String identity(JSONObject row){return row.optString("kind")+":"+row.optString("key");}
 private static boolean valid(JSONObject p){
  String kind=p.optString("kind"),key=p.optString("key");long at=p.optLong("updatedAt",-1);
  if(!kind.matches("favorite|progress|preference")||!key.matches("[A-Za-z0-9_.:-]{1,180}")||at<0)return false;
  if(kind.equals("preference"))return key.matches("audio|subtitle")&&(p.optBoolean("deleted")||p.optString("value").matches("off|default|[a-z]{2,3}"));
  if(p.optBoolean("deleted"))return true;
  ProfileReference ref=ProfileReference.parse(p);if(ref==null||!ref.key().equals(key))return false;
  return !kind.equals("progress")||!ref.type.equals("live")&&p.optDouble("time",-1)>=0&&p.optDouble("time",-1)<=172800&&p.optDouble("duration",-1)>=0&&p.optDouble("duration",-1)<=172800;
 }
 private static void read(String text,Map<String,JSONObject> target){try{JSONArray rows=new JSONArray(text);for(int i=0;i<rows.length()&&i<200;i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&valid(row))target.put(identity(row),row);}}catch(Exception ignored){}}
 private static boolean newer(JSONObject next,JSONObject old){return old==null||next.optLong("updatedAt")>old.optLong("updatedAt")||next.optLong("updatedAt")==old.optLong("updatedAt")&&next.optBoolean("deleted")&&!old.optBoolean("deleted");}
 private void persist(){preferences.edit().putString(account+".records",new JSONArray(records.values()).toString()).putString(account+".pending",new JSONArray(pending.values()).toString()).putLong(account+".clock",lastTime).apply();}
 synchronized void merge(JSONArray rows){
  if(closed||rows==null)return;
  for(int i=0;i<rows.length();i++){JSONObject p=rows.optJSONObject(i);if(p==null||!valid(p))continue;String id=identity(p);if(newer(p,records.get(id))){records.put(id,p);JSONObject queued=pending.get(id);if(queued!=null&&newer(p,queued))pending.remove(id);}}
  trim();persist();
 }
 private void trim(){
  List<JSONObject> sorted=new ArrayList<>(records.values());java.util.Collections.sort(sorted,(a,b)->Long.compare(b.optLong("updatedAt"),a.optLong("updatedAt")));
  int favorites=0,progress=0;for(JSONObject p:sorted){String kind=p.optString("kind");boolean remove=kind.equals("favorite")&&++favorites>100||kind.equals("progress")&&++progress>50;if(remove&&!pending.containsKey(identity(p)))records.remove(identity(p));}
 }
 synchronized void queue(JSONObject patch){
  if(closed||!valid(patch))return;String id=identity(patch);if(!newer(patch,records.get(id)))return;
  records.put(id,patch);pending.put(id,patch);trim();persist();
 }
 private synchronized long time(){lastTime=Math.max(System.currentTimeMillis(),lastTime+1);return lastTime;}
 synchronized JSONObject record(String kind,ProfileReference ref){return ref==null?null:records.get(kind+":"+ref.key());}
 synchronized List<JSONObject> snapshot(){return new ArrayList<>(records.values());}
 void favorite(Catalog.Item item,boolean enabled){if(item.profile==null)return;try{JSONObject p=item.profile.json().put("kind","favorite").put("key",item.profile.key()).put("updatedAt",time());if(!enabled)p.put("deleted",true);queue(p);refresh();}catch(Exception ignored){}}
 void progress(Catalog.Item item,PlaybackHistory history,String user){
  if(item==null||item.profile==null||item.profile.type.equals("live"))return;
  try{long at=history.updated(user,item.id);if(at<=0)return;JSONObject old=record("progress",item.profile);if(old!=null&&old.optLong("updatedAt")>=at)return;
   JSONObject p=item.profile.json().put("kind","progress").put("key",item.profile.key()).put("updatedAt",at);
   if(history.completed(user,item.id))p.put("deleted",true);else p.put("time",Math.min(172800,history.position(user,item.id)/1000)).put("duration",Math.min(172800,history.duration(user,item.id)/1000));queue(p);refresh();
  }catch(Exception ignored){}
 }
 void preference(String key,String value){try{queue(new JSONObject().put("kind","preference").put("key",key).put("value",value).put("updatedAt",time()));refresh();}catch(Exception ignored){}}
 synchronized String preference(String key){JSONObject p=records.get("preference:"+key);return p!=null&&!p.optBoolean("deleted")?p.optString("value",null):null;}
 void refresh(){synchronized(this){if(closed||scheduled)return;scheduled=true;}executor.execute(()->{try{syncNow();}catch(Exception ignored){/* Pending patches remain persisted for the next foreground/network retry. */}finally{synchronized(this){scheduled=false;}}});}
 void syncNow() throws Exception {
  final Remote connection;synchronized(this){if(closed||remote==null)return;connection=remote;}
  JSONObject latest=connection.call("profile_get",new JSONObject());merge(latest.optJSONArray("records"));
  final List<JSONObject> batch;synchronized(this){if(closed)return;batch=new ArrayList<>(pending.values()).subList(0,Math.min(160,pending.size()));}
  if(!batch.isEmpty()){
   JSONObject reply=connection.call("profile_patch",new JSONObject().put("patches",new JSONArray(batch)));
   synchronized(this){if(closed)return;for(JSONObject p:batch){String id=identity(p);if(pending.get(id)==p)pending.remove(id);}merge(reply.optJSONArray("records"));persist();}
  }
  synchronized(this){if(closed)return;}changed.run();
 }
 synchronized void stop(){closed=true;remote=null;executor.shutdownNow();}
 private static final class ApiRemote implements Remote {
  private final XtreamClient client;private String token;
  ApiRemote(XtreamClient client){this.client=client;}
  public JSONObject call(String operation,JSONObject extra) throws Exception {
   for(int attempt=0;attempt<2;attempt++){
    if(token==null){JSONObject auth=post(new JSONObject().put("op","auth").put("username",client.username()).put("password",client.password()));token=auth.optString("access_token",null);if(token==null||token.isEmpty())throw new Exception("profile_auth_required");}
    JSONObject body=new JSONObject(extra.toString()).put("op",operation).put("access_token",token);
    try{return post(body);}catch(Exception e){if(attempt==0&&e.getMessage().equals("profile_auth_required")){token=null;continue;}throw e;}
   }throw new Exception("profile_auth_required");
  }
  private JSONObject post(JSONObject body) throws Exception {
   HttpURLConnection c=(HttpURLConnection)new URL("https://api.snaptvnow.com/").openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(6000);c.setReadTimeout(10000);c.setRequestMethod("POST");c.setRequestProperty("Content-Type","application/json");c.setDoOutput(true);
   try{byte[] payload=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(payload.length);try(java.io.OutputStream out=c.getOutputStream()){out.write(payload);}
    int code=c.getResponseCode();if(code==401)throw new Exception("profile_auth_required");if(code!=200)throw new Exception("profile_unavailable");
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];try(InputStream in=c.getInputStream()){int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>500000)throw new Exception("profile_too_large");bytes.write(buffer,0,n);}}
    return new JSONObject(bytes.toString("UTF-8"));
   }finally{c.disconnect();}
  }
 }
}

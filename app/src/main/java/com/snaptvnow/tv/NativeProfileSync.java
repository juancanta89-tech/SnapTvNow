package com.snaptvnow.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
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
 private final ExecutorService executor;
 private final ExecutorService releases=Executors.newSingleThreadExecutor();
 private final ExecutorService telemetry=Executors.newSingleThreadExecutor();
 private final Map<String,JSONObject> records=new LinkedHashMap<>(),pending=new LinkedHashMap<>();
 private static final class Metric {
  final JSONObject payload;final long measuredAt=android.os.SystemClock.elapsedRealtime();
  Metric(JSONObject payload){this.payload=payload;}
 }
 private final List<Metric> metrics=new ArrayList<>(); // Bounded memory only; never stored with the profile.
 private final List<Runnable> refreshCallbacks=new ArrayList<>();
 private boolean closed,scheduled,refreshRequested,metricScheduled;
 private long lastTime;
 NativeProfileSync(Context context,String account,Remote remote,Runnable changed){
  this(context,account,remote,changed,Executors.newSingleThreadExecutor());
 }
 NativeProfileSync(Context context,String account,Remote remote,Runnable changed,ExecutorService executor){
  this.executor=executor;
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
  while(pending.size()>200){String oldest=null;long at=Long.MAX_VALUE;for(Map.Entry<String,JSONObject> e:pending.entrySet())if(oldest==null||e.getValue().optLong("updatedAt")<at){oldest=e.getKey();at=e.getValue().optLong("updatedAt");}pending.remove(oldest);}
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
  if(item==null||item.profile==null||!item.id.startsWith("movie")&&!item.id.startsWith("episode"))return;
  try{long at=history.updated(user,item.id);if(at<=0)return;JSONObject old=record("progress",item.profile);if(old!=null&&old.optLong("updatedAt")>=at)return;
   JSONObject p=item.profile.json().put("kind","progress").put("key",item.profile.key()).put("updatedAt",at);
   if(history.completed(user,item.id))p.put("deleted",true).put("completed",true);
   else p.put("time",Math.min(172800,history.position(user,item.id)/1000)).put("duration",Math.min(172800,history.duration(user,item.id)/1000));
   queue(p);refresh();
  }catch(Exception ignored){}
 }
 void preference(String key,String value){try{queue(new JSONObject().put("kind","preference").put("key",key).put("value",value).put("updatedAt",time()));refresh();}catch(Exception ignored){}}
 synchronized String preference(String key){JSONObject p=records.get("preference:"+key);return p!=null&&!p.optBoolean("deleted")?p.optString("value",null):null;}
 synchronized void queueMetric(JSONObject metric){queueMetric(metric,null,0,0);}
 synchronized void queueMetric(JSONObject metric,String ticket,int width,int height){
  if(closed)return;
  try{
   JSONObject payload=new JSONObject().put("metric",new JSONObject(metric.toString()));
   if(ticket!=null&&ticket.matches("[A-Za-z0-9_-]{40,4096}"))payload.put("quality_ticket",ticket).put("observation",new JSONObject().put("width",width).put("height",height));
   if(metrics.size()>=20)metrics.remove(0);metrics.add(new Metric(payload));
  }catch(Exception ignored){}
 }
 void metric(JSONObject metric){queueMetric(metric);scheduleMetric();}
 void metric(JSONObject metric,String ticket,int width,int height){queueMetric(metric,ticket,width,height);scheduleMetric();}
 void releasePlayback(String ticket){
  final Remote connection;synchronized(this){if(closed||remote==null||ticket==null||!ticket.matches("[A-Za-z0-9_-]{40,4096}"))return;connection=remote;}
  // Release the old account's exact playback independently of telemetry retries.
  try{releases.execute(()->{try{connection.call("playback_release",new JSONObject().put("quality_ticket",ticket));}catch(Exception ignored){/* Cancellation/lease expiry remains the fallback. */}});}catch(java.util.concurrent.RejectedExecutionException ignored){}
 }
 void refresh(){refresh(null);}
 synchronized void refresh(Runnable after){
  if(closed)return;
  if(after!=null)refreshCallbacks.add(after);
  refreshRequested=true;scheduleMetric();if(scheduled)return;scheduled=true;
  executor.execute(()->{
   while(true){
    final List<Runnable> callbacks;
    synchronized(this){
     if(closed){scheduled=false;return;}
     refreshRequested=false;callbacks=new ArrayList<>(refreshCallbacks);refreshCallbacks.clear();
    }
    try{syncProfile();}catch(Exception ignored){/* Keep pending patches for the next requested/foreground retry. */}
    for(Runnable callback:callbacks)callback.run();
    synchronized(this){if(closed||!refreshRequested){scheduled=false;return;}}
   }
  });
 }
 private synchronized void scheduleMetric(){
  if(closed||metricScheduled||metrics.isEmpty())return;
  metricScheduled=true;
  telemetry.execute(()->{try{sendMetric();}finally{synchronized(this){metricScheduled=false;}}});
 }
 void syncNow() throws Exception {
  try{syncProfile();}finally{sendMetric();}
 }
 private void sendMetric(){
  final Remote connection;final Metric metric;
  synchronized(this){if(closed||remote==null||metrics.isEmpty())return;connection=remote;metric=metrics.get(0);}
  try{
   JSONObject payload=new JSONObject(metric.payload.toString());
   if(payload.has("quality_ticket"))payload.getJSONObject("observation").put("ageMs",Math.max(0,android.os.SystemClock.elapsedRealtime()-metric.measuredAt));
   JSONObject result=connection.call("playback_metric",payload);
   synchronized(this){if(!closed&&(!result.optBoolean("limited")||result.optBoolean("qualityHandled"))&&!metrics.isEmpty()&&metrics.get(0)==metric)metrics.remove(0);}
  }catch(Exception ignored){/* Retry optional telemetry independently of watch progress. */}
 }
 private synchronized List<JSONObject> pendingBatch(){
  return new ArrayList<>(pending.values()).subList(0,Math.min(160,pending.size()));
 }
 private void syncProfile() throws Exception {
  final Remote connection;synchronized(this){if(closed||remote==null)return;connection=remote;}
  List<JSONObject> batch=pendingBatch();
  // The patch transaction already returns the latest profile. A pause can use one request.
  boolean progressOnly=!batch.isEmpty();
  for(JSONObject p:batch)progressOnly&=p.optString("kind").equals("progress");
  if(!progressOnly){
   JSONObject latest=connection.call("profile_get",new JSONObject());merge(latest.optJSONArray("records"));
   batch=pendingBatch();
  }
  synchronized(this){if(closed)return;}
  if(!batch.isEmpty()){
   JSONObject reply=connection.call("profile_patch",new JSONObject().put("patches",new JSONArray(batch)));
   synchronized(this){if(closed)return;for(JSONObject p:batch){String id=identity(p);if(pending.get(id)==p)pending.remove(id);}merge(reply.optJSONArray("records"));persist();}
  }
  synchronized(this){if(closed)return;}changed.run();
 }
 synchronized void stop(){
  if(closed)return;
  final Remote connection=remote;final List<JSONObject> progress=new ArrayList<>();
  for(JSONObject p:pendingBatch())if(p.optString("kind").equals("progress"))progress.add(p);
  closed=true;remote=null;metrics.clear();refreshCallbacks.clear();
  // Keep the final checkpoint durable if the activity closes during its upload.
  // An old instance never acknowledges changes into a reopened instance's preferences.
  if(connection!=null&&!progress.isEmpty())executor.execute(()->{
   try{connection.call("profile_patch",new JSONObject().put("patches",new JSONArray(progress)));}
   catch(Exception ignored){/* Persisted changes are retried on the next login. */}
  });
  executor.shutdown();telemetry.shutdownNow();releases.shutdown();
 }
 private static final class ApiRemote implements Remote {
  private final XtreamClient client;private volatile String token;
  ApiRemote(XtreamClient client){this.client=client;}
  public JSONObject call(String operation,JSONObject extra) throws Exception {
   for(int attempt=0;attempt<2;attempt++){
    String access=accessToken();
    JSONObject body=new JSONObject(extra.toString()).put("op",operation).put("access_token",access);
    try{return post(body);}catch(Exception e){
     if(attempt==0&&"profile_auth_required".equals(e.getMessage())){
      synchronized(this){if(access.equals(token))token=null;}
      continue;
     }
     throw e;
    }
   }throw new Exception("profile_auth_required");
  }
  private synchronized String accessToken() throws Exception {
   if(token==null){
    JSONObject auth=post(new JSONObject().put("op","auth").put("username",client.username()).put("password",client.password()));
    token=auth.optString("access_token",null);
    if(token==null||token.isEmpty()){token=null;throw new Exception("profile_auth_required");}
   }
   return token;
  }
  private JSONObject post(JSONObject body) throws Exception {
   okhttp3.Request request=new okhttp3.Request.Builder().url("https://api.snaptvnow.com/")
       .post(okhttp3.RequestBody.create(body.toString(),okhttp3.MediaType.get("application/json; charset=utf-8"))).build();
   try(okhttp3.Response response=SharedNetwork.execute(request,6000,10000)){
    if(response.code()==401)throw new Exception("profile_auth_required");
    if(response.code()!=200)throw new Exception("profile_unavailable");
    return new JSONObject(SharedNetwork.text(response,500000));
   }
  }
 }
}

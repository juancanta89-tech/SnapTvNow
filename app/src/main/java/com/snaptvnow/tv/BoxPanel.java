package com.snaptvnow.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/** One immutable app identity per APK. No owner/reseller credentials belong here. */
final class BoxPanel {
  static final String ORIGIN="https://api.snaptvnow.com";
  static final long LEASE_MS=600_000;
  static volatile boolean enabled;
  private static volatile JSONObject identity,config;
  private static volatile long verifiedAt;
  private static volatile String problem="Comprobando el estado de tu app…";
  static void initialize(Context context){
    try(InputStream in=context.getAssets().open("boxpanel.json")){
      enabled=true;identity=parseIdentity(new JSONObject(new String(read(in,380_000),"UTF-8")),context.getPackageName());
    }catch(FileNotFoundException absent){enabled=false;identity=config=null;verifiedAt=0;}
    catch(Exception invalid){enabled=true;identity=config=null;verifiedAt=0;problem="La configuración de esta app no es válida. Contacta al administrador.";}
  }
  static JSONObject parseIdentity(JSONObject value,String installedPackage) throws Exception {
    String id=value.getString("appId");
    if(!id.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")||!packageName(id).equals(installedPackage)||!installedPackage.equals(value.getString("packageName")))throw new IOException("Identidad de app inválida");
    validateBrand(value);return value;
  }
  static String packageName(String id){return "com.snaptvnow.box.a"+id.replace("-","");}
  static void validateBrand(JSONObject app) throws Exception {
    if(!java.util.Arrays.asList("classic","cinema","compact").contains(app.getString("design"))||!app.getString("accent").matches("#[a-fA-F0-9]{6}")||!app.getString("background").matches("#[a-fA-F0-9]{6}")||app.getString("name").trim().length()<2||app.getString("name").length()>40)throw new IOException("Diseño de app inválido");
  }
  static String base(){if(identity==null)throw new IllegalStateException("App sin identidad válida");return ORIGIN+"/api/boxpanel/client/"+identity.optString("appId");}
  static synchronized JSONObject refresh() throws Exception {
    if(!enabled)return null;
    if(identity==null)throw new IOException(problem);
    if(config!=null&&SystemClock.elapsedRealtime()-verifiedAt<5000)return config;
    try {
      JSONObject next=new JSONObject(new String(download(base()+"/config",40_000),"UTF-8"));
      validateConfig(next,identity);
      config=next;verifiedAt=SystemClock.elapsedRealtime();problem="";return config;
    }catch(Exception error){
      problem=error.getMessage()==null?"No se pudo verificar el estado de la app.":error.getMessage();
      // A network outage never falls back to SNAP or to another reseller's DNS.
      if(!authorized())config=null;throw error;
    }
  }
  static void validateConfig(JSONObject next,JSONObject expected) throws Exception {
    if(!next.optBoolean("active")){verifiedAt=0;throw new IOException("Esta app está deshabilitada por el administrador.");}
    JSONObject app=next.getJSONObject("app");
    if(!expected.getString("appId").equals(app.getString("id"))||!expected.getString("packageName").equals(next.getString("packageName")))throw new IOException("La configuración pertenece a otra app.");
    validateBrand(app);List<String> servers=AppConfig.parseServers(next.getJSONArray("servers"));
    if(servers.isEmpty()||servers.size()>3)throw new IOException("El administrador todavía no configuró los DNS de esta app.");
    JSONArray slides=next.getJSONArray("slides");if(slides.length()>8)throw new IOException("Publicidad inválida");
  }
  static boolean authorized(){return !enabled||(config!=null&&verifiedAt>0&&SystemClock.elapsedRealtime()-verifiedAt<LEASE_MS);}
  static String problem(){return problem.isEmpty()?"No se pudo verificar el estado de tu app. Revisa tu conexión y reintenta.":problem;}
  static String name(){return brand().optString("name","SNAPTVNOW");}
  static String design(){return brand().optString("design","compact");}
  static int accent(){return android.graphics.Color.parseColor(brand().optString("accent","#29dce8"));}
  static int background(){return android.graphics.Color.parseColor(brand().optString("background","#071729"));}
  static JSONObject brand(){JSONObject current=config;return current!=null?current.optJSONObject("app"):identity!=null?identity:new JSONObject();}
  static JSONObject configuration(){return config;}
  static Bitmap embeddedLogo(){
    String logo=brand().optString("logo","");if(!logo.startsWith("data:image/"))return null;
    try{byte[] bytes=Base64.decode(logo.substring(logo.indexOf(',')+1),Base64.DEFAULT);BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);o.inSampleSize=Math.max(1,Math.max(o.outWidth/160,o.outHeight/160));o.inJustDecodeBounds=false;return BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);}catch(Exception ignored){return null;}
  }
  static String asset(String path){if(!path.matches("logo|ads/[a-f0-9-]{36}"))throw new IllegalArgumentException("Imagen inválida");return base()+"/"+path;}
  static byte[] download(String address,int limit) throws Exception {
    if(!address.startsWith(ORIGIN+"/api/boxpanel/client/"))throw new IOException("Origen de app inválido");
    HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
    connection.setConnectTimeout(5000);connection.setReadTimeout(7000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("Cache-Control","no-cache");
    try{
      int status=connection.getResponseCode();
      if(status==403||(status==404&&address.endsWith("/config"))){verifiedAt=0;config=null;throw new IOException("Esta app no está habilitada. Contacta al administrador.");}
      if(status!=200)throw new IOException("No se pudo verificar la app. Revisa tu conexión.");
      try(InputStream in=connection.getInputStream()){return read(in,limit);}
    }finally{connection.disconnect();}
  }
  static byte[] read(InputStream in,int limit) throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[4096];int n;while((n=in.read(block))!=-1){if(out.size()+n>limit)throw new IOException("Configuración demasiado grande");out.write(block,0,n);}return out.toByteArray();}
  private BoxPanel(){}
}

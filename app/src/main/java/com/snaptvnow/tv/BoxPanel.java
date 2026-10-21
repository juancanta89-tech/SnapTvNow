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
  static final String[] ART_SLOTS={"background","hero","login","tv","movies","series","poster1","poster2","poster3","poster4","poster5"};
  static final long LEASE_MS=600_000;
  static volatile boolean enabled;
  private static volatile JSONObject identity,config;
  private static volatile long verifiedAt;
  private static volatile String problem="Comprobando el estado de tu app…";
  static void initialize(Context context){
    try(InputStream in=context.getAssets().open("boxpanel.json")){
      enabled=true;identity=parseIdentity(new JSONObject(new String(read(in,6*1024*1024),"UTF-8")),context.getPackageName());
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
    if(!java.util.Arrays.asList("classic","cinema","compact","aura","panorama","stadium","noir","horizon").contains(app.getString("design"))||!app.getString("accent").matches("#[a-fA-F0-9]{6}")||!app.getString("background").matches("#[a-fA-F0-9]{6}")||app.getString("name").trim().length()<2||app.getString("name").length()>40)throw new IOException("Diseño de app inválido");
    validateStyle(app);validateArtwork(app);
  }
  static void validateStyle(JSONObject app) throws Exception {
    if(!app.has("style"))return;
    JSONObject value=app.getJSONObject("style");
    if(!java.util.Arrays.asList("aurora","midnight","sunset","ocean").contains(value.getString("wallpaper"))||!java.util.Arrays.asList("comfortable","compact").contains(value.getString("density"))||value.getString("welcome").length()>80||value.getString("welcome").matches("(?s).*[\\x00-\\x1f].*"))throw new IOException("Estilo de app inválido");
  }
  static void validateArtwork(JSONObject app) throws Exception {
    if(!app.has("artwork"))return;JSONObject images=app.getJSONObject("artwork");if(images.length()>ART_SLOTS.length)throw new IOException("Demasiadas imágenes");
    java.util.Iterator<String> keys=images.keys();while(keys.hasNext()){String slot=keys.next();JSONObject value=images.getJSONObject(slot);if(!java.util.Arrays.asList(ART_SLOTS).contains(slot)||!java.util.Arrays.asList("cover","contain").contains(value.getString("fit")))throw new IOException("Zona de imagen inválida");if(value.has("data")){String data=value.getString("data");if(data.length()>350000||!data.matches("data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+"))throw new IOException("Imagen inválida");}else if(!value.has("url"))throw new IOException("Imagen sin origen");}
  }
  static boolean applyArtwork(android.widget.ImageView image,String slot){
    JSONObject images=brand().optJSONObject("artwork"),value=images==null?null:images.optJSONObject(slot);if(value==null)return false;
    image.setContentDescription("Imagen personalizada: "+slot);image.setScaleType(value.optString("fit").equals("contain")?android.widget.ImageView.ScaleType.FIT_CENTER:android.widget.ImageView.ScaleType.CENTER_CROP);
    String data=value.optString("data","");if(!data.isEmpty()){try{byte[] bytes=Base64.decode(data.substring(data.indexOf(',')+1),Base64.DEFAULT);BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);o.inSampleSize=Math.max(1,Math.max(o.outWidth/1024,o.outHeight/720));o.inJustDecodeBounds=false;Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);if(bitmap!=null)image.setImageBitmap(bitmap);}catch(Exception ignored){}}else Artwork.into(image,value.optString("url",""));return true;
  }
  static JSONObject style(){JSONObject value=brand().optJSONObject("style");return value==null?new JSONObject():value;}
  static boolean sidebar(){return java.util.Arrays.asList("cinema","compact").contains(design());}
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
    validateBrand(app);JSONObject images=app.optJSONObject("artwork");if(images!=null){java.util.Iterator<String> slots=images.keys();while(slots.hasNext()){String slot=slots.next();JSONObject value=images.getJSONObject(slot);if(value.has("data")||!value.optString("url").matches(java.util.regex.Pattern.quote(ORIGIN+"/api/boxpanel/client/"+expected.getString("appId")+"/artwork/"+slot)+"\\?v=[1-9][0-9]{0,9}"))throw new IOException("Imagen de otra app");}}
    List<String> servers=AppConfig.parseServers(next.getJSONArray("servers"));
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

package com.snaptvnow.tv;
import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

/** Never assigns unowned legacy fav_* values to a newly signed-in account. */
final class AccountFavorites {
 private final SharedPreferences preferences;
 AccountFavorites(Context context){preferences=context.getApplicationContext().getSharedPreferences("account_favorites",Context.MODE_PRIVATE);}
 static String scope(String server,String username){
  try{String canonical=ProfileReference.managed(server)?"https://api.snaptvnow.com":String.valueOf(server);byte[] digest=MessageDigest.getInstance("SHA-256").digest((canonical+"\u0000"+username.trim().toLowerCase(java.util.Locale.ROOT)).getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}catch(Exception impossible){throw new IllegalStateException(impossible);}
 }
 Set<String> load(String account){return new HashSet<>(preferences.getStringSet(account,new HashSet<>()));}
 void save(String account,Set<String> ids){preferences.edit().putStringSet(account,new HashSet<>(ids)).apply();}
}

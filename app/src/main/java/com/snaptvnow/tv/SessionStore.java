package com.snaptvnow.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Keeps a subscriber session across app restarts without storing plaintext credentials. */
final class SessionStore {
  private static final String ALIAS="snaptvnow_subscriber_session";
  private static final String PREF="subscriber_session";
  private static final String DATA="encrypted_session";

  static void save(Context context,String username,String password,String expires,String maxConnections) throws Exception {
    JSONObject value=new JSONObject();value.put("username",username);value.put("password",password);
    value.put("expires",expires);value.put("maxConnections",maxConnections);
    Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
    byte[] encrypted=cipher.doFinal(value.toString().getBytes(StandardCharsets.UTF_8));
    JSONObject record=new JSONObject();record.put("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP));
    record.put("payload",Base64.encodeToString(encrypted,Base64.NO_WRAP));
    if(!context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(DATA,record.toString()).commit())
      throw new Exception("No se pudo guardar la sesión");
  }

  static XtreamClient restore(Context context) {
    String saved=context.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(DATA,null);
    if(saved==null)return null;
    try {
      JSONObject record=new JSONObject(saved);
      byte[] iv=Base64.decode(record.getString("iv"),Base64.DEFAULT);
      if(iv.length!=12)throw new Exception("Invalid IV");
      Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
      JSONObject value=new JSONObject(new String(cipher.doFinal(Base64.decode(record.getString("payload"),Base64.DEFAULT)),StandardCharsets.UTF_8));
      return new XtreamClient(value.getString("username"),value.getString("password"),value.optString("expires"),value.optString("maxConnections"));
    }catch(Exception e){clear(context);return null;}
  }

  static void clear(Context context){context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().remove(DATA).commit();}

  private static SecretKey key() throws Exception {
    KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
    if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
    KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
    generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
    return generator.generateKey();
  }
}

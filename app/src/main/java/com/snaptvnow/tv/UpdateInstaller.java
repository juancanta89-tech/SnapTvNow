package com.snaptvnow.tv;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

/** Only newer APKs of this package, signed with the currently installed key, reach the installer. */
final class UpdateInstaller {
  private UpdateInstaller(){}
  static long version(PackageInfo info){
    return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;
  }
  static void verify(Context context,File apk,int expectedCode) throws IOException {
    int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
    try {
      PackageInfo installed=context.getPackageManager().getPackageInfo(context.getPackageName(),flags);
      PackageInfo candidate=context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),flags);
      validate(installed,candidate,expectedCode);
    }catch(PackageManager.NameNotFoundException failure){throw new IOException("No se pudo verificar la actualización.",failure);}
  }
  static void validate(PackageInfo installed,PackageInfo candidate,int expectedCode) throws IOException {
    if(candidate==null||!installed.packageName.equals(candidate.packageName))
      throw new IOException("El archivo descargado no es una actualización de SNAPTVNOW.");
    if(version(candidate)!=expectedCode||version(candidate)<=version(installed))
      throw new IOException("La versión de la APK no coincide con la actualización publicada.");
    Signature[] current=signers(installed),next=signers(candidate);
    if(current==null||next==null||current.length==0||current.length!=next.length
        ||!new HashSet<>(Arrays.asList(current)).equals(new HashSet<>(Arrays.asList(next))))
      throw new IOException("La APK no tiene la firma de esta instalación de SNAPTVNOW.");
  }
  private static Signature[] signers(PackageInfo info){
    return Build.VERSION.SDK_INT>=28
      ?(info.signingInfo==null?null:info.signingInfo.getApkContentsSigners())
      :info.signatures;
  }
  static boolean needsPermission(Context context){
    return Build.VERSION.SDK_INT>=26&&!context.getPackageManager().canRequestPackageInstalls();
  }
  static Intent permissionIntent(Context context){
    return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+context.getPackageName()));
  }
  static Intent installIntent(Context context,File apk){
    Uri uri=FileProvider.getUriForFile(context,context.getPackageName()+".updates",apk);
    Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    intent.setClipData(ClipData.newRawUri("SNAPTVNOW",uri));
    return intent;
  }
}

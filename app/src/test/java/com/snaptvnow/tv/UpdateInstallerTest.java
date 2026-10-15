package com.snaptvnow.tv;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.provider.Settings;
import java.io.File;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class UpdateInstallerTest {
  private PackageInfo info(String name,int version,String key){
    PackageInfo info=new PackageInfo();info.packageName=name;info.versionCode=version;
    info.signatures=new Signature[]{new Signature(key)};
    if(Build.VERSION.SDK_INT>=28){
      info.signingInfo=Shadow.newInstanceOf(SigningInfo.class);
      Shadows.shadowOf(info.signingInfo).setSignatures(info.signatures);
    }
    return info;
  }
  private void reject(PackageInfo candidate,int expected) throws Exception {
    try{UpdateInstaller.validate(info("com.snaptvnow.tv",40,"aabb"),candidate,expected);fail();}catch(IOException correct){}
  }
  @Test public void acceptsOnlyThePublishedNewVersionWithCurrentSignature() throws Exception {
    UpdateInstaller.validate(info("com.snaptvnow.tv",40,"aabb"),info("com.snaptvnow.tv",41,"aabb"),41);
  }
  @Test public void rejectsDifferentAppAndUnparseableDownload() throws Exception {
    reject(info("com.attacker.fake",41,"aabb"),41);reject(null,41);
  }
  @Test public void rejectsOlderSameOrIncorrectlyAdvertisedVersion() throws Exception {
    reject(info("com.snaptvnow.tv",39,"aabb"),39);reject(info("com.snaptvnow.tv",40,"aabb"),40);
    reject(info("com.snaptvnow.tv",41,"aabb"),42);
  }
  @Test public void rejectsDifferentKeyAndMissingSignature() throws Exception {
    reject(info("com.snaptvnow.tv",41,"ccdd"),41);
    PackageInfo unsigned=info("com.snaptvnow.tv",41,"aabb");unsigned.signingInfo=null;unsigned.signatures=null;reject(unsigned,41);
  }
  @Test @Config(sdk=25) public void fireOs6LegacySignatureValidationAcceptsMatchingKey() throws Exception {
    PackageInfo installed=info("com.snaptvnow.tv",40,"aabb"),candidate=info("com.snaptvnow.tv",41,"aabb");
    UpdateInstaller.validate(installed,candidate,41);
    candidate.signatures=new Signature[]{new Signature("ccdd")};
    try{UpdateInstaller.validate(installed,candidate,41);fail();}catch(IOException correct){}
  }
  @Test public void modernOsCanUseLegacyCertificatesWhenSigningInfoIsMissing() throws Exception {
    PackageInfo installed=info("com.snaptvnow.tv",45,"aabb"),candidate=info("com.snaptvnow.tv",46,"aabb");
    installed.signingInfo=null;UpdateInstaller.validate(installed,candidate,46);
    installed=info("com.snaptvnow.tv",45,"aabb");candidate.signingInfo=null;UpdateInstaller.validate(installed,candidate,46);
    installed.signingInfo=null;UpdateInstaller.validate(installed,candidate,46);
    candidate.signatures=new Signature[]{new Signature("ccdd")};
    try{UpdateInstaller.validate(installed,candidate,46);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("no tiene la firma"));}
  }
  @Test public void emptyModernFieldsCanUseTheVerifiedLegacyCertificate() throws Exception {
    PackageInfo installed=info("com.snaptvnow.tv",45,"aabb"),candidate=info("com.snaptvnow.tv",46,"aabb");
    Shadows.shadowOf(installed.signingInfo).setSignatures(new Signature[0]);
    Shadows.shadowOf(candidate.signingInfo).setSignatures(new Signature[0]);
    UpdateInstaller.validate(installed,candidate,46);
  }
  @Test public void modernMismatchNeverFallsBackToMatchingLegacyFields() throws Exception {
    PackageInfo installed=info("com.snaptvnow.tv",45,"aabb"),candidate=info("com.snaptvnow.tv",46,"ccdd");
    candidate.signatures=installed.signatures;
    try{UpdateInstaller.validate(installed,candidate,46);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("no tiene la firma"));}
  }
  @Test public void unreadableSignaturesAreDistinguishedFromDifferentKeys() throws Exception {
    PackageInfo installed=info("com.snaptvnow.tv",45,"aabb"),candidate=info("com.snaptvnow.tv",46,"aabb");
    candidate.signingInfo=null;candidate.signatures=null;
    try{UpdateInstaller.validate(installed,candidate,46);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("No se pudo leer la firma de la APK"));}
    candidate=info("com.snaptvnow.tv",46,"aabb");installed.signingInfo=null;installed.signatures=null;
    try{UpdateInstaller.validate(installed,candidate,46);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("No se pudo leer la firma de esta instalación"));}
  }
  @Test public void verifyRequestsModernAndLegacySignatureFieldsTogether() throws Exception {
    Context context=mock(Context.class);PackageManager manager=mock(PackageManager.class);File file=new File("candidate.apk");
    when(context.getPackageManager()).thenReturn(manager);when(context.getPackageName()).thenReturn("com.snaptvnow.tv");
    int flags=PackageManager.GET_SIGNATURES|PackageManager.GET_SIGNING_CERTIFICATES;
    PackageInfo installed=info("com.snaptvnow.tv",45,"aabb"),candidate=info("com.snaptvnow.tv",46,"aabb");candidate.signingInfo=null;
    when(manager.getPackageInfo("com.snaptvnow.tv",flags)).thenReturn(installed);when(manager.getPackageArchiveInfo(file.getAbsolutePath(),flags)).thenReturn(candidate);
    UpdateInstaller.verify(context,file,46);verify(manager).getPackageInfo("com.snaptvnow.tv",flags);verify(manager).getPackageArchiveInfo(file.getAbsolutePath(),flags);
  }
  @Test public void malformedCertificateArraysRemainRejected() throws Exception {
    PackageInfo installed=info("com.snaptvnow.tv",45,"aabb"),candidate=info("com.snaptvnow.tv",46,"aabb");
    candidate.signingInfo=null;candidate.signatures=new Signature[]{null};
    try{UpdateInstaller.validate(installed,candidate,46);fail();}catch(IOException expected){}
    Signature key=new Signature("aabb");candidate.signatures=new Signature[]{key,key};
    try{UpdateInstaller.validate(installed,candidate,46);fail();}catch(IOException expected){}
  }
  @Test public void unknownSourcePermissionTargetsThisAppAndIsRechecked() {
    Context context=mock(Context.class);PackageManager manager=mock(PackageManager.class);
    when(context.getPackageManager()).thenReturn(manager);when(context.getPackageName()).thenReturn("com.snaptvnow.tv");
    when(manager.canRequestPackageInstalls()).thenReturn(false);
    assertTrue(UpdateInstaller.needsPermission(context));
    when(manager.canRequestPackageInstalls()).thenReturn(true);
    assertFalse(UpdateInstaller.needsPermission(context));
    Intent settings=UpdateInstaller.permissionIntent(context);
    assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,settings.getAction());
    assertEquals("package:com.snaptvnow.tv",settings.getDataString());
  }
  @Test @Config(sdk=25) public void olderFireOsUsesTheSystemInstallPermissionPrompt(){
    Context context=mock(Context.class);assertFalse(UpdateInstaller.needsPermission(context));verifyNoInteractions(context);
  }
  @Test public void installerReceivesPrivateApkContentUriAndTemporaryReadGrant() throws Exception {
    Context context=RuntimeEnvironment.getApplication();
    File directory=new File(context.getFilesDir(),"updates");directory.mkdirs();
    File apk=new File(directory,"update-41.apk");apk.createNewFile();
    Intent intent=UpdateInstaller.installIntent(context,apk);
    assertEquals(Intent.ACTION_VIEW,intent.getAction());
    assertEquals("content",intent.getData().getScheme());
    assertEquals(context.getPackageName()+".updates",intent.getData().getAuthority());
    assertEquals("application/vnd.android.package-archive",intent.getType());
    assertNotEquals(0,intent.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);
    assertEquals(intent.getData(),intent.getClipData().getItemAt(0).getUri());
    apk.delete();
  }
  @Test public void updateButtonOpensTheInternalUpdaterWithThePublishedUrl(){
    Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
    String url="https://github.com/owner/app/releases/download/1.0.12/app-release.apk";
    AppUpdateActivity.open(activity,url,41,"1.0.12");
    Intent intent=Shadows.shadowOf(activity).getNextStartedActivity();
    assertEquals(AppUpdateActivity.class.getName(),intent.getComponent().getClassName());
    assertEquals(url,intent.getStringExtra(AppUpdateActivity.URL_EXTRA));
    assertEquals(41,intent.getIntExtra(AppUpdateActivity.CODE_EXTRA,0));
    assertNull(intent.getData());activity.finish();
  }
}

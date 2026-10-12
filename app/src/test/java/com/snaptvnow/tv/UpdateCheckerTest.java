package com.snaptvnow.tv;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import android.app.Activity;
import android.content.pm.PackageInfo;
import java.net.HttpURLConnection;
import java.io.IOException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class UpdateCheckerTest {
  private JSONObject update(int version) throws Exception {
    return new JSONObject().put("update",new JSONObject().put("versionCode",version).put("versionName","1.0.16")
      .put("url","https://panel.example/api/apk/release/SNAPTVNOW.apk").put("notes","Mejoras")
      .put("size",1234).put("sha256","a".repeat(64)));
  }
  @Test public void readsInstalledVersionFromThisDeviceInsteadOfThePanel() throws Exception {
    Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
    PackageInfo info=new PackageInfo();info.packageName=activity.getPackageName();info.versionName="1.0.9";info.versionCode=38;
    Shadows.shadowOf(activity.getPackageManager()).installPackage(info);
    assertEquals("1.0.9",UpdateChecker.installedName(activity));assertEquals(38,UpdateChecker.installedCode(activity));activity.finish();
  }
  @Test public void detectsOnlyNewerReleaseAndCarriesItsIntegrityMetadata() throws Exception {
    UpdateChecker.Update value=UpdateChecker.parse(update(45),44);assertEquals(45,value.code);assertEquals(1234,value.size);assertEquals("a".repeat(64),value.sha256);
    assertNull(UpdateChecker.parse(update(45),45));assertNull(UpdateChecker.parse(update(44),45));assertNull(UpdateChecker.parse(new JSONObject().put("update",JSONObject.NULL),44));
  }
  @Test public void malformedResponseDoesNotMasqueradeAsUpToDate() throws Exception {
    for(JSONObject body:new JSONObject[]{new JSONObject(),new JSONObject().put("update","blocked"),update(0)}){
      try{UpdateChecker.parse(body,44);fail();}catch(IOException correct){}
    }
  }
  @Test public void rejectsInsecureDownloadsAndMalformedChecksums() throws Exception {
    JSONObject body=update(45);body.getJSONObject("update").put("url","http://panel.example/SNAPTVNOW.apk");
    try{UpdateChecker.parse(body,44);fail();}catch(IOException correct){}
    body=update(45);body.getJSONObject("update").put("sha256","not-a-checksum");
    try{UpdateChecker.parse(body,44);fail();}catch(IOException correct){}
  }
  @Test public void cancellationImmediatelyDisconnectsItsOwnRequest(){
    UpdateChecker.Task task=new UpdateChecker.Task();HttpURLConnection connection=mock(HttpURLConnection.class);task.connection=connection;
    task.cancel();assertTrue(task.cancelled);verify(connection).disconnect();
  }
}

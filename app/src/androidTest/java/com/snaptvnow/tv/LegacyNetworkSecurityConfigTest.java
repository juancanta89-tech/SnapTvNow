package com.snaptvnow.tv;

import android.content.pm.ApplicationInfo;
import android.security.NetworkSecurityPolicy;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** API 23 has no per-domain exceptions; the real installed manifest must deny HTTP. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion=23,maxSdkVersion=23)
public class LegacyNetworkSecurityConfigTest {
  @Test public void compiledManifestDeniesEveryHttpHost() throws Exception {
    ApplicationInfo info=InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationInfo();
    assertEquals(0,info.flags & ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC);
    assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted());
    try(MockWebServer server=new MockWebServer()){
      server.enqueue(new MockResponse().setBody("must not reach this server"));
      server.start(InetAddress.getByName("127.0.0.1"),0);
      OkHttpClient client=SharedNetwork.media(1000).newBuilder()
          .dns(host->Collections.singletonList(InetAddress.getByName("127.0.0.1"))).build();
      for(String host:new String[]{"ccf.center","tv.newplytv.online","api.snaptvnow.com","unknown.invalid"}){
        Request request=new Request.Builder().url("http://"+host+":"+server.getPort()+"/fixture").build();
        try(okhttp3.Response ignored=client.newCall(request).execute()){
          fail("HTTP must remain blocked on API 23");
        }catch(IOException expected){assertTrue(expected.getMessage().contains("CLEARTEXT"));}
      }
      assertEquals(0,server.getRequestCount());
    }
  }
  @Test public void artworkUsesHttpsIncludingLegacyHosts() throws Exception {
    for(String host:new String[]{"ccf.center","tv.newplytv.online","api.snaptvnow.com","unknown.invalid"}){
      assertEquals("https://"+host+"/poster.jpg",Artwork.networkUrl("http://"+host+":80/poster.jpg").toString());
    }
  }
}

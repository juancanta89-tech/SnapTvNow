package com.snaptvnow.tv;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class AppConfigTest {
  @Test public void acceptsEveryEnabledDnsInPanelOrderIncludingHttp() throws Exception {
    assertEquals(Arrays.asList("http://ccf.center:8444","https://api.snaptvnow.com",
      "http://tv.newplytv.online:8444","https://snapmovienow-edge.juancanta89.workers.dev"),
      AppConfig.parseServers(new JSONArray().put("http://ccf.center:8444/")
        .put("https://api.snaptvnow.com").put("http://tv.newplytv.online:8444")
        .put("https://snapmovienow-edge.juancanta89.workers.dev")));
  }
  @Test public void malformedEntryDoesNotDiscardTheRemainingDns() throws Exception {
    assertEquals(Arrays.asList("http://first.example:8444","https://second.example"),
      AppConfig.parseServers(new JSONArray().put("broken").put("http://first.example:8444")
        .put("https://user:secret@other.example").put("https://second.example")
        .put("http://first.example:8444/")));
  }
  @Test public void doesNotAcceptAdminPathsOrEmbeddedCredentials() throws Exception {
    for(String url:Arrays.asList("ftp://example.com","https://user:secret@example.com",
      "http://example.com/admin","https://example.com/?password=secret",
      "https://example.com/#fragment","http://example.com:0","http://example.com:65536"))
      assertThrows(Exception.class,()->AppConfig.validate(url));
  }
  @Test public void allDisabledMeansNoServerAndListCannotExceedEight() throws Exception {
    assertEquals(Collections.emptyList(),AppConfig.parseServers(new JSONArray()));
    assertThrows(IllegalStateException.class,()->AppConfig.lastWorking(null,Collections.emptyList()));
    JSONArray tooMany=new JSONArray();for(int i=0;i<9;i++)tooMany.put("https://server"+i+".example");
    assertThrows(Exception.class,()->AppConfig.parseServers(tooMany));
  }
}

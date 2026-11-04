package com.snaptvnow.tv;

import static org.junit.Assert.*;

import android.net.Uri;
import android.security.NetworkSecurityPolicy;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real Android policy and transport tests. Every socket remains on loopback. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 28)
public class NetworkSecurityConfigTest {
  private static final String[] LEGACY = {"ccf.center", "tv.newplytv.online"};
  private MockWebServer server;
  private OkHttpClient client;
  private final AtomicInteger connections = new AtomicInteger();

  @Before public void startServer() throws Exception {
    final InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
    server = new MockWebServer();
    server.start(loopback, 0);
    // Production media client; DNS goes to the local fixture. The listener only
    // observes whether Android allowed a socket and does not change transport.
    client = SharedNetwork.media(3000).newBuilder()
        .dns(host -> Collections.singletonList(loopback))
        .eventListener(new EventListener() {
          @Override public void connectStart(Call call, InetSocketAddress address, Proxy proxy) {
            connections.incrementAndGet();
          }
        }).build();
  }

  @After public void stopServer() throws Exception {
    if (client != null) client.connectionPool().evictAll();
    if (server != null) server.shutdown();
  }

  private String url(String host, String path) {
    return "http://" + host + ":" + server.getPort() + path;
  }

  private RecordedRequest received(String method, String path) throws Exception {
    RecordedRequest request = server.takeRequest(3, TimeUnit.SECONDS);
    assertNotNull("The approved origin must reach the local fixture", request);
    assertEquals(method, request.getMethod());
    assertEquals(path, request.getPath());
    assertNull(request.getHeader("Authorization"));
    return request;
  }

  private byte[] media(DataSpec spec) throws IOException {
    DataSource source = new DefaultDataSource.Factory(
        InstrumentationRegistry.getInstrumentation().getTargetContext(),
        new OkHttpDataSource.Factory(client)).createDataSource();
    try {
      source.open(spec);
      ByteArrayOutputStream result = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int count;
      while ((count = source.read(buffer, 0, buffer.length)) != C.RESULT_END_OF_INPUT) {
        result.write(buffer, 0, count);
        if (result.size() > 65536) throw new IOException("Fixture exceeded its bound");
      }
      return result.toByteArray();
    } finally {
      source.close();
    }
  }

  private byte[] media(String address) throws IOException {
    return media(new DataSpec(Uri.parse(address)));
  }

  private static void assertPolicyFailure(IOException failure) {
    boolean cleartext = false;
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      String message = cause.getMessage();
      if (message != null && message.toLowerCase(Locale.ROOT).contains("cleartext")) cleartext = true;
    }
    assertTrue("Failure must come from cleartext policy, not a missing fixture", cleartext);
  }

  @Test public void androidDeniesCleartextExceptTheTwoExactLegacyHosts() {
    NetworkSecurityPolicy policy = NetworkSecurityPolicy.getInstance();
    assertFalse("No global cleartext permission", policy.isCleartextTrafficPermitted());
    for (String host : LEGACY) assertTrue(host, policy.isCleartextTrafficPermitted(host));
    for (String host : new String[]{"stream.ccf.center", "ccf.center.evil.test",
        "x.tv.newplytv.online", "tv.newplytv.online.evil.test", "newplytv.online",
        "api.snaptvnow.com", "unrelated.example.test", "localhost", "127.0.0.1"}) {
      assertFalse(host, policy.isCleartextTrafficPermitted(host));
    }
  }

  @Test public void approvedOriginsRetainTvMovieAndSeriesHttp() throws Exception {
    for (String host : LEGACY) {
      for (String path : new String[]{"/live/fixture/fixture/1.m3u8",
          "/movie/fixture/fixture/2.mp4", "/series/fixture/fixture/3.mp4"}) {
        server.enqueue(new MockResponse().setBody("fixture"));
        try (Response response = client.newCall(new Request.Builder().url(url(host, path)).build()).execute()) {
          assertEquals(200, response.code());
          assertEquals("fixture", response.body().string());
        }
        assertEquals(host + ":" + server.getPort(), received("GET", path).getHeader("Host"));
      }
    }
    assertEquals(6, server.getRequestCount());
  }

  @Test public void media3RangeAndHeadRemainAvailableOnBothApprovedOrigins() throws Exception {
    for (String host : LEGACY) {
      server.enqueue(new MockResponse().setResponseCode(206)
          .setHeader("Content-Range", "bytes 2-4/8").setBody("cde"));
      DataSpec range = new DataSpec.Builder().setUri(url(host, "/movie/fixture/fixture/2.mp4"))
          .setPosition(2).setLength(3).build();
      assertArrayEquals("cde".getBytes(StandardCharsets.UTF_8), media(range));
      assertEquals("bytes=2-4", received("GET", "/movie/fixture/fixture/2.mp4").getHeader("Range"));
      server.enqueue(new MockResponse().setHeader("Content-Length", "8"));
      try (Response response = client.newCall(new Request.Builder()
          .url(url(host, "/series/fixture/fixture/3.mp4")).head().build()).execute()) {
        assertEquals(200, response.code());
        assertEquals("8", response.header("Content-Length"));
        assertEquals(0, response.body().bytes().length);
      }
      received("HEAD", "/series/fixture/fixture/3.mp4");
    }
  }

  @Test public void foreignLookalikeAndLoopbackHttpNeverOpenASocket() throws Exception {
    for (String host : new String[]{"stream.ccf.center", "ccf.center.evil.test",
        "x.tv.newplytv.online", "tv.newplytv.online.evil.test", "api.snaptvnow.com",
        "unrelated.example.test", "localhost", "127.0.0.1"}) {
      try (Response ignored = client.newCall(new Request.Builder().url(url(host, "/denied")).build()).execute()) {
        fail("HTTP must be denied for " + host);
      } catch (IOException failure) {
        assertPolicyFailure(failure);
      }
    }
    assertEquals("Policy must reject before TCP connect", 0, connections.get());
    assertEquals(0, server.getRequestCount());
  }

  @Test public void approvedHttpRedirectCannotEscapeToForeignHttp() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(302)
        .setHeader("Location", url("foreign.example.test", "/redirected")));
    try (Response ignored = client.newCall(new Request.Builder().url(url(LEGACY[0], "/start")).build()).execute()) {
      fail("Redirect destination must pass the Android policy too");
    } catch (IOException failure) {
      assertPolicyFailure(failure);
    }
    received("GET", "/start");
    assertEquals("Only the original allowed origin opens a socket", 1, connections.get());
    assertEquals(1, server.getRequestCount());
  }

  @Test public void media3ReadsNestedHlsKeyAndSegmentAcrossApprovedOrigins() throws Exception {
    String masterUrl = url(LEGACY[0], "/master.m3u8");
    String variantUrl = url(LEGACY[1], "/nested/media.m3u8");
    String keyUrl = url(LEGACY[0], "/key.bin");
    String segmentUrl = url(LEGACY[1], "/segment.ts");
    String master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1280000\n" + variantUrl + "\n";
    String variant = "#EXTM3U\n#EXT-X-TARGETDURATION:8\n#EXT-X-MEDIA-SEQUENCE:0\n"
        + "#EXT-X-KEY:METHOD=AES-128,URI=\"" + keyUrl + "\",IV=0x1\n"
        + "#EXTINF:8,\n" + segmentUrl + "\n#EXT-X-ENDLIST\n";
    server.enqueue(new MockResponse().setBody(master));
    HlsMultivariantPlaylist parsedMaster = (HlsMultivariantPlaylist) new HlsPlaylistParser()
        .parse(Uri.parse(masterUrl), new ByteArrayInputStream(media(masterUrl)));
    assertEquals(1, parsedMaster.variants.size());
    assertEquals(variantUrl, parsedMaster.variants.get(0).url.toString());
    server.enqueue(new MockResponse().setBody(variant));
    HlsMediaPlaylist parsedVariant = (HlsMediaPlaylist) new HlsPlaylistParser()
        .parse(parsedMaster.variants.get(0).url, new ByteArrayInputStream(media(variantUrl)));
    assertEquals(1, parsedVariant.segments.size());
    HlsMediaPlaylist.Segment segment = parsedVariant.segments.get(0);
    assertEquals(keyUrl, segment.fullSegmentEncryptionKeyUri);
    assertEquals(segmentUrl, segment.url);
    byte[] key = new byte[16];
    for (int i = 0; i < key.length; i++) key[i] = (byte) i;
    server.enqueue(new MockResponse().setBody(new Buffer().write(key)));
    assertArrayEquals(key, media(segment.fullSegmentEncryptionKeyUri));
    server.enqueue(new MockResponse().setBody("fixture-segment"));
    assertEquals("fixture-segment", new String(media(segment.url), StandardCharsets.UTF_8));
    received("GET", "/master.m3u8");
    received("GET", "/nested/media.m3u8");
    received("GET", "/key.bin");
    received("GET", "/segment.ts");
    assertEquals(4, server.getRequestCount());
  }

  @Test public void media3RejectsForeignHlsSegmentBeforeItsSocketOpens() throws Exception {
    String variantUrl = url(LEGACY[0], "/nested.m3u8");
    String foreign = url("foreign.example.test", "/segment.ts");
    server.enqueue(new MockResponse().setBody("#EXTM3U\n#EXT-X-TARGETDURATION:8\n"
        + "#EXTINF:8,\n" + foreign + "\n#EXT-X-ENDLIST\n"));
    HlsMediaPlaylist playlist = (HlsMediaPlaylist) new HlsPlaylistParser()
        .parse(Uri.parse(variantUrl), new ByteArrayInputStream(media(variantUrl)));
    int connected = connections.get();
    try {
      media(playlist.segments.get(0).url);
      fail("Each segment must pass Android policy independently");
    } catch (IOException failure) {
      assertPolicyFailure(failure);
    }
    received("GET", "/nested.m3u8");
    assertEquals(connected, connections.get());
    assertEquals(1, server.getRequestCount());
  }

  @Test public void artworkUsesTheEffectivePolicyWithoutBroadHttpFallback() throws Exception {
    for (String host : LEGACY) {
      String allowed = "http://" + host + ":8444/logo.png?size=2";
      assertEquals(allowed, Artwork.networkUrl(allowed).toString());
    }
    assertEquals("https://images.example.test/logo.png?size=2",
        Artwork.networkUrl("http://images.example.test:80/logo.png?size=2").toString());
    assertEquals("https://api.snaptvnow.com/logo.png",
        Artwork.networkUrl("http://api.snaptvnow.com:80/logo.png").toString());
    assertEquals("https://images.example.test:8080/logo.png?size=2",
        Artwork.networkUrl("http://images.example.test:8080/logo.png?size=2").toString());
    assertEquals("https://images.example.test/logo.png?size=2",
        Artwork.networkUrl("https://images.example.test/logo.png?size=2").toString());
    for (String invalid : new String[]{"ftp://ccf.center/logo.png", "file:///logo.png",
        "http://user:fixture@ccf.center/logo.png"}) {
      try {
        Artwork.networkUrl(invalid);
        fail("Unsupported scheme and userinfo must be rejected");
      } catch (MalformedURLException expected) {
        // A malformed source cannot request a cleartext exception or load a file.
      }
    }
  }
}

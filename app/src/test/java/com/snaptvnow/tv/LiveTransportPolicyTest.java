package com.snaptvnow.tv;
import static org.junit.Assert.*;
import org.junit.Test;

public class LiveTransportPolicyTest {
 @Test public void startsManagedLiveWithContinuousTransportAndKeepsBothRoutes(){
  LiveTransportPolicy p=new LiveTransportPolicy();String hls="https://api.snaptvnow.com:443/live/test/p%20x/169.m3u8",ts=hls.replace("m3u8","ts");
  assertEquals(ts,p.preferred(hls));assertEquals(hls,LiveTransportPolicy.alternative(ts));assertEquals(ts,LiveTransportPolicy.alternative(hls));
  p.rendered(hls,hls);assertEquals(hls,p.preferred(hls));assertEquals("https://api.snaptvnow.com:443/live/test/p%20x/170.ts",p.preferred(hls.replace("169","170")));
 }
 @Test public void foreignServersVodAndUnrelatedSignalsCannotChangeTheRoute(){
  LiveTransportPolicy p=new LiveTransportPolicy();for(String u:new String[]{"https://other.example/live/test/p/1.m3u8","http://api.snaptvnow.com/live/test/p/1.m3u8","https://api.snaptvnow.com.evil.example/live/test/p/1.m3u8","https://api.snaptvnow.com/movie/test/p/1.m3u8","https://api.snaptvnow.com/live/test/p/1.m3u8?ticket=x"}){assertEquals(u,p.preferred(u));assertNull(LiveTransportPolicy.alternative(u));}
  String hls="https://api.snaptvnow.com/live/test/p/1.m3u8";p.rendered(hls,hls.replace("/1.","/2."));assertTrue(p.preferred(hls).endsWith("1.ts"));
 }
}

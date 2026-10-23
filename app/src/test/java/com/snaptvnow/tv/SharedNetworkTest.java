package com.snaptvnow.tv;
import static org.junit.Assert.*;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.junit.Test;
public class SharedNetworkTest {
 @Test public void apiAndMediaShareConnectionsWithoutSharingPerRequestCredentials()throws Exception{
  try(MockWebServer server=new MockWebServer()){
   server.enqueue(new MockResponse().setBody("login"));server.enqueue(new MockResponse().setBody("video"));server.start();
   try(Response r=SharedNetwork.execute(new Request.Builder().url(server.url("/login")).header("Authorization","private").build(),1000,1000)){assertEquals("login",SharedNetwork.text(r,100));}
   try(Response r=SharedNetwork.media(1000).newCall(new Request.Builder().url(server.url("/video")).build()).execute()){assertEquals("video",SharedNetwork.text(r,100));}
   RecordedRequest login=server.takeRequest(),video=server.takeRequest();assertEquals(0,login.getSequenceNumber());assertEquals(1,video.getSequenceNumber());assertNull(video.getHeader("Authorization"));
  }
 }
 @Test public void jsonRedirectsCannotForwardLoginAndOversizedBodiesAreBounded()throws Exception{
  try(MockWebServer server=new MockWebServer()){
   server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location","https://other.invalid/steal"));server.enqueue(new MockResponse().setBody("123456"));server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location","https://other.invalid/video"));server.start();
   try(Response r=SharedNetwork.execute(new Request.Builder().url(server.url("/login?password=fixture")).build(),1000,1000)){assertEquals(302,r.code());}
   try(Response r=SharedNetwork.execute(new Request.Builder().url(server.url("/big")).build(),1000,1000)){try{SharedNetwork.text(r,5);fail("must reject oversized response");}catch(java.io.IOException expected){assertEquals("response_too_large",expected.getMessage());}}
   try(Response r=SharedNetwork.media(1000).newCall(new Request.Builder().url(server.url("/media")).build()).execute()){assertEquals(302,r.code());}
  }
 }
}

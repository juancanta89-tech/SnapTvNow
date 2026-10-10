package com.snaptvnow.tv;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class UpdateDownloadTest {
  @Rule public TemporaryFolder temp=new TemporaryFolder();
  private static final String APK="https://github.com/owner/app/releases/download/1.0.12/app-release.apk";
  private static class Response extends HttpURLConnection {
    final int status;final byte[] body;final String location,length;boolean disconnected;
    Response(URL url,int status,String location,byte[] body,String length){super(url);this.status=status;this.location=location;this.body=body;this.length=length;}
    @Override public int getResponseCode(){return status;}
    @Override public String getHeaderField(String field){return field.equals("Location")?location:field.equals("Content-Length")?length:null;}
    @Override public ByteArrayInputStream getInputStream(){return new ByteArrayInputStream(body);}
    @Override public void connect(){}
    @Override public void disconnect(){disconnected=true;}
    @Override public boolean usingProxy(){return false;}
  }
  private File destination(){return new File(temp.getRoot(),"update.apk");}
  private void fails(UpdateDownload task,String link) throws Exception {
    try{task.download(link,destination(),(bytes,total)->{});fail("Expected rejection");}catch(IOException expected){}
    assertFalse(destination().exists());
    assertEquals(0,temp.getRoot().list().length);
  }
  @Test public void githubRedirectDownloadsBytesWithoutBrowserOrCredentials() throws Exception {
    byte[] expected={80,75,3,4,5,6};List<Response> seen=new ArrayList<>();
    UpdateDownload task=new UpdateDownload(url->{
      Response response=seen.isEmpty()?new Response(url,302,"https://release-assets.githubusercontent.com/asset?token=abc",new byte[0],null)
        :new Response(url,200,null,expected,"6");
      seen.add(response);return response;
    });
    List<Long> progress=new ArrayList<>();
    task.download(APK,destination(),(bytes,total)->progress.add(bytes));
    assertArrayEquals(expected,Files.readAllBytes(destination().toPath()));
    assertEquals("release-assets.githubusercontent.com",seen.get(1).getURL().getHost());
    assertEquals(Long.valueOf(6),progress.get(progress.size()-1));
    for(Response response:seen){assertTrue(response.disconnected);assertFalse(response.getInstanceFollowRedirects());assertNull(response.getRequestProperty("Authorization"));}
  }
  @Test public void rejectsHttpCredentialsFragmentsAndNonApkUrls() throws Exception {
    UpdateDownload task=new UpdateDownload(url->{throw new AssertionError("Must not connect");});
    for(String url:new String[]{"http://example.org/app.apk","https://user:secret@example.org/app.apk","https://example.org/app.apk#secret","https://example.org/page"})fails(task,url);
  }
  @Test public void rejectsHttpsToHttpRedirect() throws Exception {
    fails(new UpdateDownload(url->new Response(url,302,"http://example.org/app.apk",new byte[0],null)),APK);
  }
  @Test public void rejectsRedirectLoopAfterSixResponses() throws Exception {
    List<URL> seen=new ArrayList<>();
    fails(new UpdateDownload(url->{seen.add(url);return new Response(url,302,"/again.apk",new byte[0],null);}),APK);
    assertEquals(6,seen.size());
  }
  @Test public void truncatedOrEmptyDownloadsAreNeverSaved() throws Exception {
    fails(new UpdateDownload(url->new Response(url,200,null,new byte[]{1,2,3},"20")),APK);
    fails(new UpdateDownload(url->new Response(url,200,null,new byte[0],null)),APK);
  }
  @Test public void serverErrorAndOversizedFileLeaveNoPartialApk() throws Exception {
    fails(new UpdateDownload(url->new Response(url,404,null,new byte[0],null)),APK);
    fails(new UpdateDownload(url->new Response(url,200,null,new byte[0],Long.toString(UpdateDownload.MAX_BYTES+1))),APK);
  }
  @Test public void cancellationDisconnectsAndRemovesOnlyItsPartialFile() throws Exception {
    Response response=new Response(new URL(APK),200,null,new byte[100],"100");
    UpdateDownload task=new UpdateDownload(url->response);
    try{task.download(APK,destination(),(bytes,total)->{if(bytes>0)task.cancel();});fail();}catch(IOException expected){}
    assertTrue(response.disconnected);assertFalse(destination().exists());assertEquals(0,temp.getRoot().list().length);
  }
  @Test public void missingContentLengthStillCompletesWithProgress() throws Exception {
    new UpdateDownload(url->new Response(url,200,null,new byte[]{1,2,3},null))
      .download(APK,destination(),(bytes,total)->assertEquals(-1,total));
    assertEquals(3,destination().length());
  }
}

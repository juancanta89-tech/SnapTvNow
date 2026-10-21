package com.snaptvnow.tv;

import static org.junit.Assert.*;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class SessionRecoveryTest {
  private static final List<String> SERVERS=Arrays.asList("https://first.example","https://second.example");
  private static final String ACTIVE="{\"user_info\":{\"auth\":1,\"status\":\"Active\",\"exp_date\":\"1893456000\",\"max_connections\":\"3\"}}";
  private XtreamClient saved(){XtreamClient.SERVER=SERVERS.get(0);return new XtreamClient("synthetic-client","synthetic-password","1","1");}

  @Test public void failedRestoreDoesNotExposeStaleAccessAndRetryReusesTheSavedCredentials(){
    XtreamClient saved=saved();int[] writes={0};
    SessionRecovery.Connector unavailable=(account,servers)->XtreamClient.attemptLogin(servers,account.username(),account.password(),(server,user,password)->{throw new SocketTimeoutException();},null);
    SessionRecovery.Result failure=SessionRecovery.reconnect(saved,SERVERS,unavailable,verified->writes[0]++);
    assertNull(failure.client);assertNotNull(failure.message);assertEquals(0,writes[0]);
    assertEquals("synthetic-client",saved.username());assertEquals("synthetic-password",saved.password());
    SessionRecovery.Result retry=SessionRecovery.reconnect(saved,SERVERS,(account,servers)->XtreamClient.attemptLogin(servers,account.username(),account.password(),(server,user,password)->{
      assertEquals(saved.username(),user);assertEquals(saved.password(),password);
      if(server.equals(SERVERS.get(0)))throw new SocketTimeoutException();return ACTIVE;
    },null),verified->{writes[0]++;assertEquals("1893456000",verified.expires);assertEquals("3",verified.maxConnections);});
    assertNotNull(retry.client);assertNotSame(saved,retry.client);assertEquals(SERVERS.get(1),retry.client.server());assertNull(retry.message);assertEquals(1,writes[0]);
  }

  @Test public void expiredOrRejectedSavedAccountCannotFallBackToItsCachedAccess(){
    for(String response:Arrays.asList(ACTIVE.replace("Active","Expired"),"{\"user_info\":{\"auth\":0}}")){
      SessionRecovery.Result r=SessionRecovery.reconnect(saved(),SERVERS,(account,servers)->XtreamClient.attemptLogin(servers,account.username(),account.password(),(server,user,password)->response,null),verified->fail("Unverified account must not be saved"));
      assertNull(r.client);assertNotNull(r.message);
    }
  }

  @Test public void storageFailureKeepsOnlyTheVerifiedConnectionAndDoesNotExposePrivateErrors(){
    XtreamClient saved=saved();SessionRecovery.Result r=SessionRecovery.reconnect(saved,SERVERS,(account,servers)->XtreamClient.attemptLogin(servers,account.username(),account.password(),(server,user,password)->ACTIVE,null),verified->{throw new Exception("private-storage-detail");});
    assertNotNull(r.client);assertNotSame(saved,r.client);assertEquals("1893456000",r.client.expires);assertNotNull(r.message);assertFalse(r.message.contains("private-storage-detail"));assertFalse(r.message.contains(saved.password()));
  }

  @Test public void noSavedAccountDoesNotAttemptAConnectionOrWrite(){
    SessionRecovery.Result r=SessionRecovery.reconnect(null,SERVERS,(account,servers)->{fail("No saved credentials");return null;},verified->fail("No verified account"));
    assertNull(r.client);assertNull(r.message);
  }
}

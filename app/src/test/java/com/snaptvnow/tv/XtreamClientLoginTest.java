package com.snaptvnow.tv;

import static org.junit.Assert.*;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class XtreamClientLoginTest {
  private static final String ACTIVE="{\"user_info\":{\"auth\":1,\"status\":\"Active\",\"exp_date\":\"1893456000\",\"max_connections\":\"3\"}}";
  private static final List<String> SERVERS=Arrays.asList("http://first.example:8444",
    "https://second.example","http://third.example:8444","https://fourth.example");

  @Test public void triesDnsInOrderAfterRejectionAndTimeoutAndStopsAtFirstSuccess() throws Exception {
    List<String> attempted=new ArrayList<>(),progress=new ArrayList<>();
    XtreamClient client=XtreamClient.attemptLogin(SERVERS,"ClienteDemo74","synthetic-client-password",(server,user,pass)->{
      attempted.add(server);assertEquals("ClienteDemo74",user);assertEquals("synthetic-client-password",pass);
      if(server.equals(SERVERS.get(0)))throw new XtreamClient.HttpFailure(401);
      if(server.equals(SERVERS.get(1)))throw new SocketTimeoutException();
      return ACTIVE;
    },(number,total)->progress.add(number+"/"+total));
    assertEquals(SERVERS.subList(0,3),attempted);assertEquals(Arrays.asList("1/4","2/4","3/4"),progress);
    assertEquals(SERVERS.get(2),client.server());assertEquals("ClienteDemo74",client.username());
    assertEquals("synthetic-client-password",client.password());assertEquals("3",client.maxConnections);
  }
  @Test public void forbiddenRateLimitedAndBadJsonStillReachTheFourthDns() throws Exception {
    List<String> attempted=new ArrayList<>();
    XtreamClient client=XtreamClient.attemptLogin(SERVERS,"synthetic","synthetic",(server,user,pass)->{
      attempted.add(server);
      if(attempted.size()==1)throw new XtreamClient.HttpFailure(403);
      if(attempted.size()==2)throw new XtreamClient.HttpFailure(429);
      return attempted.size()==3?"<html>upstream error</html>":ACTIVE;
    },null);
    assertEquals(SERVERS,attempted);assertEquals(SERVERS.get(3),client.server());
  }
  @Test public void inactiveAccountOnOneDnsDoesNotRejectAnActiveAccountOnTheNext() throws Exception {
    List<String> attempted=new ArrayList<>();
    XtreamClient client=XtreamClient.attemptLogin(SERVERS,"synthetic","synthetic",(server,user,pass)->{
      attempted.add(server);return attempted.size()==1?ACTIVE.replace("Active","Expired"):ACTIVE;
    },null);
    assertEquals(SERVERS.subList(0,2),attempted);assertEquals(SERVERS.get(1),client.server());
  }
  @Test public void onlyReportsBadCredentialsAfterEveryDnsHasBeenTried() throws Exception {
    List<String> attempted=new ArrayList<>();
    Exception error=assertThrows(Exception.class,()->XtreamClient.attemptLogin(SERVERS,"synthetic","synthetic",(server,user,pass)->{
      attempted.add(server);if(attempted.size()%2==0)throw new XtreamClient.HttpFailure(401);
      return "{\"user_info\":{\"auth\":0}}";
    },null));
    assertEquals(SERVERS,attempted);assertTrue(error.getMessage().contains("usuario y la contraseña"));
    assertFalse(error.getMessage().contains("servidor disponible"));
  }
  @Test public void unavailableOrUnrelatedResponsesAreNotCalledInvalidCredentials() throws Exception {
    List<String> attempted=new ArrayList<>();
    Exception error=assertThrows(Exception.class,()->XtreamClient.attemptLogin(SERVERS,"synthetic","synthetic",(server,user,pass)->{
      attempted.add(server);if(attempted.size()==1)throw new XtreamClient.HttpFailure(503);
      if(attempted.size()==2)throw new SocketTimeoutException();
      return "{\"error\":\"unrelated\"}";
    },null));
    assertEquals(SERVERS,attempted);assertTrue(error.getMessage().contains("servidor disponible"));
    assertFalse(error.getMessage().contains("contraseña"));
  }
}

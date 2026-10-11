package com.snaptvnow.tv;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.json.JSONObject;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class NativePlaybackMetricsTest {
 @Test public void startupBufferingPauseAndQualityAreMeasuredWithoutIdentity()throws Exception{
  NativePlaybackMetrics.Counters c=new NativePlaybackMetrics.Counters(new ProfileReference("live","123","ccf",""),0);assertNull(c.report(1000,false));c.firstFrame(1500);c.height(1080);c.state(1500,true,false,true);c.state(2500,false,true,true);c.state(4500,true,false,true);c.state(5500,false,false,false);
  JSONObject m=c.report(8000,false);assertEquals(1500,m.getLong("startupMs"));assertEquals(4000,m.getLong("watchMs"));assertEquals(2000,m.getLong("stallMs"));assertEquals(1,m.getInt("stalls"));assertEquals("1080",m.getString("quality"));assertEquals("android",m.getString("platform"));assertFalse(m.has("id"));assertFalse(m.has("username"));assertNull(c.report(9000,false));
 }
 @Test public void anErrorBeforeTheFirstFrameReportsAFailedStartOnce()throws Exception{
  NativePlaybackMetrics.Counters c=new NativePlaybackMetrics.Counters(new ProfileReference("movie","123","ccf",""),0);c.error();JSONObject m=c.report(4000,false);assertTrue(m.getBoolean("failedStart"));assertTrue(m.getBoolean("attempted"));assertTrue(m.isNull("startupMs"));assertFalse(m.getBoolean("started"));assertNull(c.report(5000,true));
 }
 @Test public void stoppingBeforeTheFirstFrameIsCounted()throws Exception{
  NativePlaybackMetrics.Counters c=new NativePlaybackMetrics.Counters(new ProfileReference("series","123","ccf","88"),0);assertTrue(c.report(3000,true).getBoolean("failedStart"));
 }
 @Test public void frameAndTrafficReportsContainDeltasWithoutDoubleCounting()throws Exception{
  NativePlaybackMetrics.Counters c=new NativePlaybackMetrics.Counters(new ProfileReference("live","123","ccf",""),0);
  c.firstFrame(1000);c.frames(90,10);c.transfer(12000);JSONObject first=c.report(1000,false);
  assertEquals(100,first.getLong("decoded"));assertEquals(10,first.getLong("dropped"));assertEquals(12000,first.getLong("bytes"));
  c.transfer(12000);assertNull(c.report(2000,false));c.frames(48,2);c.transfer(20000);JSONObject second=c.report(3000,false);
  assertEquals(50,second.getLong("decoded"));assertEquals(2,second.getLong("dropped"));assertEquals(8000,second.getLong("bytes"));assertFalse(second.getBoolean("started"));
 }
 @Test public void missingTrafficMeasurementIsNotReportedAsMeasuredZero()throws Exception{
  NativePlaybackMetrics.Counters c=new NativePlaybackMetrics.Counters(new ProfileReference("movie","123","ccf",""),0);
  c.firstFrame(1000);assertTrue(c.report(1000,false).isNull("bytes"));
 }
 @Test public void measuredCountersStayWithinTheExistingServerContract()throws Exception{
  NativePlaybackMetrics.Counters c=new NativePlaybackMetrics.Counters(new ProfileReference("live","123","ccf",""),0);
  c.frames(150000,50000);c.transfer(300000000);JSONObject row=c.report(1000,false);
  assertEquals(100000,row.getLong("decoded"));assertTrue(row.getLong("dropped")<=row.getLong("decoded"));assertEquals(250000000,row.getLong("bytes"));
 }
}

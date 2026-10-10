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
}

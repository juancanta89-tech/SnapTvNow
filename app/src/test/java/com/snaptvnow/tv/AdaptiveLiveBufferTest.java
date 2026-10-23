package com.snaptvnow.tv;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class AdaptiveLiveBufferTest {
 @Test public void repeatedStallsIncreaseReserveWithoutAnUnboundedLatencyPenalty(){
  AdaptiveLiveBuffer b=new AdaptiveLiveBuffer();assertEquals(3000,b.resumeMs());b.stalled(1000);b.stalled(1500);assertEquals(3000,b.resumeMs());
  b.stalled(3000);assertEquals(5000,b.resumeMs());b.stalled(5000);assertEquals(7000,b.resumeMs());b.stalled(7000);assertEquals(8000,b.resumeMs());b.stalled(9000);assertEquals(8000,b.resumeMs());
  b.healthy(10000);b.healthy(130000);assertEquals(7000,b.resumeMs());b.stalled(260000);assertEquals(3000,b.resumeMs());
 }
}

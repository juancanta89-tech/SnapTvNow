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
 @Test public void actualLoadControlKeepsFastStartupAndCannotDeadlockAtItsByteBudget(){
  AdaptiveLiveBuffer b=new AdaptiveLiveBuffer();
  androidx.media3.exoplayer.analytics.PlayerId id=androidx.media3.exoplayer.analytics.PlayerId.UNSET;
  b.onPrepared(id);b.stalled(1000);b.stalled(3000);b.stalled(5000);b.stalled(7000);
  androidx.media3.common.Timeline timeline=new androidx.media3.exoplayer.source.SinglePeriodTimeline(60000000L,true,false,false,null,androidx.media3.common.MediaItem.fromUri("https://example.invalid/channel.ts"));
  androidx.media3.exoplayer.source.MediaSource.MediaPeriodId period=new androidx.media3.exoplayer.source.MediaSource.MediaPeriodId(timeline.getUidOfPeriod(0));
  java.util.function.BiFunction<Long,Boolean,androidx.media3.exoplayer.LoadControl.Parameters> params=(duration,rebuffer)->new androidx.media3.exoplayer.LoadControl.Parameters(id,timeline,period,0,duration,1f,true,rebuffer,androidx.media3.common.C.TIME_UNSET,androidx.media3.common.C.TIME_UNSET);
  assertTrue(b.shouldStartPlayback(params.apply(1000000L,false)));
  assertFalse(b.shouldStartPlayback(params.apply(4000000L,true)));
  assertTrue(b.shouldStartPlayback(params.apply(8000000L,true)));
  androidx.media3.exoplayer.upstream.Allocator allocator=b.getAllocator(id);
  java.util.List<androidx.media3.exoplayer.upstream.Allocation> allocated=new java.util.ArrayList<>();
  while(allocator.getTotalBytesAllocated()<32*1024*1024)allocated.add(allocator.allocate());
  assertTrue(b.shouldStartPlayback(params.apply(4000000L,true)));
  for(androidx.media3.exoplayer.upstream.Allocation allocation:allocated)allocator.release(allocation);
  b.onReleased(id);
 }

}

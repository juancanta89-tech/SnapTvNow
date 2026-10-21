package com.snaptvnow.tv;
import static org.junit.Assert.*;
import org.junit.Test;
public class LivePlaybackTimingTest {
 @Test public void startsWithOneSecondButPreservesEstablishedStreamWindows(){
  assertEquals(1000,LivePlaybackTiming.INITIAL_BUFFER_MS);
  assertEquals(9000,LivePlaybackTiming.quietWindow(false,0));
  assertEquals(9000,LivePlaybackTiming.quietWindow(false,12000));
  assertEquals(15000,LivePlaybackTiming.quietWindow(true,0));
  assertEquals(26000,LivePlaybackTiming.quietWindow(true,12000));
  assertEquals(45000,LivePlaybackTiming.quietWindow(true,60000));
 }
 @Test public void continuousProgressHasABoundedSeparateDeadline(){
  assertEquals(45000,LivePlaybackTiming.loadingWindow(false,12000));
  assertEquals(78000,LivePlaybackTiming.loadingWindow(true,12000));
  assertEquals(90000,LivePlaybackTiming.loadingWindow(true,60000));
 }
}

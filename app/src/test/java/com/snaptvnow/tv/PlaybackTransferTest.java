package com.snaptvnow.tv;

import static org.junit.Assert.*;
import org.junit.Test;

public class PlaybackTransferTest {
  @Test public void fileReadsAndInvalidCountsDoNotPolluteNetworkMeasurements(){
    PlaybackTransfer t=new PlaybackTransfer();t.onBytesTransferred(null,null,false,5000);t.onBytesTransferred(null,null,true,-1);assertEquals(0,t.bytes());
    t.onBytesTransferred(null,null,true,2000);t.onBytesTransferred(null,null,true,3000);assertEquals(5000,t.bytes());
  }
  @Test public void concurrentMediaAndPlaylistTransfersAreCountedExactlyOnce()throws Exception{
    PlaybackTransfer t=new PlaybackTransfer();Thread[] readers=new Thread[4];
    for(int i=0;i<readers.length;i++){readers[i]=new Thread(()->{for(int n=0;n<1000;n++)t.onBytesTransferred(null,null,true,1024);});readers[i].start();}
    for(Thread reader:readers)reader.join();assertEquals(4*1000*1024L,t.bytes());
  }
}

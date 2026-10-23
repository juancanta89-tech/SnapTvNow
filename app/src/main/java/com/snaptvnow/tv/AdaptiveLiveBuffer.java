package com.snaptvnow.tv;

import androidx.media3.common.C;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.upstream.DefaultAllocator;

/** Fast first tune; repeated real stalls increase resume reserve, within the existing byte budget. */
final class AdaptiveLiveBuffer extends DefaultLoadControl {
  private volatile int resumeMs=3000;
  private long lastStall=-1,healthySince=-1;
  AdaptiveLiveBuffer(){
    super(new DefaultAllocator(true,C.DEFAULT_BUFFER_SEGMENT_SIZE),20000,20000,40000,40000,
        LivePlaybackTiming.INITIAL_BUFFER_MS,LivePlaybackTiming.INITIAL_BUFFER_MS,3000,3000,
        32*1024*1024,false,false,0,false);
  }
  synchronized void stalled(long now){
    if(lastStall>=0&&now-lastStall<1000)return;
    resumeMs=lastStall>=0&&now-lastStall<120000?Math.min(8000,resumeMs+2000):3000;
    lastStall=now;healthySince=-1;
  }
  synchronized void healthy(long now){if(healthySince<0)healthySince=now;else if(now-healthySince>=120000){resumeMs=Math.max(3000,resumeMs-1000);healthySince=now;}}
  int resumeMs(){return resumeMs;}
  @Override public boolean shouldStartPlayback(LoadControl.Parameters p){
    if(!p.rebuffering)return super.shouldStartPlayback(p);
    long reserve=resumeMs*1000L;
    if(p.targetLiveOffsetUs!=C.TIME_UNSET)reserve=Math.min(reserve,p.targetLiveOffsetUs/2);
    return super.shouldStartPlayback(p)&&Util.getPlayoutDurationForMediaDuration(p.bufferedDurationUs,p.playbackSpeed)>=reserve;
  }
}

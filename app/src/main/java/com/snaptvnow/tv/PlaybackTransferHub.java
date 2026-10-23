package com.snaptvnow.tv;

import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import java.util.IdentityHashMap;
import java.util.Map;

/** Captures the viewing identity per source; canceled old downloads cannot pollute a new channel. */
final class PlaybackTransferHub implements TransferListener {
  private final Map<DataSource,PlaybackTransfer> active=new IdentityHashMap<>();
  private PlaybackTransfer current;
  synchronized PlaybackTransfer begin(){current=new PlaybackTransfer();return current;}
  public synchronized void onTransferInitializing(DataSource source,DataSpec spec,boolean network){if(current!=null)active.put(source,current);}
  public synchronized void onTransferStart(DataSource source,DataSpec spec,boolean network){PlaybackTransfer t=active.get(source);if(t!=null)t.onTransferStart(source,spec,network);}
  public synchronized void onBytesTransferred(DataSource source,DataSpec spec,boolean network,int count){PlaybackTransfer t=active.get(source);if(t!=null)t.onBytesTransferred(source,spec,network,count);}
  public synchronized void onTransferEnd(DataSource source,DataSpec spec,boolean network){active.remove(source);}
}

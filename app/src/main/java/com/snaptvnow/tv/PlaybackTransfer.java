package com.snaptvnow.tv;

import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

/** Per-player network payload counter. Never retains a URL, header or account. */
final class PlaybackTransfer implements TransferListener {
  private volatile long bytes;
  long bytes() { return bytes; }
  @Override public void onTransferInitializing(DataSource source,DataSpec spec,boolean network) {}
  @Override public void onTransferStart(DataSource source,DataSpec spec,boolean network) {}
  @Override public synchronized void onBytesTransferred(DataSource source,DataSpec spec,boolean network,int count) {
    if(network && count>0)bytes+=Math.min((long)count,Long.MAX_VALUE-bytes);
  }
  @Override public void onTransferEnd(DataSource source,DataSpec spec,boolean network) {}
}

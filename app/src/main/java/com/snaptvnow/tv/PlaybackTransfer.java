package com.snaptvnow.tv;

import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import java.util.List;
import java.util.Map;

/** Per-player payload counter and opaque monitoring receipt, kept in memory only. */
final class PlaybackTransfer implements TransferListener {
  static final String MONITOR_REQUEST_HEADER="X-SNAP-Playback-Monitor";
  static final String MONITOR_RESPONSE_HEADER="X-SNAP-Playback-Quality";
  private volatile long bytes;
  private volatile String qualityTicket;
  long bytes() { return bytes; }
  String qualityTicket() { return qualityTicket; }
  @Override public void onTransferInitializing(DataSource source,DataSpec spec,boolean network) {}
  @Override public void onTransferStart(DataSource source,DataSpec spec,boolean network) {
    if(!network||source==null||spec==null)return;
    android.net.Uri uri=spec.uri;
    if(!"https".equals(uri.getScheme())||!"api.snaptvnow.com".equalsIgnoreCase(uri.getHost())||uri.getPort()!=-1&&uri.getPort()!=443||uri.getUserInfo()!=null||uri.getPath()==null||!uri.getPath().matches("/(live|movie|series|exclusive)/.+"))return;
    for(Map.Entry<String,List<String>> header:source.getResponseHeaders().entrySet()){
      if(!MONITOR_RESPONSE_HEADER.equalsIgnoreCase(header.getKey()))continue;
      List<String> values=header.getValue();
      if(values!=null&&values.size()==1&&values.get(0)!=null&&values.get(0).matches("[A-Za-z0-9_-]{40,4096}"))qualityTicket=values.get(0);
      return;
    }
  }
  @Override public synchronized void onBytesTransferred(DataSource source,DataSpec spec,boolean network,int count) {
    if(network && count>0)bytes+=Math.min((long)count,Long.MAX_VALUE-bytes);
  }
  @Override public void onTransferEnd(DataSource source,DataSpec spec,boolean network) {}
}

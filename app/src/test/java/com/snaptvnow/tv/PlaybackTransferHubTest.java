package com.snaptvnow.tv;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class PlaybackTransferHubTest {
 @Test public void lateOldChannelBytesNeverReachTheNewViewing(){
  PlaybackTransferHub hub=new PlaybackTransferHub();DataSource old=mock(DataSource.class),next=mock(DataSource.class);DataSpec spec=new DataSpec(Uri.parse("https://example.invalid/video"));
  PlaybackTransfer a=hub.begin();hub.onTransferInitializing(old,spec,true);hub.onBytesTransferred(old,spec,true,100);
  PlaybackTransfer b=hub.begin();hub.onTransferInitializing(next,spec,true);hub.onBytesTransferred(old,spec,true,50);hub.onBytesTransferred(next,spec,true,20);
  assertEquals(150,a.bytes());assertEquals(20,b.bytes());hub.onTransferEnd(old,spec,true);hub.onBytesTransferred(old,spec,true,99);assertEquals(150,a.bytes());
 }
}

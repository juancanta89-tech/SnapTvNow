package com.snaptvnow.tv;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import android.net.Uri;
import androidx.media3.datasource.*;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class PlaybackSourceOwnershipTest {
 @Test public void sourceCreatedAfterChannelSwitchKeepsTheFactorysOriginalViewing()throws Exception{
  MainActivity activity=Robolectric.buildActivity(MainActivity.class).get();
  java.lang.reflect.Field f=MainActivity.class.getDeclaredField("playbackTransfer");f.setAccessible(true);
  PlaybackTransfer old=new PlaybackTransfer(),next=new PlaybackTransfer();f.set(activity,old);DataSource.Factory oldFactory=activity.playbackDataFactory();
  f.set(activity,next);DataSource.Factory nextFactory=activity.playbackDataFactory();
  try(MockWebServer server=new MockWebServer()){
   server.enqueue(new MockResponse().setBody("old bytes"));server.enqueue(new MockResponse().setBody("new"));server.start();
   DataSpec spec=new DataSpec(Uri.parse(server.url("/video").toString()));byte[] buffer=new byte[64];
   DataSource late=oldFactory.createDataSource();late.open(spec);while(late.read(buffer,0,64)!=-1){}late.close();
   DataSource current=nextFactory.createDataSource();current.open(spec);while(current.read(buffer,0,64)!=-1){}current.close();
   assertEquals(9,old.bytes());assertEquals(3,next.bytes());
  }finally{java.lang.reflect.Field e=MainActivity.class.getDeclaredField("searchExecutor");e.setAccessible(true);((java.util.concurrent.ExecutorService)e.get(activity)).shutdownNow();}
 }
}

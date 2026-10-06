package com.snaptvnow.tv;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class ContinueArtworkTest {
  private Context context;
  @Before public void setUp(){context=RuntimeEnvironment.getApplication();clear();}
  @After public void clear(){if(context==null)return;File dir=new File(context.getCacheDir(),"continue_artwork");File[] files=dir.listFiles();if(files!=null)for(File file:files)file.delete();}
  private Catalog.Item movie(int n){return new Catalog.Item("movie"+n,"Película","Películas","","");}
  @Test public void thumbnailPixelsSurviveReopenInPrivateCacheAndAreBounded(){
    String key=PlaybackHistory.artworkKey("alice",movie(1));Bitmap bitmap=Bitmap.createBitmap(800,400,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff29dce8);Artwork.storeHistory(context,key,bitmap);
    File file=Artwork.historyFile(context,key);assertTrue(file.isFile());assertTrue(file.getPath().startsWith(context.getCacheDir().getPath()));Bitmap stored=BitmapFactory.decodeFile(file.getPath());assertNotNull(stored);assertEquals(320,stored.getWidth());assertEquals(160,stored.getHeight());assertFalse(file.getName().contains("alice"));
  }
  @Test public void cacheKeepsThirtyTwoCoversAndCannotGrowWithTheEntireCatalog(){
    Bitmap bitmap=Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888);
    for(int i=1;i<=35;i++){String key=PlaybackHistory.artworkKey("alice",movie(i));Artwork.storeHistory(context,key,bitmap);Artwork.historyFile(context,key).setLastModified(i*1000);}
    File[] files=new File(context.getCacheDir(),"continue_artwork").listFiles((dir,name)->name.endsWith(".jpg"));assertNotNull(files);assertEquals(32,files.length);assertFalse(Artwork.historyFile(context,PlaybackHistory.artworkKey("alice",movie(1))).exists());assertTrue(Artwork.historyFile(context,PlaybackHistory.artworkKey("alice",movie(35))).exists());
  }
  @Test public void seriesChaptersShareCoverWhileAccountsAndMoviesRemainSeparate(){
    Catalog.Item first=new Catalog.Item("episode1","Capítulo","Series","","","","series1","Serie",1,1),second=new Catalog.Item("episode2","Capítulo","Series","","","","series1","Serie",1,2);
    assertEquals(PlaybackHistory.artworkKey("alice",first),PlaybackHistory.artworkKey("alice",second));assertNotEquals(PlaybackHistory.artworkKey("alice",first),PlaybackHistory.artworkKey("bob",first));assertNotEquals(PlaybackHistory.artworkKey("alice",first),PlaybackHistory.artworkKey("alice",movie(1)));
  }
}

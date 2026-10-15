package com.snaptvnow.tv;
import static org.junit.Assert.*;
import android.app.Activity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ExclusiveCatalogTest {
 private static final String SERVER="https://api.snaptvnow.com";
 private JSONObject title(String id,String type)throws Exception{return new JSONObject().put("exclusive_id",id).put("type",type).put("name","Título de prueba").put("container_extension","mp4").put("stream_icon",SERVER+"/exclusive-cover/abc").put("smn_profile",new JSONObject().put("type",type).put("id",id).put("server","gnula"));}
 @Test public void movieUsesManagedPlaybackAndKeepsWebProfileIdentity()throws Exception{
  JSONObject movie=title("gnula-movie","movie").put("page","https://untrusted.test/steal");List<Catalog.Item> list=ExclusiveCatalog.parse(SERVER,"a/b","p ?",new JSONObject().put("items",new JSONArray().put(movie).put(title("../escape","movie"))));
  assertEquals(1,list.size());Catalog.Item item=list.get(0);assertEquals("movie-exclusive-gnula-movie",item.id);assertEquals(SERVER+"/exclusive/a%2Fb/p%20%3F/gnula-movie.mp4",item.url);assertFalse(item.url.contains("untrusted"));assertEquals("movie:gnula:gnula-movie",item.profile.key());
 }
 @Test public void otherProvidersCannotSupplyExclusiveUrlsOrReadCredentials()throws Exception{
  JSONObject data=new JSONObject().put("items",new JSONArray().put(title("custom-movie","movie")));assertTrue(ExclusiveCatalog.parse("https://provider.test","test","test",data).isEmpty());
  try{ExclusiveCatalog.playback("https://api.snaptvnow.com.evil.test","test","test","movie-a","mp4");fail();}catch(IllegalArgumentException expected){}
  assertEquals("",ExclusiveCatalog.rawId("movie12"));assertEquals("",ExclusiveCatalog.rawId("movie-exclusive-../../bad"));
 }
 @Test public void seriesHaveNoMovieUrlAndEpisodesKeepTheirOrderAndParent()throws Exception{
  Catalog.Item series=ExclusiveCatalog.parse(SERVER,"test","test",new JSONObject().put("items",new JSONArray().put(title("custom-series","series")))).get(0);assertEquals("",series.url);
  JSONObject second=new JSONObject().put("exclusive_id","episode-two").put("title","Dos").put("season",1).put("episode_num",2).put("container_extension","mkv").put("smn_profile",new JSONObject().put("type","series").put("id","custom-series").put("server","exclusive").put("episodeId","episode-two"));
  JSONObject first=new JSONObject(second.toString()).put("exclusive_id","episode-one").put("episode_num",1);
  List<Catalog.Item> episodes=ExclusiveCatalog.episodes(SERVER,"test","test",series,new JSONObject().put("episodes",new JSONObject().put("1",new JSONArray().put(second).put(first))));assertEquals(2,episodes.size());assertEquals("episode-exclusive-episode-one",episodes.get(0).id);assertEquals(series.id,episodes.get(1).seriesId);assertEquals("episode-two",episodes.get(1).profile.episodeId);assertTrue(episodes.get(1).url.endsWith("/episode-two.mkv"));
 }
 @Test public void continueWatchingResolvesWithTheCurrentCredentialsWithoutPersistingThem()throws Exception{
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();try{
   PlaybackHistory history=new PlaybackHistory(activity);String account="exclusive-history-test";Catalog.Item item=ExclusiveCatalog.parse(SERVER,"old-user","old-password",new JSONObject().put("items",new JSONArray().put(title("custom-movie","movie")))).get(0);history.remember(account,item);history.save(account,item.id,50000,100000,true);PlaybackHistory.Entry entry=history.recent(account).get(0);assertFalse(entry.item.url.contains("old-password"));
   String previous=XtreamClient.SERVER;try{XtreamClient.SERVER=SERVER;Catalog.Item resumed=new XtreamClient("current-user","current-password","","").resume(entry);assertTrue(resumed.url.contains("/current-user/current-password/"));assertEquals(item.profile.key(),resumed.profile.key());}finally{XtreamClient.SERVER=previous;}
  }finally{activity.finish();}
 }
}

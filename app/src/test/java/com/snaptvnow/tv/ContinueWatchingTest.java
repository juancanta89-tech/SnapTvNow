package com.snaptvnow.tv;

import static org.junit.Assert.*;
import android.app.Activity;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class ContinueWatchingTest {
  private PlaybackHistory history;
  private Catalog.Item movie,episode1,episode2,episode3;
  @Before public void setUp(){
    RuntimeEnvironment.getApplication().getSharedPreferences("vod_playback",0).edit().clear().commit();
    history=new PlaybackHistory(RuntimeEnvironment.getApplication());
    movie=new Catalog.Item("movie17","Una película","Películas","https://old.example/movie/alice/secret/17.mkv","Drama");
    episode1=episode("episode41",1);episode2=episode("episode42",2);episode3=episode("episode43",3);
  }
  private Catalog.Item episode(String id,int number){return new Catalog.Item(id,"La que se avecina · Episodio "+number,"Series","https://old.example/series/alice/secret/"+id.substring(7)+".mp4","Temporada 1","","series8","La que se avecina",1,number);}
  private void watch(Catalog.Item item,long position){history.remember("alice",item);history.save("alice",item.id,position,2_400_000,true);}

  @Test public void movieAndLatestChapterSurviveACompleteHistoryReopen(){
    watch(movie,91_000);watch(episode1,100_000);watch(episode2,745_000);
    List<PlaybackHistory.Entry> entries=new PlaybackHistory(RuntimeEnvironment.getApplication()).recent("alice");
    assertEquals(2,entries.size());assertEquals("episode42",entries.get(0).item.id);assertEquals(745_000,entries.get(0).position);
    assertEquals("La que se avecina",entries.get(0).title());assertTrue(entries.get(0).detail().contains("T01 · E02"));assertTrue(entries.get(0).detail().contains("12:25"));assertEquals("movie17",entries.get(1).item.id);
  }
  @Test public void accountsAndMoviesAreSeparatedFromSeriesEvenWithMatchingNumericIds(){
    watch(movie,91_000);history.remember("bob",episode1);history.save("bob",episode1.id,66_000,100_000,true);
    assertEquals(1,history.recent("alice").size());assertEquals("movie17",history.recent("alice").get(0).item.id);assertEquals(66_000,history.recent("bob").get(0).position);assertTrue(history.recent("carol").isEmpty());
  }
  @Test public void unfinishedLatestChapterIsSelectedAndCompletedChapterSuggestsNext(){
    watch(episode1,20_000);watch(episode2,50_000);List<Catalog.Item> episodes=Arrays.asList(episode1,episode2,episode3);
    assertEquals(1,EpisodeSelection.recommended(episodes,history,"alice"));assertTrue(EpisodeSelection.labels(episodes,history,"alice")[1].startsWith("Continuar · 0:50"));
    history.complete("alice",episode2.id);assertEquals(2,EpisodeSelection.recommended(episodes,history,"alice"));assertTrue(EpisodeSelection.labels(episodes,history,"alice")[1].startsWith("✓ Visto"));
    watch(episode3,20_000);history.complete("alice",episode3.id);assertEquals(2,EpisodeSelection.recommended(episodes,history,"alice"));
  }
  @Test public void completingMovieRemovesItAndRestartingAddsItBack(){
    watch(movie,91_000);history.complete("alice",movie.id);assertTrue(history.recent("alice").isEmpty());history.reset("alice",movie.id);assertFalse(history.completed("alice",movie.id));assertEquals(0,history.position("alice",movie.id));
    history.save("alice",movie.id,8_000,2_400_000,true);assertEquals(8_000,history.recent("alice").get(0).position);
  }
  @Test public void metadataNeverPersistsPlaybackUrlHostOrSubscriberCredentials() throws Exception {
    watch(movie,91_000);String data=RuntimeEnvironment.getApplication().getSharedPreferences("vod_playback",0).getAll().toString();assertFalse(data.contains("old.example"));assertFalse(data.contains("alice"));assertFalse(data.contains("secret"));
    XtreamClient.SERVER="https://new.example";Catalog.Item current=new XtreamClient("new user","new password","","3").resume(history.recent("alice").get(0));
    assertEquals("https://new.example/movie/new%20user/new%20password/17.mkv",current.url);assertEquals(91_000,history.position("alice",current.id));
  }
  @Test public void failedStartupCannotReplaceLastViewedChapter(){
    watch(episode1,91_000);history.remember("alice",episode2);Player player=mock(Player.class);VodPlaybackSession session=new VodPlaybackSession(player,history,"alice",episode2.id);
    session.start(MediaItem.fromUri(episode2.url),-1,false);session.close();assertEquals("episode41",history.recent("alice").get(0).item.id);
  }
  @Test public void realCardShowsChapterAndTimeAndItsClickContinues(){
    watch(episode2,745_000);Activity activity=Robolectric.buildActivity(Activity.class).setup().get();int[] clicks={0};ContinueWatchingCard card=new ContinueWatchingCard(activity,history.recent("alice").get(0),()->clicks[0]++);activity.setContentView(card);
    LinearLayout text=(LinearLayout)card.getChildAt(1);assertEquals("La que se avecina",((TextView)text.getChildAt(0)).getText());String detail=((TextView)text.getChildAt(1)).getText().toString();assertTrue(detail.contains("E02"));assertTrue(detail.contains("12:25"));assertTrue(card.isFocusable());card.performClick();assertEquals(1,clicks[0]);activity.finish();
  }
  @Test public void providerSeasonsAreOrderedNumericallyAndKeepTheirParentAndEpisodeNumbers() throws Exception {
    Catalog.Item series=new Catalog.Item("series8","La que se avecina","Series","","Comedia");JSONObject root=new JSONObject("{\"episodes\":{\"10\":[{\"id\":\"103\",\"episode_num\":3}],\"2\":[{\"id\":\"22\",\"episode_num\":2},{\"id\":\"21\",\"episode_num\":1}]}}");
    List<Catalog.Item> list=XtreamClient.parseEpisodes("https://example.invalid","u","p",series,root);assertEquals("episode21",list.get(0).id);assertEquals("episode22",list.get(1).id);assertEquals(10,list.get(2).seasonNumber);assertEquals("series8",list.get(0).seriesId);assertEquals(1,list.get(0).episodeNumber);
  }
  @Test public void legacyPositionsStillResumeAndCorruptOptionalMetadataDoesNotBreakHistory(){
    history.save("alice",episode2.id,66_000,100_000,true);assertEquals(1,EpisodeSelection.recommended(Arrays.asList(episode1,episode2),history,"alice"));watch(movie,10_000);
    RuntimeEnvironment.getApplication().getSharedPreferences("vod_playback",0).edit().putString("broken.metadata","{").commit();assertEquals(1,history.recent("alice").size());assertEquals(66_000,history.position("alice",episode2.id));
  }
}

package com.snaptvnow.tv;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class ContinueQueueTest {
  private PlaybackHistory history;
  @Before public void setUp(){RuntimeEnvironment.getApplication().getSharedPreferences("vod_playback",0).edit().clear().commit();history=reopen();}
  private PlaybackHistory reopen(){return new PlaybackHistory(RuntimeEnvironment.getApplication());}
  private Catalog.Item movie(int n){return new Catalog.Item("movie"+n,"Película "+n,"Películas","https://example.invalid/"+n+".mp4","");}
  private Catalog.Item episode(int series,int chapter){return new Catalog.Item("episode"+series+"x"+chapter,"Capítulo "+chapter,"Series","https://example.invalid/1.mp4","","","series"+series,"Serie "+series,1,chapter);}
  private void watch(Catalog.Item item){history.remember("alice",item);history.save("alice",item.id,91_000,200_000,true);}
  @Test public void separateLimitsKeepSixteenTitlesAndMostRecentlyWatchedFirst(){
    for(int i=1;i<=10;i++){watch(movie(i));watch(episode(i,1));}
    assertEquals(16,history.recent("alice").size());
    List<PlaybackHistory.Entry> movies=history.recent("alice",PlaybackHistory.Kind.MOVIES),series=history.recent("alice",PlaybackHistory.Kind.SERIES);
    assertEquals(8,movies.size());assertEquals(8,series.size());assertEquals("movie10",movies.get(0).item.id);assertEquals("movie3",movies.get(7).item.id);
    assertEquals("series10",series.get(0).item.seriesId);assertEquals("series3",series.get(7).item.seriesId);
  }
  @Test public void ninthMovieEvictsOnlyOldestMovieAndWatchingItAgainMovesItToFront(){
    for(int i=1;i<=8;i++){watch(movie(i));watch(episode(i,1));}history.recent("alice");watch(movie(9));
    assertEquals("movie2",history.recent("alice",PlaybackHistory.Kind.MOVIES).get(7).item.id);
    assertEquals(8,history.recent("alice",PlaybackHistory.Kind.SERIES).size());assertEquals(91_000,history.position("alice","movie1"));
    watch(movie(1));List<PlaybackHistory.Entry> movies=history.recent("alice",PlaybackHistory.Kind.MOVIES);
    assertEquals(8,movies.size());assertEquals("movie1",movies.get(0).item.id);assertEquals("movie3",movies.get(7).item.id);
  }
  @Test public void removalSurvivesReopenAndCatalogRefreshButRealPlaybackRestoresTitle(){
    watch(movie(1));history.removeFromContinue("alice",history.recent("alice").get(0));history=reopen();
    history.remember("alice",movie(1));history.rememberBookmarks("alice",Arrays.asList(movie(1)));
    assertTrue(history.recent("alice").isEmpty());assertEquals(91_000,history.position("alice","movie1"));assertFalse(history.completed("alice","movie1"));
    history.save("alice","movie1",92_000,200_000,true);assertEquals(92_000,history.recent("alice").get(0).position);
  }
  @Test public void removingSeriesPreservesAllChaptersAndRecommendsItsLastChapter(){
    Catalog.Item first=episode(1,1),second=episode(1,2),third=episode(1,3);
    watch(first);history.complete("alice",first.id);watch(second);watch(third);
    history.removeFromContinue("alice",history.recent("alice").get(0));history=reopen();
    assertTrue(history.recent("alice").isEmpty());assertTrue(history.completed("alice",first.id));assertEquals(91_000,history.position("alice",second.id));
    assertEquals(2,EpisodeSelection.recommended(Arrays.asList(first,second,third),history,"alice"));
    watch(second);assertEquals(1,history.recent("alice").size());assertEquals(second.id,history.recent("alice").get(0).item.id);
    assertEquals(1,EpisodeSelection.recommended(Arrays.asList(first,second,third),history,"alice"));
  }
  @Test public void removingAVisibleMovieCannotBringBackPreviouslyEvictedMovies(){
    for(int i=1;i<=10;i++)watch(movie(i));assertEquals(8,history.recent("alice").size());
    history.removeFromContinue("alice",history.recent("alice").get(0));history=reopen();
    List<PlaybackHistory.Entry> entries=history.recent("alice");assertEquals(7,entries.size());assertEquals("movie3",entries.get(6).item.id);
    for(PlaybackHistory.Entry entry:new ArrayList<>(entries))history.removeFromContinue("alice",entry);
    assertTrue(reopen().recent("alice").isEmpty());assertEquals(91_000,history.position("alice","movie1"));
  }
  @Test public void evictionStillKeepsTheLastChapterRecommendation(){
    Catalog.Item first=episode(1,1),second=episode(1,2),third=episode(1,3);
    watch(first);watch(second);history.complete("alice",second.id);
    for(int i=2;i<=10;i++)watch(episode(i,1));assertEquals(8,history.recent("alice").size());
    assertEquals(2,EpisodeSelection.recommended(Arrays.asList(first,second,third),reopen(),"alice"));
    assertTrue(reopen().completed("alice",second.id));
  }
  @Test public void removingOneAccountsTitleDoesNotChangeAnotherAccountsQueue(){
    watch(movie(1));history.remember("bob",movie(1));history.save("bob","movie1",33_000,200_000,true);
    history.removeFromContinue("alice",history.recent("alice").get(0));
    assertTrue(history.recent("alice").isEmpty());assertEquals(33_000,history.recent("bob").get(0).position);
    for(int i=2;i<=12;i++)watch(movie(i));assertEquals(8,history.recent("alice").size());assertEquals(1,history.recent("bob").size());
  }
  @Test public void failedStartupAndZeroProgressCannotUnhideRemovedTitle(){
    watch(movie(1));history.removeFromContinue("alice",history.recent("alice").get(0));history.remember("alice",movie(1));
    assertTrue(history.recent("alice").isEmpty());history.save("alice","movie1",0,200_000,true);assertTrue(history.recent("alice").isEmpty());
    history.save("alice","movie1",5_000,200_000,true);assertEquals(1,history.recent("alice").size());
  }
  @Test public void legacyBookmarksAreRecoveredThenCappedWithoutLosingAnyPosition(){
    List<Catalog.Item> catalog=new ArrayList<>();for(int i=1;i<=11;i++){catalog.add(movie(i));history.save("alice","movie"+i,91_000,200_000,true);}
    history.rememberBookmarks("alice",catalog);assertEquals(8,history.recent("alice").size());history=reopen();
    for(int i=1;i<=11;i++)assertEquals(91_000,history.position("alice","movie"+i));
    assertEquals("movie11",history.recent("alice").get(0).item.id);
  }
}

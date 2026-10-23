package com.snaptvnow.tv;
import static org.junit.Assert.*;
import android.content.Context;
import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class LocalHistoryRecoveryTest {
 private Context context;private PlaybackHistory history;private String target,old;
 private final Catalog.Item parent=new Catalog.Item("series8","La que se avecina","Series","","");
 private final Catalog.Item episode=new Catalog.Item("episode42","La que se avecina · Episodio 2","Series","http://current.invalid/series/fixture/private/42.mp4","Temporada 1","","series8","La que se avecina",1,2);
 @Before public void setup(){context=RuntimeEnvironment.getApplication();context.getSharedPreferences("vod_playback",0).edit().clear().commit();history=new PlaybackHistory(context);target=AccountFavorites.scope("http://current.invalid","Nito");old=AccountFavorites.scope("http://previous.invalid","Nito");}
 private LocalHistoryRecovery recovery(){return LocalHistoryRecovery.verified("Nito","fixture-password","Nito","fixture-password","http://current.invalid",Arrays.asList("http://previous.invalid"));}
 private LocalHistoryRecovery.Source source(){return new LocalHistoryRecovery.Source(){public List<Catalog.Item> movies(){return Collections.emptyList();}public List<Catalog.Item> series(){return Arrays.asList(parent);}public List<Catalog.Item> episodes(Catalog.Item p){return Arrays.asList(episode);}};}
 private void watch(String owner,long position){history.remember(owner,episode);history.save(owner,episode.id,position,2400000,true);}
 @Test public void upgradeRecoversExactLegacyUsernameHistoryWithoutDeletingItsOriginal()throws Exception{
  watch("Nito",745000);long at=history.updated("Nito",episode.id);assertTrue(history.recent(target).isEmpty());
  assertEquals(1,recovery().recover(history,source()));PlaybackHistory reopened=new PlaybackHistory(context);
  assertEquals(745000,reopened.position(target,episode.id));assertEquals(at,reopened.updated(target,episode.id));assertEquals(745000,reopened.position("Nito",episode.id));assertEquals("La que se avecina",reopened.recent(target).get(0).title());
 }
 @Test public void restoredSavedCredentialsCannotBeUsedToReadAnotherAccountOrChangedPassword(){
  watch(old,745000);
  assertNull(LocalHistoryRecovery.verified("Nito","fixture-password","other","fixture-password","http://current.invalid",Arrays.asList("http://previous.invalid")));
  assertNull(LocalHistoryRecovery.verified("Nito","fixture-password","Nito","different","http://current.invalid",Arrays.asList("http://previous.invalid")));
 }
 @Test public void matchingDnsAliasCanRecoverButCannotOverwriteNewerViewingOrAReset()throws Exception{
  watch(old,745000);watch(target,900000);assertEquals(0,recovery().recover(history,source()));assertEquals(900000,history.position(target,episode.id));
  history.reset(target,episode.id);assertEquals(0,recovery().recover(history,source()));assertEquals(0,history.position(target,episode.id));assertEquals(745000,history.position(old,episode.id));
 }
 @Test public void aReusedNumericSeriesIdWithADifferentTitleCannotTakeTheOldPosition()throws Exception{
  watch(old,745000);LocalHistoryRecovery.Source different=new LocalHistoryRecovery.Source(){public List<Catalog.Item> movies(){return Collections.emptyList();}public List<Catalog.Item> series(){return Arrays.asList(new Catalog.Item("series8","Otra serie","Series","",""));}public List<Catalog.Item> episodes(Catalog.Item p){throw new AssertionError("must not read unrelated episodes");}};
  assertEquals(0,recovery().recover(history,different));assertTrue(history.recent(target).isEmpty());assertEquals(745000,history.position(old,episode.id));
 }
 @Test public void unavailableCatalogDoesNotEraseTheOriginalAndCanBeRetried()throws Exception{
  watch(old,745000);LocalHistoryRecovery.Source unavailable=new LocalHistoryRecovery.Source(){public List<Catalog.Item> movies(){return Collections.emptyList();}public List<Catalog.Item> series()throws Exception{throw new Exception("offline");}public List<Catalog.Item> episodes(Catalog.Item p){return Collections.emptyList();}};
  try{recovery().recover(history,unavailable);fail("expected unavailable catalog");}catch(Exception expected){}
  assertEquals(745000,history.position(old,episode.id));assertTrue(history.recent(target).isEmpty());assertEquals(1,recovery().recover(history,source()));
 }
 @Test public void deliberatelyHiddenOrCompletedSeriesStateIsPreserved()throws Exception{
  watch(old,745000);history.removeFromContinue(old,history.recent(old).get(0));assertEquals(0,recovery().recover(history,source()));assertEquals(745000,history.position(old,episode.id));
  watch("Nito",745000);history.complete("Nito",episode.id);assertEquals(1,recovery().recover(history,source()));assertTrue(history.completed(target,episode.id));assertEquals(1,history.recent(target).size());
 }
 @Test public void oldPositionOnlyChaptersAreRecoveredWhenTheActualSeriesIsOpened(){
  history.save("Nito",episode.id,929000,4895000,true);assertEquals(1,recovery().restoreLoadedLegacy(history,Arrays.asList(episode)));
  assertEquals(929000,history.position(target,episode.id));assertEquals(929000,history.position("Nito",episode.id));assertEquals(1,history.recent(target).size());
  history.save(target,episode.id,1000000,4895000,true);assertEquals(0,recovery().restoreLoadedLegacy(history,Arrays.asList(episode)));assertEquals(1000000,history.position(target,episode.id));
 }
 @Test public void recoveringOneUserCannotImportAnotherUsersBookmarks()throws Exception{
  watch(AccountFavorites.scope("http://previous.invalid","other"),745000);assertFalse(recovery().available(history));assertEquals(0,recovery().recover(history,source()));assertTrue(history.recent(target).isEmpty());
 }
 @Test public void recoveryRespectsANewerRemovalInTheDestination()throws Exception{
  watch(old,745000);watch(target,900000);history.removeFromContinue(target,history.recent(target).get(0));
  assertEquals(0,recovery().recover(history,source()));assertTrue(history.recent(target).isEmpty());assertEquals(745000,history.position(old,episode.id));
 }
 @Test public void aReusedEpisodeIdCannotTransferProgressToAnotherChapter()throws Exception{
  watch(old,745000);Catalog.Item otherChapter=new Catalog.Item(episode.id,episode.title,"Series",episode.url,"Temporada 1","","series8",parent.title,1,3);
  LocalHistoryRecovery.Source changed=new LocalHistoryRecovery.Source(){public List<Catalog.Item> movies(){return Collections.emptyList();}public List<Catalog.Item> series(){return Arrays.asList(parent);}public List<Catalog.Item> episodes(Catalog.Item p){return Arrays.asList(otherChapter);}};
  assertEquals(0,recovery().recover(history,changed));assertTrue(history.recent(target).isEmpty());
 }

}

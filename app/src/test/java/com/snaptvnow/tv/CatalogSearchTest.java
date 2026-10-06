package com.snaptvnow.tv;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class CatalogSearchTest {
  private Catalog.Item item(String id,String title,String section,String folder) {
    return new Catalog.Item(id,title,section,"https://example.invalid/video",folder);
  }
  @Test public void findsTitlesInsideUnopenedFoldersAndBeyondOldLimits() throws Exception {
    List<Catalog.Item> catalog=new ArrayList<>();
    for(int i=0;i<320;i++)catalog.add(item("series"+i,i==319?"La casa de papel":"Serie "+i,"Series",i<160?"Netflix":"Estrenos 2026"));
    CatalogSearch search=new CatalogSearch(scope->catalog);
    List<Catalog.Item> found=CatalogSearch.filter(search.all(CatalogSearch.Scope.SERIES),CatalogSearch.Scope.SERIES,"casa papel");
    assertEquals(1,found.size());assertEquals("series319",found.get(0).id);assertEquals("Estrenos 2026",found.get(0).description);
  }
  @Test public void respectsMoviesSeriesAndChannelsEvenForIdenticalTitles() throws Exception {
    List<Catalog.Item> mixed=Arrays.asList(item("movie1","Match","Películas","A"),item("series1","Match","Series","B"),item("live1","Match","TV en vivo","C"),item("episode1","Match","Series","D"));
    CatalogSearch search=new CatalogSearch(scope->mixed);
    for(CatalogSearch.Scope scope:CatalogSearch.Scope.values()) {
      List<Catalog.Item> matches=CatalogSearch.filter(search.all(scope),scope,"Match");
      assertEquals(1,matches.size());assertTrue(matches.get(0).id.startsWith(scope.kind));
    }
  }
  @Test public void usesCurrentSectionWhenOpeningMagnifier() {
    assertEquals(CatalogSearch.Scope.SERIES,CatalogSearch.Scope.forSection("Series",CatalogSearch.Scope.MOVIES));
    assertEquals(CatalogSearch.Scope.MOVIES,CatalogSearch.Scope.forSection("Películas",CatalogSearch.Scope.SERIES));
    assertEquals(CatalogSearch.Scope.CHANNELS,CatalogSearch.Scope.forSection("TV en vivo",CatalogSearch.Scope.MOVIES));
    assertEquals(CatalogSearch.Scope.CHANNELS,CatalogSearch.Scope.forSection("PPV HOY",CatalogSearch.Scope.MOVIES));
  }
  @Test public void matchesAccentsCaseAndAllQueryWordsButNotFolderNames() {
    List<Catalog.Item> catalog=Arrays.asList(item("movie1","CORAZÓN de León","Películas","A"),item("movie2","Otra película","Películas","Corazon leon"));
    List<Catalog.Item> found=CatalogSearch.filter(catalog,CatalogSearch.Scope.MOVIES,"  corazon LEON  ");
    assertEquals(1,found.size());assertEquals("movie1",found.get(0).id);
    assertTrue(CatalogSearch.filter(catalog,CatalogSearch.Scope.MOVIES," ").isEmpty());
  }
  @Test public void cachesCompleteCatalogPerSectionAndPerSubscriberSession() throws Exception {
    AtomicInteger loads=new AtomicInteger();
    CatalogSearch.Source source=scope->{loads.incrementAndGet();return Collections.singletonList(item(scope.kind+"1","Test",scope.section,"A"));};
    CatalogSearch first=new CatalogSearch(source);
    assertSame(first.all(CatalogSearch.Scope.MOVIES),first.all(CatalogSearch.Scope.MOVIES));
    first.all(CatalogSearch.Scope.SERIES);assertEquals(2,loads.get());
    new CatalogSearch(source).all(CatalogSearch.Scope.MOVIES);assertEquals(3,loads.get());
  }
  @Test public void failedFullCatalogCanRetryAndDuplicateIdsAppearOnce() throws Exception {
    AtomicInteger loads=new AtomicInteger();Catalog.Item movie=item("movie1","Title","Películas","A");
    CatalogSearch search=new CatalogSearch(scope->{if(loads.incrementAndGet()==1)throw new Exception("offline");return Arrays.asList(movie,movie);});
    try{search.all(CatalogSearch.Scope.MOVIES);fail("Must report failure");}catch(Exception expected){}
    assertEquals(1,search.all(CatalogSearch.Scope.MOVIES).size());assertEquals(2,loads.get());
  }
}

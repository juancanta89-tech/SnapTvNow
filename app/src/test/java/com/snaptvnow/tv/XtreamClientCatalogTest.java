package com.snaptvnow.tv;

import static org.junit.Assert.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class XtreamClientCatalogTest {
  @Test public void fullCatalogParsesEveryFolderAndTitleAfter150() throws Exception {
    JSONArray response=new JSONArray();Map<String,String> names=new HashMap<>();names.put("1","Estrenos");names.put("2","Clásicos");
    for(int i=1;i<=330;i++)response.put(new JSONObject().put("stream_id",i).put("name","Película "+i).put("category_id",i<=150?"1":"2").put("container_extension","mkv"));
    List<Catalog.Item> catalog=XtreamClient.parseItems("https://example.invalid","test","test","Películas",response,names);
    assertEquals(330,catalog.size());assertEquals("movie330",catalog.get(329).id);assertEquals("Clásicos",catalog.get(329).description);
    assertTrue(catalog.get(329).url.endsWith("/330.mkv"));
  }
  @Test public void seriesResultsOpenSeriesInsteadOfInventingMovieUrls() throws Exception {
    JSONArray response=new JSONArray().put(new JSONObject().put("series_id",123).put("name","Serie").put("cover","https://example.invalid/cover.jpg"));
    Catalog.Item item=XtreamClient.parseItems("https://example.invalid","test","test","Series",response,new HashMap<>()).get(0);
    assertEquals("series123",item.id);assertEquals("",item.url);assertEquals("Series",item.category);assertTrue(item.artwork.endsWith("cover.jpg"));
  }
  @Test public void rejectsInvalidIdsAndEncodesCredentialsAsPathSegments() throws Exception {
    JSONArray response=new JSONArray().put(new JSONObject().put("stream_id","../../bad")).put(new JSONObject().put("stream_id","9").put("name","Canal"));
    List<Catalog.Item> catalog=XtreamClient.parseItems("https://example.invalid","a/b","p ?","TV en vivo",response,new HashMap<>());
    assertEquals(1,catalog.size());assertEquals("live9",catalog.get(0).id);assertTrue(catalog.get(0).url.contains("/a%2Fb/p%20%3F/9.m3u8"));
  }
}

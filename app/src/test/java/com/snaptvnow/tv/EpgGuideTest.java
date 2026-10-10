package com.snaptvnow.tv;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class EpgGuideTest {
 @Test public void boundedGuideDecodesUtf8AndKeepsPlainTitles()throws Exception{
  assertEquals("News",EpgGuide.text("News"));String title="Película española";assertEquals(title,EpgGuide.text(android.util.Base64.encodeToString(title.getBytes(StandardCharsets.UTF_8),android.util.Base64.NO_WRAP)));
  JSONArray rows=new JSONArray().put(JSONObject.NULL).put(new JSONObject().put("title","Terminó").put("stop_timestamp",1)).put(new JSONObject().put("title","Actual").put("start_timestamp",2).put("stop_timestamp",6));for(int i=0;i<50;i++)rows.put(new JSONObject().put("title","Programa "+i));String guide=EpgGuide.format(rows,4000);assertTrue(guide.contains("En directo · Actual"));assertFalse(guide.contains("Terminó"));assertEquals(24,guide.split("\n\n").length);assertEquals("Guía no disponible",EpgGuide.format(null,4000));
 }
}

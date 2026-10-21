package com.snaptvnow.tv;
import static org.junit.Assert.*;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class BoxPanelTest {
  private JSONObject identity(String id,String design) throws Exception {return new JSONObject().put("appId",id).put("packageName",BoxPanel.packageName(id)).put("name","Marca "+design).put("design",design).put("accent","#16d4cd").put("background","#07151f");}
  private JSONObject config(JSONObject identity) throws Exception {JSONObject brand=new JSONObject(identity.toString()).put("id",identity.getString("appId"));return new JSONObject().put("active",true).put("packageName",identity.getString("packageName")).put("app",brand).put("servers",new JSONArray().put("https://a.example.com")).put("slides",new JSONArray());}
  @Test public void eachDesignAndPackageHasItsOwnIdentity() throws Exception {
    for(String design:new String[]{"classic","cinema","compact"}){JSONObject app=identity("11111111-1111-4111-8111-111111111111",design);assertSame(app,BoxPanel.parseIdentity(app,app.getString("packageName")));BoxPanel.validateConfig(config(app),app);}
    JSONObject first=identity("11111111-1111-4111-8111-111111111111","classic"),second=identity("22222222-2222-4222-8222-222222222222","cinema");
    assertNotEquals(first.getString("packageName"),second.getString("packageName"));
    assertThrows(Exception.class,()->BoxPanel.parseIdentity(first,second.getString("packageName")));
    assertThrows(Exception.class,()->BoxPanel.validateConfig(config(second),first));
  }
  @Test public void disabledMissingDnsAndInvalidBrandNeverFallBackToSnap() throws Exception {
    JSONObject app=identity("11111111-1111-4111-8111-111111111111","classic");
    assertThrows(Exception.class,()->BoxPanel.validateConfig(config(app).put("active",false),app));
    assertThrows(Exception.class,()->BoxPanel.validateConfig(config(app).put("servers",new JSONArray()),app));
    assertThrows(Exception.class,()->BoxPanel.validateConfig(config(app).put("servers",new JSONArray().put("http://user:password@foreign.example")),app));
    assertThrows(Exception.class,()->BoxPanel.parseIdentity(identity("11111111-1111-4111-8111-111111111111","untrusted"),app.getString("packageName")));
  }
}

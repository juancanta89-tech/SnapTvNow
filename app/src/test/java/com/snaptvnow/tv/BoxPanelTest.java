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
    for(String design:new String[]{"classic","cinema","compact","aura","panorama","stadium","noir","horizon"}){JSONObject app=identity("11111111-1111-4111-8111-111111111111",design);assertSame(app,BoxPanel.parseIdentity(app,app.getString("packageName")));BoxPanel.validateConfig(config(app),app);}
    JSONObject styled=identity("11111111-1111-4111-8111-111111111111","aura").put("style",new JSONObject().put("wallpaper","sunset").put("density","compact").put("welcome","Mi marca"));
    BoxPanel.validateBrand(styled);styled.getJSONObject("style").put("wallpaper","untrusted");assertThrows(Exception.class,()->BoxPanel.validateBrand(styled));
    JSONObject first=identity("11111111-1111-4111-8111-111111111111","classic"),second=identity("22222222-2222-4222-8222-222222222222","cinema");
    assertNotEquals(first.getString("packageName"),second.getString("packageName"));
    assertThrows(Exception.class,()->BoxPanel.parseIdentity(first,second.getString("packageName")));
    assertThrows(Exception.class,()->BoxPanel.validateConfig(config(second),first));
  }
  @Test public void artworkHasScopedUrlsAndValidZones() throws Exception {
    JSONObject app=identity("11111111-1111-4111-8111-111111111111","cinema"),value=config(app),brand=value.getJSONObject("app");
    JSONObject hero=new JSONObject().put("fit","contain").put("url",BoxPanel.ORIGIN+"/api/boxpanel/client/"+app.getString("appId")+"/artwork/hero?v=1");brand.put("artwork",new JSONObject().put("hero",hero));BoxPanel.validateConfig(value,app);
    hero.put("url",BoxPanel.ORIGIN+"/api/boxpanel/client/22222222-2222-4222-8222-222222222222/artwork/hero?v=1");assertThrows(Exception.class,()->BoxPanel.validateConfig(value,app));
    hero.put("fit","untrusted");assertThrows(Exception.class,()->BoxPanel.validateArtwork(brand));
  }
  @Test public void disabledMissingDnsAndInvalidBrandNeverFallBackToSnap() throws Exception {
    JSONObject app=identity("11111111-1111-4111-8111-111111111111","classic");
    assertThrows(Exception.class,()->BoxPanel.validateConfig(config(app).put("active",false),app));
    assertThrows(Exception.class,()->BoxPanel.validateConfig(config(app).put("servers",new JSONArray()),app));
    assertThrows(Exception.class,()->BoxPanel.validateConfig(config(app).put("servers",new JSONArray().put("http://user:password@foreign.example")),app));
    assertThrows(Exception.class,()->BoxPanel.parseIdentity(identity("11111111-1111-4111-8111-111111111111","untrusted"),app.getString("packageName")));
  }
}

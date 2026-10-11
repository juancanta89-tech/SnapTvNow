package com.snaptvnow.tv;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.Test;

public class LiveQualityAlternativeTest {
  private Catalog.Item channel(String id,String title,String group,String url){return new Catalog.Item("live"+id,title,"TV en vivo",url,"Deportes").withLiveIdentity(group,"espn.mx");}
  private Catalog.Item channel(String id,String title){return channel(id,title,"7","https://example.invalid/live/account/test/"+id+".m3u8");}
  private Catalog.Item match(Catalog.Item source,Catalog.Item... candidates){return LiveQualityAlternative.find720(source,Arrays.asList(candidates));}
  @Test public void acceptsUniqueExplicitQualityLabelsAndRetainsRegion(){Catalog.Item source=channel("1","MX | ESPN [1080p]"),target=channel("2","mx| espn [720P]");assertSame(target,match(source,target));}
  @Test public void channelNumberCannotBeConfused(){assertNull(match(channel("1","ESPN 1080"),channel("2","ESPN 2 720")));}
  @Test public void languageAndRegionMustAgree(){Catalog.Item source=channel("1","MX ESPN Español 1080");assertNull(match(source,channel("2","US ESPN Español 720")));assertNull(match(source,channel("2","MX ESPN Inglés 720")));}
  @Test public void duplicateAlternativesAreAmbiguous(){assertNull(match(channel("1","ESPN 1080"),channel("2","ESPN 720"),channel("3","ESPN 720")));}
  @Test public void categoryIdsMustAgreeEvenWhenFolderNamesMatch(){assertNull(match(channel("1","ESPN 1080"),channel("2","ESPN 720","8","https://example.invalid/live/account/test/2.m3u8")));}
  @Test public void missingCategoryIdentityCannotOfferAnAlternative(){assertNull(match(channel("1","ESPN 1080").withLiveIdentity("","espn.mx"),channel("2","ESPN 720")));}
  @Test public void folderAndSectionMustAgree(){Catalog.Item source=channel("1","ESPN 1080");assertNull(match(source,new Catalog.Item("live2","ESPN 720","PPV HOY","https://example.invalid/live/account/test/2.m3u8","Deportes").withLiveIdentity("7","espn.mx")));assertNull(match(source,new Catalog.Item("live2","ESPN 720","TV en vivo","https://example.invalid/live/account/test/2.m3u8","Noticias").withLiveIdentity("7","espn.mx")));}
  @Test public void accountAndCredentialPathsMustAgree(){Catalog.Item source=channel("1","ESPN 1080");assertNull(match(source,channel("2","ESPN 720","7","https://example.invalid/live/other/test/2.m3u8")));assertNull(match(source,channel("2","ESPN 720","7","https://example.invalid/live/account/other/2.m3u8")));}
  @Test public void differentOriginProtocolOrPortCannotMatch(){Catalog.Item source=channel("1","ESPN 1080");for(String origin:Arrays.asList("https://other.invalid","http://example.invalid","https://example.invalid:8443"))assertNull(match(source,channel("2","ESPN 720","7",origin+"/live/account/test/2.m3u8")));}
  @Test public void explicitDefaultPortStillMatches(){Catalog.Item target=channel("2","ESPN 720","7","https://example.invalid:443/live/account/test/2.m3u8");assertSame(target,match(channel("1","ESPN 1080"),target));}
  @Test public void contradictoryEpgIdentityIsRejected(){assertNull(match(channel("1","ESPN 1080"),channel("2","ESPN 720").withLiveIdentity("7","espn.us")));}
  @Test public void vagueOrMultipleQualityLabelsCannotInventAMatch(){assertNull(match(channel("1","ESPN FHD"),channel("2","ESPN 720")));assertNull(match(channel("1","ESPN 1080 720"),channel("2","ESPN 720")));assertNull(match(channel("1","ESPN 1080"),channel("2","ESPN HD")));}
  @Test public void malformedOrSignedUrlsAndVodAreNotEligible(){Catalog.Item source=channel("1","ESPN 1080");for(String url:Arrays.asList("not a url","https://example.invalid/live/account/test/2.m3u8?token=test","https://example.invalid/live/account/test/2.m3u8#fragment"))assertNull(match(source,channel("2","ESPN 720","7",url)));assertNull(match(source,new Catalog.Item("movie2","ESPN 720","TV en vivo",source.url,"Deportes").withLiveIdentity("7","espn.mx")));}
  @Test public void managedBridgeRequiresSameUpstreamIdentity(){Catalog.Item source=channel("1","ESPN 1080","7","https://api.snaptvnow.com/live/account/test/1.m3u8");Catalog.Item target=channel("2","ESPN 720","7","https://api.snaptvnow.com/live/account/test/2.m3u8");assertNull(match(source,target));source=source.withProfile(new ProfileReference("live","1","provider-a",""));assertNull(match(source,target));assertNull(match(source,target.withProfile(new ProfileReference("live","2","provider-b",""))));Catalog.Item same=target.withProfile(new ProfileReference("live","2","provider-a",""));assertSame(same,match(source,same));}
}

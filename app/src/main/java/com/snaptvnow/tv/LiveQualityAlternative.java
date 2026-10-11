package com.snaptvnow.tv;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A conservative catalog match, never a claim that the alternative is online or truly 720p. */
final class LiveQualityAlternative {
  private static final Pattern QUALITY=Pattern.compile("(?<![\\p{L}\\p{N}])(1080|720)\\s*[pP]?(?![\\p{L}\\p{N}])");
  private static final class Label {
    final String channel;final int quality;
    Label(String channel,int quality){this.channel=channel;this.quality=quality;}
  }
  private static Label label(String title){
    if(title==null||title.length()>512)return null;
    Matcher m=QUALITY.matcher(title);if(!m.find())return null;
    int quality=Integer.parseInt(m.group(1));int start=m.start(),end=m.end();if(m.find())return null;
    String channel=(title.substring(0,start)+" "+title.substring(end))
      .replaceAll("\\(\\s*\\)|\\[\\s*\\]|\\{\\s*\\}"," ")
      .replaceAll("\\s*([|·])\\s*","$1").replaceAll("^[\\s|·_-]+|[\\s|·_-]+$","")
      .replaceAll("\\s+"," ").trim().toUpperCase(Locale.ROOT);
    return channel.isEmpty()?null:new Label(channel,quality);
  }
  static Catalog.Item find720(Catalog.Item source,List<Catalog.Item> catalog){
    Label from=source==null?null:label(source.title);
    if(from==null||from.quality!=1080||!source.id.matches("live[0-9]+")||source.liveGroupId.isEmpty())return null;
    Catalog.Item match=null;
    for(Catalog.Item item:catalog){
      if(item==null||!item.id.matches("live[0-9]+")||item.id.equals(source.id))continue;
      Label to=label(item.title);
      if(to==null||to.quality!=720||!to.channel.equals(from.channel)
          ||!source.category.equals(item.category)||!source.liveGroupId.equals(item.liveGroupId)
          ||!source.description.equals(item.description)||!sameProvider(source,item))continue;
      if(!source.epgChannelId.isEmpty()&&!item.epgChannelId.isEmpty()&&!source.epgChannelId.equals(item.epgChannelId))continue;
      if(match!=null)return null; // Never choose arbitrarily among duplicate feeds.
      match=item;
    }
    return match;
  }
  private static boolean sameProvider(Catalog.Item a,Catalog.Item b){
    try {
      URI x=new URI(a.url),y=new URI(b.url);
      if(!valid(x)||!valid(y)||!x.getScheme().equalsIgnoreCase(y.getScheme())
          ||!x.getHost().equalsIgnoreCase(y.getHost())||port(x)!=port(y)
          ||!prefix(x).equals(prefix(y)))return false;
      if(a.profile!=null||b.profile!=null)return a.profile!=null&&b.profile!=null
          &&a.profile.type.equals("live")&&b.profile.type.equals("live")&&a.profile.server.equals(b.profile.server);
      // This bridge can aggregate multiple providers behind one hostname.
      return !x.getHost().equalsIgnoreCase("api.snaptvnow.com");
    }catch(Exception ignored){return false;}
  }
  private static boolean valid(URI u){
    return ("https".equalsIgnoreCase(u.getScheme())||"http".equalsIgnoreCase(u.getScheme()))
      &&u.getHost()!=null&&u.getRawUserInfo()==null&&u.getRawQuery()==null&&u.getRawFragment()==null
      &&u.getRawPath()!=null&&u.getRawPath().matches(".*/live/[^/]+/[^/]+/[0-9]+\\.m3u8");
  }
  private static int port(URI u){return u.getPort()!=-1?u.getPort():"https".equalsIgnoreCase(u.getScheme())?443:80;}
  private static String prefix(URI u){String p=u.getRawPath();return p.substring(0,p.lastIndexOf('/')+1);}
  private LiveQualityAlternative(){}
}

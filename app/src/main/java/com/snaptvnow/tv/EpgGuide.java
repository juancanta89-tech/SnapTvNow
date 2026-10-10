package com.snaptvnow.tv;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.text.DateFormat;
import java.util.Date;

final class EpgGuide {
 static String text(String raw){if(raw==null)return "";if(raw.length()>4000)raw=raw.substring(0,4000);if(!raw.matches("[A-Za-z0-9+/]+={0,2}")||raw.length()%4!=0)return raw;try{String value=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(android.util.Base64.decode(raw,android.util.Base64.DEFAULT))).toString();return value.matches("(?s).*[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f].*")?raw:value;}catch(Exception ignored){return raw;}}
 static String format(JSONArray rows,long now){if(rows==null)return "Guía no disponible";StringBuilder out=new StringBuilder();int count=0;DateFormat time=DateFormat.getTimeInstance(DateFormat.SHORT);for(int i=0;i<Math.min(rows.length(),100)&&count<24;i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;long start=row.optLong("start_timestamp",0)*1000,end=row.optLong("stop_timestamp",0)*1000;if(end>0&&end<=now)continue;String title=text(row.optString("title")).trim();if(title.isEmpty())continue;if(title.length()>200)title=title.substring(0,200);if(count++>0)out.append("\n\n");if(start>0){out.append(time.format(new Date(start)));if(end>start)out.append("–").append(time.format(new Date(end)));out.append(" · ");}if(start>0&&start<=now&&end>now)out.append("En directo · ");out.append(title);}return count==0?"Guía no disponible":out.toString();}
}

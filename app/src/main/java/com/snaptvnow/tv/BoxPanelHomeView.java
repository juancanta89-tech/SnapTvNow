package com.snaptvnow.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.function.Consumer;

/** Eight original, remote-friendly homes. Real artwork comes only from this app's catalog. */
final class BoxPanelHomeView extends LinearLayout {
 private final boolean wide,compact;
 private final String design;
 private final int accent,background;
 private final Consumer<String> navigate;
 private final Consumer<Catalog.Item> open;
 private final List<Catalog.Item> items;
 final ImageView advertisement;
 BoxPanelHomeView(Context context,boolean television,List<Catalog.Item> catalog,Consumer<String> go,Consumer<Catalog.Item> play){
  super(context);wide=television;items=catalog;navigate=go;open=play;design=BoxPanel.design();accent=BoxPanel.accent();background=BoxPanel.background();compact=BoxPanel.style().optString("density").equals("compact");
  setOrientation(VERTICAL);setContentDescription("Diseño "+design);setTag("boxpanel-home-"+design);
  switch(design){
   case "compact": portals(true);space(10);guide();break;
   case "cinema": addView(hero(220,"UNA SELECCIÓN PARA TI","Tu próxima gran historia",false));space(14);categoryStrip(false);break;
   case "aura": addView(hero(wide?250:300,"HECHO PARA TU MOMENTO","Historias que se quedan",true));space(12);categoryStrip(true);break;
   case "panorama": addView(hero(wide?300:320,"ABRE TU HORIZONTE","Un mundo por descubrir",true));space(10);categoryStrip(false);break;
   case "stadium": addView(hero(wide?200:230,"TU PASIÓN, EN PANTALLA","Vive cada momento",false));space(10);guide();space(12);portals(false);break;
   case "noir": addView(hero(wide?245:280,"LA SESIÓN COMIENZA","El arte de descubrir",true));space(15);categoryStrip(false);break;
   case "horizon":
    if(wide){LinearLayout row=new LinearLayout(context);row.addView(hero(275,"DESCUBRE OTRA MIRADA","Siempre hay algo más",true),new LayoutParams(0,dp(275),1.55f));LinearLayout side=new LinearLayout(context);side.setOrientation(VERTICAL);for(String section:new String[]{"TV en vivo","Películas","Series"}){TextView v=button(section.toUpperCase(java.util.Locale.ROOT),()->navigate.accept(section));LayoutParams lp=new LayoutParams(-1,0,1);lp.setMargins(dp(12),0,0,dp(8));side.addView(v,lp);}row.addView(side,new LayoutParams(0,dp(275),1));addView(row);}
    else{addView(hero(280,"DESCUBRE OTRA MIRADA","Siempre hay algo más",true));space(12);categoryStrip(false);}break;
   default:addView(hero(wide?235:260,"TU PRÓXIMA HISTORIA","Más allá de lo cotidiano",false));space(12);portals(false);
  }
  advertisement=new ImageView(context);advertisement.setScaleType(ImageView.ScaleType.FIT_CENTER);advertisement.setContentDescription("Imágenes de tu servicio");
  org.json.JSONObject config=BoxPanel.configuration();org.json.JSONArray slides=config==null?null:config.optJSONArray("slides");
  if(slides!=null&&slides.length()>0){space(16);addView(label("Novedades de tu servicio",16,true));space(8);addView(advertisement,new LayoutParams(-1,dp(wide?160:180)));}
  if(!items.isEmpty()){space(18);TextView label=label(design.equals("stadium")?"Explora tu programación":"De tu catálogo",wide?18:17,true);addView(label);space(10);posters();}
 }
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private void space(int n){addView(new View(getContext()),new LayoutParams(1,dp(compact?Math.max(5,n-4):n)));}
 private TextView label(String value,int size,boolean bold){TextView t=new TextView(getContext());t.setText(value);t.setTextColor(Color.WHITE);t.setTextSize(size);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
 private GradientDrawable surface(int first,int last,int radius){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{first,last});d.setCornerRadius(dp(radius));return d;}
 private TextView button(String value,Runnable callback){TextView t=label(value,14,true);t.setPadding(dp(12),dp(9),dp(12),dp(9));t.setGravity(Gravity.CENTER);t.setMinHeight(dp(48));t.setBackground(surface(0xff263548,background,12));t.setFocusable(true);t.setOnClickListener(v->callback.run());t.setOnFocusChangeListener((v,focus)->{t.setBackground(surface(focus?accent:0xff263548,background,12));t.setTextColor(Color.WHITE);t.setScaleX(focus?1.02f:1f);t.setScaleY(focus?1.02f:1f);});return t;}
 private FrameLayout hero(int height,String kicker,String headline,boolean immersive){
  FrameLayout frame=new FrameLayout(getContext());frame.setContentDescription(kicker);frame.setBackground(surface(background,accent,18));frame.setClipToOutline(true);frame.setLayoutParams(new LayoutParams(-1,dp(compact?height-20:height)));
  frame.addView(new Scene(getContext(),design,BoxPanel.style().optString("wallpaper","aurora")),new FrameLayout.LayoutParams(-1,-1));
  Catalog.Item selected=null;for(Catalog.Item item:items)if(!item.artwork.isEmpty()&&!item.id.startsWith("live")){selected=item;break;}
  if(selected!=null){ImageView art=new ImageView(getContext());art.setScaleType(ImageView.ScaleType.CENTER_CROP);frame.addView(art,new FrameLayout.LayoutParams(-1,-1));Artwork.into(art,selected.artwork);}
  View veil=new View(getContext());veil.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xf2080e18,0x1a080e18}));frame.addView(veil,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout copy=new LinearLayout(getContext());copy.setOrientation(VERTICAL);copy.setGravity(immersive?Gravity.BOTTOM:Gravity.CENTER_VERTICAL);copy.setPadding(dp(wide?24:18),dp(18),dp(18),dp(22));frame.addView(copy,new FrameLayout.LayoutParams(wide?dp(design.equals("horizon")?270:380):-1,-1));
  TextView eyebrow=label(kicker,10,true);eyebrow.setTextColor(accent);eyebrow.setLetterSpacing(.14f);copy.addView(eyebrow);TextView name=label(selected==null?headline:selected.title,wide?31:29,true);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);if(design.equals("noir"))name.setTypeface(Typeface.create("serif",Typeface.NORMAL));copy.addView(name);String message=BoxPanel.style().optString("welcome","");if(message.isEmpty())message="Tu entretenimiento, a tu manera.";TextView sub=label(message,13,false);sub.setMaxLines(2);copy.addView(sub);TextView explore=button(design.equals("stadium")?"◉ TV EN VIVO":"▶ EXPLORAR CONTENIDO",()->navigate.accept(design.equals("stadium")?"TV en vivo":"Películas"));LayoutParams lp=new LayoutParams(-2,dp(48));lp.topMargin=dp(12);copy.addView(explore,lp);
  return frame;
 }
 private void categoryStrip(boolean rounded){LinearLayout row=new LinearLayout(getContext());String[] names={"TV en vivo","Películas","Series"};for(String name:names){TextView v=button(name.toUpperCase(java.util.Locale.ROOT),()->navigate.accept(name));if(rounded)v.setBackground(surface(0xff533a56,background,25));LayoutParams lp=new LayoutParams(0,dp(52),1);lp.setMargins(0,0,dp(7),0);row.addView(v,lp);}addView(row);}
 private void portals(boolean large){
  LinearLayout row=new LinearLayout(getContext());String[] names={"TV en vivo","Películas","Series"};String[] icons={"◉","▶","▤"};
  for(int i=0;i<names.length;i++){
   final String section=names[i];FrameLayout portal=new FrameLayout(getContext());portal.setBackground(surface(background,accent,14));portal.setClipToOutline(true);portal.addView(new Scene(getContext(),new String[]{"stadium","cinema","horizon"}[i],BoxPanel.style().optString("wallpaper","aurora")),new FrameLayout.LayoutParams(-1,-1));TextView link=button(icons[i]+"\n"+section.toUpperCase(java.util.Locale.ROOT),()->navigate.accept(section));link.setGravity(Gravity.BOTTOM|Gravity.LEFT);link.setBackground(surface(0x19081320,0xf2081320,14));link.setTextSize(wide?17:12);portal.addView(link,new FrameLayout.LayoutParams(-1,-1));LayoutParams lp=new LayoutParams(0,dp(large?(wide?205:155):(wide?115:105)),1);lp.setMargins(0,0,dp(8),0);row.addView(portal,lp);
  }addView(row);
 }
 private void guide(){TextView t=button("◉ GUÍA DE TV   ·   Tu programación y tus favoritos  →",()->navigate.accept("TV en vivo"));t.setGravity(Gravity.CENTER_VERTICAL);t.setTextSize(wide?14:12);addView(t,new LayoutParams(-1,dp(52)));}
 private void posters(){
  HorizontalScrollView scroll=new HorizontalScrollView(getContext());scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(getContext());scroll.addView(row);boolean portrait=design.equals("noir")||design.equals("cinema")||design.equals("aura");int count=0;
  for(Catalog.Item item:items){if(count++>=8)break;FrameLayout tile=new FrameLayout(getContext());tile.setBackground(surface(background,0xff273c49,12));tile.setClipToOutline(true);tile.addView(new Scene(getContext(),design,BoxPanel.style().optString("wallpaper","aurora")),new FrameLayout.LayoutParams(-1,-1));if(!item.artwork.isEmpty()){ImageView art=new ImageView(getContext());art.setScaleType(ImageView.ScaleType.CENTER_CROP);tile.addView(art,new FrameLayout.LayoutParams(-1,-1));Artwork.into(art,item.artwork);}TextView name=button(item.title,()->open.accept(item));name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);name.setGravity(Gravity.BOTTOM);name.setBackground(surface(0x00000000,0xeb070d17,10));tile.addView(name,new FrameLayout.LayoutParams(-1,-1));tile.setContentDescription(item.title);LayoutParams lp=new LayoutParams(dp(portrait?125:190),dp(portrait?170:112));lp.setMargins(0,0,dp(10),0);row.addView(tile,lp);
  }addView(scroll,new LayoutParams(-1,-2));
 }
 /** Lightweight original vector backdrops, not third-party player assets. */
 static final class Scene extends View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final String design,mood;
  Scene(Context c,String d,String m){super(c);design=d;mood=m;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
  protected void onDraw(Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/960f,getHeight()/540f);int sky=mood.equals("sunset")?0xff48324d:mood.equals("ocean")?0xff153f5b:mood.equals("midnight")?0xff1b1b34:0xff17394a;int hill=mood.equals("sunset")?0xffc28b83:mood.equals("midnight")?0xff7d729e:0xff63a4a4;
   paint.setShader(new LinearGradient(0,0,960,540,sky,0xff081420,Shader.TileMode.CLAMP));c.drawRect(0,0,960,540,paint);paint.setShader(null);paint.setColor(0xffebd2ab);c.drawCircle(730,130,78,paint);paint.setColor(0xffe0eff2);for(int i=0;i<12;i++)c.drawCircle(320+(i*67)%620,30+(i*37)%160,i%3+1,paint);
   if(design.equals("stadium")){paint.setColor(0xff247464);c.drawOval(360,180,1060,630,paint);paint.setStyle(Paint.Style.STROKE);paint.setColor(0x99d7f5d8);paint.setStrokeWidth(3);c.drawOval(410,220,1020,570,paint);c.drawRect(500,280,930,510,paint);c.drawLine(715,280,715,510,paint);c.drawCircle(715,395,45,paint);paint.setStyle(Paint.Style.FILL);}
   else if(design.equals("aura")||design.equals("noir")){paint.setColor(hill);for(int i=0;i<7;i++){int x=270+i*100,y=130+(i*71)%200;c.drawRect(x,y,x+65,540,paint);paint.setColor(0xffecd5aa);c.drawRect(x+15,y+35,x+19,y+170,paint);paint.setColor(hill);}}
   else if(design.equals("cinema")){paint.setColor(hill);c.drawCircle(650,280,140,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(20);c.save();c.rotate(-24,650,280);c.drawOval(370,250,950,330,paint);c.restore();paint.setStyle(Paint.Style.FILL);}
   else{Path mountain=new Path();mountain.moveTo(0,440);mountain.lineTo(300,190);mountain.lineTo(420,310);mountain.lineTo(600,80);mountain.lineTo(960,430);mountain.lineTo(960,540);mountain.lineTo(0,540);mountain.close();paint.setColor(hill);c.drawPath(mountain,paint);Path front=new Path();front.moveTo(0,480);front.lineTo(400,350);front.lineTo(600,450);front.lineTo(820,270);front.lineTo(960,390);front.lineTo(960,540);front.lineTo(0,540);front.close();paint.setColor(sky);c.drawPath(front,paint);}
   paint.setShader(new LinearGradient(0,300,0,540,0x00081420,0xcc081420,Shader.TileMode.CLAMP));c.drawRect(0,0,960,540,paint);paint.setShader(null);c.restore();
  }
 }
}

package com.snaptvnow.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Build;
import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.AspectRatioFrameLayout;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
  private static final int NAVY=0xff071729, PANEL=0xff10283d, CYAN=0xff29dce8, WHITE=Color.WHITE, MUTED=0xffb5c9d3;
  private static final String[] SECTIONS={"Inicio","TV en vivo","PPV HOY","Películas","Series","Mi lista"};
  private static final String[] ICONS={"⌂","▣","★","▶","▤","♡"};
  private List<Catalog.Item> items=Catalog.demo();
  private XtreamClient client;
  private String loadedSection="";
  private XtreamClient.Group activeGroup;
  private String activeGroupSection="";
  private android.graphics.Bitmap adBitmap;
  private ImageView adView;
  private List<Advertisement.Slide> adSlides=new ArrayList<>();
  private int adIndex,adGeneration;
  private Runnable adNext;
  private TextView refreshButton;
  private boolean refreshing;
  private final Set<String> favorites=new HashSet<>();
  private final Map<String,Catalog.Item> seen=new LinkedHashMap<>();
  private final List<Catalog.Item> recentlyPlayed=new ArrayList<>();
  private SharedPreferences prefs;
  private LinearLayout root, body;
  private ExoPlayer video;
  private PlayerView playerView;
  private FrameLayout playerControls;
  private final android.os.Handler controlHandler=new android.os.Handler(android.os.Looper.getMainLooper());
  private Runnable hideControls;
  private Runnable advanceTimeout;
  private Runnable countdownTick;
  private Runnable recoveryTimeout;
  private boolean findingOnline;
  private int advanceAttempts,advanceLimit;
  private boolean channelHadSignal;
  private int recoveryAttempts;
  private String section="Inicio", query="";
  private Catalog.Item currentItem;
  private boolean logged,wide,playing;
  private long resumePosition=-1;
  private boolean resumePaused;
  private View initialFocus;
  private int d(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
  private GradientDrawable shape(int c,int radius){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(d(radius));return g;}
  private GradientDrawable gradient(int first,int second,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{first,second});g.setCornerRadius(d(radius));return g;}
  private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);t.setGravity(Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
  private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
  private void gap(LinearLayout l,int height){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,d(height)));}
  private TextView action(String label,Runnable task){TextView v=text(label,15,WHITE,true);v.setGravity(Gravity.CENTER);v.setPadding(d(12),d(7),d(12),d(7));v.setBackground(shape(PANEL,12));v.setFocusable(true);v.setOnClickListener(w->task.run());v.setOnFocusChangeListener((w,focus)->{v.setBackground(focus?gradient(CYAN,0xff10abce,12):shape(PANEL,12));v.setTextColor(focus?NAVY:WHITE);v.setScaleX(focus?1.025f:1f);v.setScaleY(focus?1.025f:1f);});return v;}
  private void title(String s){body.addView(text(s,wide?25:22,WHITE,true));gap(body,12);}
  @Override public void onCreate(Bundle b){
    super.onCreate(b);prefs=getSharedPreferences("demo",MODE_PRIVATE);
    TextView loading=text("SNAPTVNOW\nConectando…",23,CYAN,true);
    loading.setGravity(Gravity.CENTER);loading.setBackgroundColor(NAVY);setContentView(loading);
    new Thread(()->{
      List<String> servers=null;String error=null;XtreamClient restored=null;
      try{
        servers=AppConfig.load(this);
        restored=SessionStore.restore(this);
        if(restored!=null){
          try{restored=restored.reconnect(servers);AppConfig.rememberWorking(this,XtreamClient.SERVER);}
          catch(Exception ignored){XtreamClient.SERVER=AppConfig.lastWorking(this,servers);}
        }else XtreamClient.SERVER=servers.get(0);
      }catch(Exception e){error=e.getMessage();}
      final XtreamClient resolved=restored;final String problem=error;
      runOnUiThread(()->{
        client=resolved;logged=client!=null;
        if(logged)items=new ArrayList<>();
        for(Catalog.Item i:items)if(prefs.getBoolean("fav_"+i.id,false))favorites.add(i.id);
        render();
        UpdateChecker.check(this);
        if(client!=null)checkDirectMessage();
        loadAdvertisement();
        if(problem!=null)new AlertDialog.Builder(this).setTitle("Sin configuración")
          .setMessage(problem).setPositiveButton("Reintentar",(dialog,which)->recreate())
          .setNegativeButton("Demostración",null).show();
      });
    }).start();
  }
  private void render(){
    if(adNext!=null){controlHandler.removeCallbacks(adNext);adNext=null;}adView=null;
    findingOnline=false;releaseVideo();playing=false;wide=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().getDecorView().setSystemUiVisibility(0);
    root=col();root.setBackgroundColor(NAVY);
    if(Build.VERSION.SDK_INT>=30){
      getWindow().setDecorFitsSystemWindows(false);
      root.setOnApplyWindowInsetsListener((view,insets)->{
        android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        view.setPadding(safe.left,safe.top,safe.right,safe.bottom);return insets;
      });
    }
    setContentView(root);if(!logged){login();return;}initialFocus=null;
    if(wide){
      LinearLayout shell=new LinearLayout(this);root.addView(shell,new LinearLayout.LayoutParams(-1,-1));
      LinearLayout rail=col();rail.setPadding(d(14),d(22),d(12),d(12));rail.setBackgroundColor(0xff091d32);
      shell.addView(rail,new LinearLayout.LayoutParams(d(192),-1));brand(rail,24);gap(rail,18);
      ScrollView railScroll=new ScrollView(this);rail.addView(railScroll);LinearLayout railItems=col();railScroll.addView(railItems);navigation(railItems,false);
      LinearLayout main=col();main.setPadding(d(20),d(16),d(20),d(8));shell.addView(main,new LinearLayout.LayoutParams(0,-1,1));content(main);
    }else{
      LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(d(18),d(10),d(12),d(6));root.addView(header,new LinearLayout.LayoutParams(-1,d(59)));
      brand(header,22);View spacer=new View(this);header.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
      TextView find=action("⌕",()->{section="Buscar";render();});header.addView(find,new LinearLayout.LayoutParams(d(42),d(42)));
      TextView profile=action("◎",()->{section="Cuenta";render();});LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(d(42),d(42));pp.leftMargin=d(8);header.addView(profile,pp);
      LinearLayout main=col();main.setPadding(d(14),d(6),d(14),d(4));root.addView(main,new LinearLayout.LayoutParams(-1,0,1));content(main);
      mobileNavigation();
    }
    if(initialFocus!=null&&wide)initialFocus.requestFocus();
    if(section.equals("Inicio"))scheduleAdvertisement();
  }
  private void mobileNavigation(){
    LinearLayout bar=new LinearLayout(this);bar.setPadding(d(6),d(4),d(6),d(7));bar.setBackgroundColor(0xff071421);root.addView(bar,new LinearLayout.LayoutParams(-1,d(68)));
    String[] names={"Inicio","TV en vivo","Mi lista"};String[] icons={"⌂","▣","♡"};
    for(int n=0;n<names.length;n++){
      final String target=names[n];TextView button=action(icons[n]+"\n"+target,()->{section=target;render();});button.setTextSize(11);
      button.setBackground(target.equals(section)?gradient(0xff12546a,0xff0a253a,12):shape(0xff071421,12));
      button.setTextColor(target.equals(section)?CYAN:MUTED);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(d(2),0,d(2),0);bar.addView(button,lp);
    }
  }
  private void releaseVideo(){if(hideControls!=null)controlHandler.removeCallbacks(hideControls);if(advanceTimeout!=null){controlHandler.removeCallbacks(advanceTimeout);advanceTimeout=null;}if(countdownTick!=null){controlHandler.removeCallbacks(countdownTick);countdownTick=null;}if(recoveryTimeout!=null){controlHandler.removeCallbacks(recoveryTimeout);recoveryTimeout=null;}playerControls=null;if(playerView!=null){playerView.setPlayer(null);playerView=null;}if(video!=null){video.release();video=null;}}
  private void brand(LinearLayout holder,int size){TextView t=text("SNAPTVNOW",size,CYAN,true);t.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD_ITALIC));holder.addView(t);}
  private void navigation(LinearLayout holder,boolean horizontal){for(int i=0;i<SECTIONS.length;i++){String s=SECTIONS[i],label=ICONS[i]+"  "+s;TextView t=action(label,()->{section=s;render();});t.setTextSize(horizontal?13:14);t.setGravity(horizontal?Gravity.CENTER:Gravity.CENTER_VERTICAL);if(s.equals(section)){t.setBackground(gradient(0xff106580,0xff0b354e,12));t.setTextColor(CYAN);}LinearLayout.LayoutParams lp=horizontal?new LinearLayout.LayoutParams(d(105),d(46)):new LinearLayout.LayoutParams(-1,d(49));lp.setMargins(0,0,horizontal?d(5):0,horizontal?0:d(4));holder.addView(t,lp);if(s.equals(section))initialFocus=t;}}
  private void content(LinearLayout holder){
    if(wide){LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);holder.addView(top);
      TextView page=text(section,22,WHITE,true);top.addView(page,new LinearLayout.LayoutParams(0,d(40),1));
      TextView accountButton=action("◎",()->{section="Cuenta";render();});top.addView(accountButton,new LinearLayout.LayoutParams(d(48),d(40)));gap(holder,8);}
    ScrollView sc=new ScrollView(this);sc.setFillViewport(true);holder.addView(sc,new LinearLayout.LayoutParams(-1,0,1));body=col();sc.addView(body);
    switch(section){case "Inicio":home();break;case "Explorar":explore();break;case "PPV HOY":sports();break;
      case "Cuenta":account();break;
      case "Buscar":search();break;case "Mi lista":favoritesPage();break;
      default:if(client!=null&&!section.equals(loadedSection))categoryPicker();else catalog();}
  }
  private void login(){
    LinearLayout panel=col();panel.setPadding(d(24),d(28),d(24),d(24));root.addView(panel);
    FrameLayout splash=new FrameLayout(this);splash.setBackgroundResource(R.drawable.entertainment_hero);panel.addView(splash,new LinearLayout.LayoutParams(-1,d(wide?160:230)));
    LinearLayout heading=col();heading.setPadding(d(15),d(15),d(15),d(12));heading.setBackground(gradient(0xdd071729,0x55071729,12));splash.addView(heading,new FrameLayout.LayoutParams(-1,-1));
    brand(heading,wide?31:30);gap(heading,30);heading.addView(text("Acceso de clientes",24,WHITE,true));
    gap(panel,22);
    EditText user=new EditText(this);user.setSingleLine(true);user.setHint("Usuario de la línea");user.setTextColor(WHITE);user.setHintTextColor(MUTED);user.setBackground(shape(PANEL,12));user.setPadding(d(16),0,d(16),0);panel.addView(user,new LinearLayout.LayoutParams(wide?d(350):-1,d(55)));
    gap(panel,10);EditText pass=new EditText(this);pass.setSingleLine(true);pass.setInputType(129);pass.setHint("Contraseña de la línea");pass.setTextColor(WHITE);pass.setHintTextColor(MUTED);pass.setBackground(shape(PANEL,12));pass.setPadding(d(16),0,d(16),0);panel.addView(pass,new LinearLayout.LayoutParams(wide?d(350):-1,d(55)));
    gap(panel,15);TextView enter=action("Entrar",()->{
      String u=user.getText().toString().trim(),p=pass.getText().toString();if(u.isEmpty()||p.isEmpty()){Toast.makeText(this,"Escribe usuario y contraseña",Toast.LENGTH_SHORT).show();return;}
      enterLine(u,p);
    });enter.setBackground(gradient(CYAN,0xff0fb5cd,12));enter.setTextColor(NAVY);panel.addView(enter,new LinearLayout.LayoutParams(wide?d(350):-1,d(54)));
    gap(panel,14);TextView vpn=action("VPN",this::openVpn);panel.addView(vpn,new LinearLayout.LayoutParams(wide?d(350):-1,d(48)));
    gap(panel,9);panel.addView(text("Tu sesión se guarda cifrada en este dispositivo hasta que cierres sesión.",13,MUTED,false));
  }
  private void openVpn(){
    new Thread(()->{try{
      List<VpnLocations.Location> locations=VpnLocations.load();
      runOnUiThread(()->{
        if(isFinishing()||isDestroyed())return;
        if(locations.isEmpty()){new AlertDialog.Builder(this).setTitle("VPN").setMessage("No hay países VPN habilitados en el panel.").setPositiveButton("Aceptar",null).show();return;}
        String[] labels=new String[locations.size()];
        for(int i=0;i<locations.size();i++)labels[i]=locations.get(i).label();
        new AlertDialog.Builder(this).setTitle("Elegir VPN").setItems(labels,(dialog,which)->
          new AlertDialog.Builder(this).setTitle(locations.get(which).name)
            .setMessage("La conexión directa está en preparación. Aún no se puede activar este país hasta integrar y probar el motor OpenVPN.")
            .setPositiveButton("Aceptar",null).show())
          .setNegativeButton("Cerrar",null).show();
      });
    }catch(Exception error){runOnUiThread(()->{
      if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("VPN").setMessage(error.getMessage()).setPositiveButton("Aceptar",null).show();
    });}}).start();
  }
  private void enterLine(String u,String p){new Thread(()->{try{List<String> servers=AppConfig.load(this);XtreamClient connected=XtreamClient.loginAny(servers,u,p);AppConfig.rememberWorking(this,XtreamClient.SERVER);SessionStore.save(this,u,p,connected.expires,connected.maxConnections);runOnUiThread(()->{client=connected;items=new ArrayList<>();loadedSection="";activeGroup=null;activeGroupSection="";logged=true;section="Inicio";render();checkDirectMessage();loadAdvertisement();});}catch(Exception e){runOnUiThread(()->new AlertDialog.Builder(this).setTitle("No se pudo conectar").setMessage(e.getMessage()).setPositiveButton("Aceptar",null).show());}}).start();}
  private void checkDirectMessage(){
    final XtreamClient session=client;if(session==null)return;
    new Thread(()->{try{
      org.json.JSONObject message=DirectMessages.request(session,"list",0).optJSONObject("message");
      if(message==null)return;
      runOnUiThread(()->{if(client!=session||isFinishing()||isDestroyed())return;
        int id=message.optInt("id",0);if(id<=0)return;
        new AlertDialog.Builder(this).setTitle(message.optString("title","Mensaje directo"))
          .setMessage(message.optString("body",""))
          .setPositiveButton("OK",(dialog,which)->respondDirect(session,id,false))
          .setNegativeButton("Cancelar el servicio",(dialog,which)->
            new AlertDialog.Builder(this).setTitle("Cancelar el servicio")
              .setMessage("Se enviará tu solicitud al administrador y se cerrará la app. ¿Continuar?")
              .setNegativeButton("Volver",(d,w)->checkDirectMessage())
              .setPositiveButton("Confirmar",(d,w)->respondDirect(session,id,true)).show())
          .setCancelable(false).show();
      });
    }catch(Exception ignored){} }).start();
  }
  private void respondDirect(XtreamClient session,int id,boolean cancel){
    new Thread(()->{try{
      DirectMessages.request(session,cancel?"cancel":"ok",id);
      runOnUiThread(()->{if(cancel){SessionStore.clear(this);client=null;logged=false;finishAndRemoveTask();}else checkDirectMessage();});
    }catch(Exception e){runOnUiThread(()->new AlertDialog.Builder(this).setTitle("No se pudo registrar la respuesta")
      .setMessage("Revisa tu conexión y vuelve a intentarlo.")
      .setPositiveButton("Reintentar",(d,w)->respondDirect(session,id,cancel)).show());}}).start();
  }
  private void displayAdvertisement(android.graphics.Bitmap picture){
    adBitmap=picture;if(adView!=null)adView.setImageBitmap(picture);
  }
  private void scheduleAdvertisement(){
    if(adNext!=null)controlHandler.removeCallbacks(adNext);
    if(!section.equals("Inicio")||adSlides.size()<2)return;
    int generation=adGeneration;
    adNext=()->{
      if(generation!=adGeneration||!section.equals("Inicio"))return;
      adIndex=(adIndex+1)%adSlides.size();Advertisement.Slide slide=adSlides.get(adIndex);
      new Thread(()->{android.graphics.Bitmap picture=null;try{picture=Advertisement.image(slide);}catch(Exception ignored){}
        final android.graphics.Bitmap ready=picture;
        runOnUiThread(()->{if(generation!=adGeneration||!section.equals("Inicio"))return;
          if(ready!=null)displayAdvertisement(ready);scheduleAdvertisement();});}).start();
    };
    controlHandler.postDelayed(adNext,adSlides.get(adIndex).seconds*1000L);
  }
  @Override protected void onPause(){
    if(adNext!=null){controlHandler.removeCallbacks(adNext);adNext=null;}
    super.onPause();
  }
  @Override protected void onResume(){
    super.onResume();
    UpdateChecker.check(this);
    if(logged&&section.equals("Inicio"))scheduleAdvertisement();
  }
  private void loadAdvertisement(){
    int generation=++adGeneration;
    new Thread(()->{try{
      List<Advertisement.Slide> slides=Advertisement.playlist();
      android.graphics.Bitmap picture=slides.isEmpty()?null:Advertisement.image(slides.get(0));
      runOnUiThread(()->{if(generation!=adGeneration)return;adSlides=slides;adIndex=0;
        displayAdvertisement(picture);scheduleAdvertisement();});
    }catch(Exception ignored){} }).start();
  }
  private void refreshContent(){
    if(refreshing)return;
    if(client==null){loadAdvertisement();Toast.makeText(this,"Conecta una línea para actualizar contenido",Toast.LENGTH_LONG).show();return;}
    refreshing=true;if(refreshButton!=null)refreshButton.setText("Actualizando…");
    final XtreamClient.Group group=activeGroup;final String category=activeGroupSection;
    new Thread(()->{try{
      List<String> servers=AppConfig.load(this);XtreamClient connected=client.reconnect(servers);
      XtreamClient.Group matched=null;
      if(group!=null&&!category.isEmpty())for(XtreamClient.Group candidate:connected.categories(category))
        if(candidate.name.equalsIgnoreCase(group.name)){matched=candidate;break;}
      List<Catalog.Item> updated=matched!=null?connected.loadCategory(category,matched.id,matched.name):new ArrayList<>();
      AppConfig.rememberWorking(this,XtreamClient.SERVER);SessionStore.save(this,connected.username(),connected.password(),connected.expires,connected.maxConnections);
      final XtreamClient.Group newGroup=matched;
      runOnUiThread(()->{client=connected;items=updated;seen.clear();recentlyPlayed.clear();for(Catalog.Item item:updated)seen.put(item.id,item);
        activeGroup=newGroup;activeGroupSection=newGroup==null?"":category;loadedSection=newGroup==null?"":category;refreshing=false;render();loadAdvertisement();
        if(refreshButton!=null){refreshButton.setText("100% actualizado");TextView finished=refreshButton;controlHandler.postDelayed(()->{if(refreshButton==finished)finished.setText("Actualizar contenido");},2200);}
      });
    }catch(Exception e){runOnUiThread(()->{refreshing=false;if(refreshButton!=null)refreshButton.setText("Actualizar contenido");new AlertDialog.Builder(this).setTitle("No se pudo actualizar").setMessage(e.getMessage()).setPositiveButton("Aceptar",null).show();});}}).start();
  }
  private void hero(String headline,String sub,Runnable target){
    FrameLayout frame=new FrameLayout(this);frame.setBackgroundResource(R.drawable.entertainment_hero);frame.setClipToOutline(true);body.addView(frame,new LinearLayout.LayoutParams(-1,d(wide?230:260)));
    if(section.equals("Inicio")){
      FrameLayout imageArea=new FrameLayout(this);imageArea.setBackgroundColor(0xff091927);
      FrameLayout.LayoutParams adParams=new FrameLayout.LayoutParams(d(180),-1,Gravity.RIGHT);frame.addView(imageArea,adParams);
      adView=new ImageView(this);adView.setScaleType(ImageView.ScaleType.FIT_CENTER);
      imageArea.addView(adView,new FrameLayout.LayoutParams(-1,-1));displayAdvertisement(adBitmap);
      frame.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,oright,ob)->{
        FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)imageArea.getLayoutParams();int width=(r-l)/2;
        if(params.width!=width){params.width=width;imageArea.setLayoutParams(params);}
      });
    }
    LinearLayout overlay=col();overlay.setPadding(d(16),d(12),d(12),d(10));overlay.setBackground(gradient(0xdd061525,0x55061525,13));
    frame.addView(overlay,new FrameLayout.LayoutParams(section.equals("Inicio")?d(180):-1,-1,Gravity.LEFT));
    if(section.equals("Inicio"))frame.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,oright,ob)->{
      FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)overlay.getLayoutParams();int width=(r-l)/2;
      if(params.width!=width){params.width=width;overlay.setLayoutParams(params);}
    });
    overlay.addView(text(client==null?"SNAPTVNOW  ·  DEMO":"SNAPTVNOW  ·  TU ENTRETENIMIENTO",wide?11:12,CYAN,true));gap(overlay,8);
    overlay.addView(text(headline,wide?22:18,WHITE,true));gap(overlay,4);overlay.addView(text(sub,12,WHITE,false));gap(overlay,8);
    TextView button=action(section.equals("Inicio")?(refreshing?"Actualizando…":"Actualizar contenido"): "Ver canales  ›",target);if(section.equals("Inicio"))refreshButton=button;button.setBackground(gradient(CYAN,0xff12a9c6,12));button.setTextColor(NAVY);
    int availableWidth=getResources().getDisplayMetrics().widthPixels-(wide?d(192+40):d(28));
    int buttonWidth=section.equals("Inicio")?Math.max(d(105),Math.min(d(175),availableWidth/2-d(28))):d(165);
    button.setTextSize(buttonWidth<d(150)?11:13);
    overlay.addView(button,new LinearLayout.LayoutParams(buttonWidth,d(38)));
  }
  private void home(){
    hero("Todo tu entretenimiento","TV en vivo, películas y series en un solo lugar",this::refreshContent);gap(body,18);
    title("Elige qué ver");String[] names={"TV en vivo","Películas","Series"};String[] icons={"◉","▶","▤"};int[] colors={0xff0d6887,0xff71532c,0xff603e85};cards(names,icons,colors);
    if(!recentlyPlayed.isEmpty()){gap(body,18);title("Visto recientemente");catalogRow(recentlyPlayed.subList(0,Math.min(8,recentlyPlayed.size())));}
    if(!items.isEmpty()){gap(body,18);title(client==null?"Vista previa":"De tu última categoría");catalogRow(items.subList(0,Math.min(12,items.size())));}
    gap(body,12);body.addView(text(client==null?"Vista de demostración. Conecta una línea para consultar su catálogo.":"Explora las categorías de tu línea para ver canales y títulos disponibles.",13,MUTED,false));
  }
  private void explore(){
    title("Explorar");body.addView(text("TV en vivo, películas y series",14,MUTED,false));gap(body,14);
    TextView find=action("⌕  Buscar en la categoría cargada",()->{section="Buscar";render();});body.addView(find,new LinearLayout.LayoutParams(-1,d(48)));gap(body,18);
    String[] names={"TV en vivo","Películas","Series","PPV HOY"};String[] icons={"◉","▶","▤","★"};int[] colors={0xff0d6887,0xff71532c,0xff603e85,0xff155a59};cards(names,icons,colors);
    if(!recentlyPlayed.isEmpty()){gap(body,20);title("Visto recientemente");catalogRow(recentlyPlayed.subList(0,Math.min(8,recentlyPlayed.size())));}
    if(!items.isEmpty()){gap(body,20);title("Explorar contenido");catalogRow(items.subList(0,Math.min(20,items.size())));}
  }
  private void favoritesPage(){
    title("Mi lista");List<Catalog.Item> matches=new ArrayList<>();
    for(Catalog.Item item:seen.values())if(favorites.contains(item.id))matches.add(item);
    if(client==null)for(Catalog.Item item:items)if(favorites.contains(item.id)&&!matches.contains(item))matches.add(item);
    if(matches.isEmpty()){body.addView(text("Mantén pulsada una portada para añadirla a Mi lista.",15,MUTED,false));return;}
    catalogRow(matches);
  }
  private void cards(String[] names,String[] icons,int[] colors){
    if(wide){LinearLayout row=new LinearLayout(this);body.addView(row);for(int i=0;i<names.length;i++){final String name=names[i],icon=icons[i];final int color=colors[i];
      TextView tile=categoryTile(icon,name,color);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,d(165),1);lp.rightMargin=d(9);row.addView(tile,lp);}}
    else{for(int start=0;start<names.length;start+=2){LinearLayout row=new LinearLayout(this);body.addView(row);
      for(int i=start;i<Math.min(names.length,start+2);i++){TextView tile=categoryTile(icons[i],names[i],colors[i]);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,d(138),1);lp.setMargins(d(3),d(3),d(3),d(4));row.addView(tile,lp);}}}
  }
  private TextView categoryTile(String icon,String name,int color){
    TextView tile=action(icon+"\n"+name.toUpperCase(Locale.ROOT),()->{section=name;render();});tile.setGravity(Gravity.CENTER);tile.setTextSize(wide?19:17);
    tile.setBackground(gradient(color,PANEL,16));tile.setOnFocusChangeListener((v,f)->{tile.setBackground(f?gradient(CYAN,color,16):gradient(color,PANEL,16));tile.setTextColor(f?NAVY:WHITE);});return tile;
  }
  private void sports(){if(client!=null&&!section.equals(loadedSection)){categoryPicker();return;}hero("PPV HOY en SNAPTVNOW","Encuentra eventos disponibles en tu línea",()->{section="TV en vivo";render();});gap(body,20);title(client==null?"Vista previa de eventos":"Eventos PPV");List<Catalog.Item> sports=new ArrayList<>();for(Catalog.Item i:items)if(i.category.equals("PPV HOY")||client==null&&i.category.equals("Deportes"))sports.add(i);catalogGrid(sports);}
  private void guide(){title("Guía de programación");if(client==null){body.addView(text("Vista previa · Los horarios reales aparecerán si tu fuente incluye EPG.",15,MUTED,false));gap(body,20);guideTable();return;}body.addView(text("Selecciona un canal para consultar su EPG disponible.",15,MUTED,false));gap(body,14);int count=0;for(Catalog.Item i:items){if(!i.id.startsWith("live"))continue;TextView b=action("▣  "+i.title,()->loadEpg(i));body.addView(b,new LinearLayout.LayoutParams(-1,d(48)));gap(body,6);if(++count>=25)break;}if(count==0)body.addView(text("No hay canales para consultar.",15,MUTED,false));}
  private void loadEpg(Catalog.Item channel){new Thread(()->{try{String value=client.epg(channel);runOnUiThread(()->new AlertDialog.Builder(this).setTitle(channel.title).setMessage(value).setPositiveButton("Cerrar",null).show());}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Guía no disponible",Toast.LENGTH_LONG).show());}}).start();}
  private void guideTable(){body.addView(text("GUÍA DE PROGRAMACIÓN  ·  DEMO",17,CYAN,true));gap(body,9);for(int i=1;i<=3;i++){LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(d(8),d(4),d(8),d(4));line.setBackground(shape(PANEL,8));TextView channel=text("Canal "+(char)('A'+i-1),14,WHITE,true);line.addView(channel,new LinearLayout.LayoutParams(d(wide?130:90),d(44)));TextView program=text("Programación no disponible",13,MUTED,false);line.addView(program);body.addView(line);gap(body,6);}}
  private void categoryPicker(){
    title(section);body.addView(text("Cargando categorías del servidor…",15,MUTED,false));
    final String requested=section;
    final LinearLayout target=body;
    new Thread(()->{try{
      List<XtreamClient.Group> groups=client.categories(requested);
      runOnUiThread(()->{
        if(!section.equals(requested)||body!=target)return;
        body.removeAllViews();title(requested);
        List<XtreamClient.Group> visible=new ArrayList<>();
        for(XtreamClient.Group group:groups){
          if(requested.equals("PPV HOY")&&!group.name.toLowerCase(Locale.ROOT).matches(".*(ppv|evento|event|pay.per.view|ufc|boxeo).*"))continue;
          visible.add(group);
        }
        if(visible.isEmpty())body.addView(text("No hay categorías disponibles.",16,MUTED,false));
        else categoryGrid(visible,requested);
      });
    }catch(Exception e){runOnUiThread(()->{
      if(section.equals(requested)&&body==target){body.removeAllViews();title(requested);body.addView(text("No se pudieron cargar categorías: "+e.getMessage(),15,MUTED,false));}
    });}}).start();
  }
  private void categoryGrid(List<XtreamClient.Group> groups,String requested){
    int columns=wide?5:2;
    for(int start=0;start<groups.size();start+=columns){
      LinearLayout row=new LinearLayout(this);body.addView(row);
      for(int column=0;column<columns;column++){
        int index=start+column;
        if(index<groups.size()){
          XtreamClient.Group group=groups.get(index);
          TextView button=action("▣\n"+group.name,()->loadGroup(requested,group));
          button.setTextSize(wide?13:14);
          button.setMaxLines(3);
          button.setEllipsize(TextUtils.TruncateAt.END);
          LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,d(wide?96:90),1);
          lp.setMargins(d(3),d(3),d(3),d(6));row.addView(button,lp);
        }else row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
      }
    }
  }
  private void loadGroup(String requested,XtreamClient.Group group){body.removeAllViews();title(group.name);body.addView(text("Cargando contenido…",15,MUTED,false));new Thread(()->{try{List<Catalog.Item> loaded=client.loadCategory(requested,group.id,group.name);runOnUiThread(()->{if(!section.equals(requested))return;items=loaded;for(Catalog.Item item:loaded){seen.put(item.id,item);if(prefs.getBoolean("fav_"+item.id,false))favorites.add(item.id);}loadedSection=requested;activeGroup=group;activeGroupSection=requested;render();});}catch(Exception e){runOnUiThread(()->new AlertDialog.Builder(this).setTitle("No se pudo cargar la categoría").setMessage(e.getMessage()).setPositiveButton("Aceptar",null).show());}}).start();}
  private void catalog(){title(section);if(client!=null&&!section.equals("Mi lista")){TextView choose=action("Cambiar categoría",()->{loadedSection="";render();});body.addView(choose,new LinearLayout.LayoutParams(d(190),d(44)));gap(body,12);}List<Catalog.Item> matches=new ArrayList<>();for(Catalog.Item i:items)if(i.category.equals(section)||section.equals("TV en vivo")&&(i.category.equals("Deportes")||i.category.equals("PPV HOY"))||section.equals("Mi lista")&&favorites.contains(i.id))matches.add(i);if(matches.isEmpty()){body.addView(text("Aún no hay títulos en esta sección.",16,MUTED,false));return;}body.addView(text(client==null?"Contenido de demostración · selecciona una tarjeta":"Contenido de tu línea · selecciona una tarjeta",14,MUTED,false));gap(body,12);if(section.equals("TV en vivo")||section.equals("PPV HOY"))catalogGrid(matches);else catalogRow(matches);}
  private void catalogGrid(List<Catalog.Item> list){
    int columns=wide?3:2;int[] shades={0xff134e68,0xff554681,0xff78532f,0xff225d62};
    for(int start=0;start<list.size();start+=columns){
      LinearLayout row=new LinearLayout(this);body.addView(row);
      for(int column=0;column<columns;column++){
        int index=start+column;
        if(index<list.size()){
          Catalog.Item item=list.get(index);
          FrameLayout card=poster(item,shades[index%shades.length],true);
          LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,d(wide?164:158),1);
          lp.setMargins(d(3),d(2),d(3),d(8));row.addView(card,lp);
        }else row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
      }
    }
  }
  private void catalogRow(List<Catalog.Item> list){
    int[] shades={0xff134e68,0xff554681,0xff78532f,0xff225d62};
    if(wide){
      HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);body.addView(scroll);
      LinearLayout row=new LinearLayout(this);scroll.addView(row);
      for(int n=0;n<Math.min(list.size(),60);n++){
        Catalog.Item item=list.get(n);boolean live=item.category.equals("TV en vivo")||item.category.equals("Deportes")||item.category.equals("PPV HOY");
        FrameLayout card=poster(item,shades[n%shades.length],live);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(d(live?196:146),d(live?162:206));lp.rightMargin=d(10);row.addView(card,lp);
      }
    }else{
      // A vertical grid leaves every title reachable with the normal page scroll.
      for(int start=0;start<Math.min(list.size(),60);start+=2){
        LinearLayout row=new LinearLayout(this);body.addView(row);
        for(int n=start;n<Math.min(start+2,Math.min(list.size(),60));n++){
          Catalog.Item item=list.get(n);boolean live=item.category.equals("TV en vivo")||item.category.equals("Deportes")||item.category.equals("PPV HOY");
          FrameLayout card=poster(item,shades[n%shades.length],live);
          LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,d(live?158:207),1);lp.setMargins(d(3),0,d(3),d(7));row.addView(card,lp);
        }
      }
    }
  }
  private FrameLayout poster(Catalog.Item item,int shade,boolean live){
      FrameLayout card=new FrameLayout(this);card.setBackground(gradient(shade,PANEL,12));card.setClipToOutline(true);
      ImageView art=new ImageView(this);art.setScaleType(ImageView.ScaleType.CENTER_CROP);card.addView(art,new FrameLayout.LayoutParams(-1,-1));Artwork.into(art,item.artwork);
      LinearLayout caption=col();caption.setPadding(d(9),d(6),d(8),d(8));caption.setBackground(gradient(0x66071729,0xee071729,9));
      FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);card.addView(caption,cp);
      TextView type=text(live?"●  EN VIVO":item.category.toUpperCase(Locale.ROOT),10,CYAN,true);caption.addView(type);
      TextView name=text(item.title,14,WHITE,true);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.addView(name);
      TextView save=action(favorites.contains(item.id)?"♥":"♡",()->{toggleFavorite(item);render();});save.setTextSize(20);
      FrameLayout.LayoutParams saveParams=new FrameLayout.LayoutParams(d(42),d(40),Gravity.TOP|Gravity.RIGHT);saveParams.setMargins(0,d(5),d(5),0);card.addView(save,saveParams);
      card.setFocusable(true);card.setOnClickListener(v->openItem(item));card.setOnLongClickListener(v->{detail(item);return true;});
      card.setOnFocusChangeListener((v,focus)->{card.setBackground(focus?gradient(CYAN,shade,12):gradient(shade,PANEL,12));card.setScaleX(focus?1.04f:1f);card.setScaleY(focus?1.04f:1f);});
      return card;
  }
  private int nIndex(Catalog.Item item){return Math.max(0,items.indexOf(item));}
  private String[] episodeNames(List<Catalog.Item> episodes){String[] names=new String[episodes.size()];for(int k=0;k<names.length;k++)names[k]=episodes.get(k).title;return names;}
  private void openItem(Catalog.Item i){if(client!=null&&i.id.startsWith("series")){new Thread(()->{try{List<Catalog.Item> episodes=client.episodes(i);runOnUiThread(()->{if(episodes.isEmpty()){Toast.makeText(this,"Sin episodios disponibles",Toast.LENGTH_LONG).show();return;}new AlertDialog.Builder(this).setTitle(i.title).setItems(episodeNames(episodes),(dialog,index)->play(episodes.get(index))).show();});}catch(Exception ex){runOnUiThread(()->Toast.makeText(this,"No se pudieron cargar episodios",Toast.LENGTH_LONG).show());}}).start();}else play(i);}
  private void detail(Catalog.Item i){new AlertDialog.Builder(this).setTitle(i.title).setMessage(i.description+(client==null?"\n\nContenido de demostración.":"\n\nContenido de tu línea.")).setPositiveButton("Reproducir",(a,b)->openItem(i)).setNeutralButton(favorites.contains(i.id)?"Quitar favorito":"Añadir favorito",(a,b)->{if(!favorites.add(i.id))favorites.remove(i.id);prefs.edit().putBoolean("fav_"+i.id,favorites.contains(i.id)).apply();render();}).setNegativeButton("Cerrar",null).show();}
  private void search(){title("Buscar contenido");EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Buscar en la categoría cargada");input.setText(query);input.setTextColor(WHITE);input.setHintTextColor(MUTED);body.addView(input);gap(body,7);body.addView(action("Buscar",()->{query=input.getText().toString().trim();render();}),new LinearLayout.LayoutParams(d(150),d(46)));gap(body,18);List<Catalog.Item> matches=new ArrayList<>();for(Catalog.Item i:items)if(query.isEmpty()||i.title.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))matches.add(i);if(matches.isEmpty())body.addView(text("Sin resultados.",15,MUTED,false));else catalogRow(matches);}
  private String formatExpiry(String value){try{long unix=Long.parseLong(value);if(unix<=0)return "Sin fecha";return new java.text.SimpleDateFormat("dd/MM/yyyy",Locale.getDefault()).format(new java.util.Date(unix*1000));}catch(Exception e){return "No disponible";}}
  private void account(){title("Cuenta y configuración");body.addView(text(client==null?"Sesión de demostración":"Línea activa",18,WHITE,true));gap(body,10);body.addView(text(client==null?"La fecha de vencimiento y el límite de dispositivos se mostrarán al conectar una línea.":"Vencimiento: "+formatExpiry(client.expires)+"  ·  Conexiones: "+client.maxConnections,15,MUTED,false));gap(body,20);if(client!=null){body.addView(text("Seguridad: el proveedor utiliza HTTP sin cifrado; las credenciales pueden ser visibles en la red.",13,MUTED,false));gap(body,20);}body.addView(action("Cerrar sesión",()->{SessionStore.clear(this);logged=false;client=null;items=Catalog.demo();loadedSection="";activeGroup=null;activeGroupSection="";recentlyPlayed.clear();seen.clear();render();}),new LinearLayout.LayoutParams(d(185),d(52)));}
  private void devices(){title("Dispositivos compatibles");body.addView(text("Amazon Fire TV · Firestick · Android TV · TV Box · Celular Android",18,WHITE,false));gap(body,12);body.addView(text("Usa el control remoto en TV o toca las tarjetas en tu celular.",15,MUTED,false));}
  private Catalog.Item nextChannel(Catalog.Item current){
    int start=-1;for(int i=0;i<items.size();i++)if(items.get(i).id.equals(current.id)){start=i;break;}
    if(start<0)return null;
    for(int offset=1;offset<items.size();offset++){
      Catalog.Item candidate=items.get((start+offset)%items.size());
      if(candidate.id.startsWith("live")&&candidate.category.equals(current.category)
        &&candidate.description.equals(current.description)&&candidate.url!=null&&!candidate.url.isEmpty())return candidate;
    }
    return null;
  }
  private void findNextOnline(Catalog.Item from){
    int count=0;for(Catalog.Item candidate:items)if(candidate.id.startsWith("live")
      &&candidate.category.equals(from.category)&&candidate.description.equals(from.description)
      &&candidate.url!=null&&!candidate.url.isEmpty()&&!candidate.id.equals(from.id))count++;
    advanceLimit=count;advanceAttempts=0;findingOnline=count>0;
    if(findingOnline)tryNextOnline(from);
  }
  private void tryNextOnline(Catalog.Item from){
    if(!findingOnline||advanceAttempts>=advanceLimit){findingOnline=false;unavailableCurrent("No hay más canales disponibles en esta lista");return;}
    Catalog.Item candidate=nextChannel(from);
    if(candidate==null){findingOnline=false;unavailableCurrent("No hay más canales disponibles en esta lista");return;}
    advanceAttempts++;play(candidate);
  }
  private void unavailableCurrent(String status){
    View view=getWindow().getDecorView().findViewWithTag("player_stage");
    if(view instanceof FrameLayout&&currentItem!=null)unavailable((FrameLayout)view,currentItem,status);
  }
  private void unavailable(FrameLayout stage,Catalog.Item item,String status){
    if(stage.findViewWithTag("unavailable")!=null)return;
    if(playerControls!=null)playerControls.setVisibility(View.GONE);
    if(hideControls!=null)controlHandler.removeCallbacks(hideControls);
    boolean live=item.id.startsWith("live");Catalog.Item next=live&&status==null?nextChannel(item):null;
    FrameLayout cover=new FrameLayout(this);cover.setTag("unavailable");cover.setBackgroundColor(NAVY);
    stage.addView(cover,new FrameLayout.LayoutParams(-1,-1));
    ImageView artwork=new ImageView(this);artwork.setImageResource(R.drawable.entertainment_hero);
    artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);artwork.setAlpha(0.48f);
    cover.addView(artwork,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout panel=col();panel.setGravity(Gravity.CENTER);panel.setPadding(d(24),d(16),d(24),d(16));
    panel.setBackgroundColor(0xcc071729);cover.addView(panel,new FrameLayout.LayoutParams(-1,-1));
    TextView brand=text("SNAPTVNOW",wide?26:24,CYAN,true);brand.setGravity(Gravity.CENTER);panel.addView(brand);
    gap(panel,18);
    TextView message=text(live?"Canal no disponible":"Contenido no disponible",wide?27:23,WHITE,true);
    message.setGravity(Gravity.CENTER);panel.addView(message);
    gap(panel,8);
    TextView suggestion=text(status!=null?status:next!=null?"Conectando al siguiente canal disponible":"Inténtalo más tarde",wide?19:17,WHITE,false);
    suggestion.setGravity(Gravity.CENTER);panel.addView(suggestion);
    gap(panel,23);
    if(next!=null){
      TextView counter=text("3",wide?42:38,CYAN,true);counter.setGravity(Gravity.CENTER);
      panel.addView(counter,new LinearLayout.LayoutParams(d(220),d(62)));gap(panel,10);
      final int[] remaining={3};
      countdownTick=new Runnable(){@Override public void run(){
        if(!playing||currentItem!=item||stage.findViewWithTag("unavailable")!=cover)return;
        remaining[0]--;
        if(remaining[0]>0){counter.setText(String.valueOf(remaining[0]));controlHandler.postDelayed(this,1000);}
        else{countdownTick=null;findNextOnline(item);}
      }};
      controlHandler.postDelayed(countdownTick,1000);
    }
    TextView back=action(live?"Volver a canales":"Volver",this::render);
    panel.addView(back,new LinearLayout.LayoutParams(d(220),d(48)));back.requestFocus();
  }
  private void showPlayerControls(){
    if(playerControls==null)return;playerControls.setVisibility(View.VISIBLE);
    if(hideControls!=null)controlHandler.removeCallbacks(hideControls);
    hideControls=()->{if(playerControls!=null)playerControls.setVisibility(View.GONE);};controlHandler.postDelayed(hideControls,5000);
  }
  private void toggleFavorite(Catalog.Item item){
    if(!favorites.add(item.id))favorites.remove(item.id);
    prefs.edit().putBoolean("fav_"+item.id,favorites.contains(item.id)).apply();
    Toast.makeText(this,favorites.contains(item.id)?"Añadido a Mi lista":"Quitado de Mi lista",Toast.LENGTH_SHORT).show();
  }
  private void play(Catalog.Item item){
    if(item.url==null||item.url.isEmpty()){Toast.makeText(this,"Este título no tiene enlace de reproducción",Toast.LENGTH_LONG).show();return;}
    if(!playing||currentItem!=item){resumePosition=-1;resumePaused=false;channelHadSignal=false;recoveryAttempts=0;}
    currentItem=item;seen.put(item.id,item);recentlyPlayed.remove(item);recentlyPlayed.add(0,item);releaseVideo();playing=true;
    wide=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    FrameLayout stage=new FrameLayout(this);stage.setTag("player_stage");stage.setBackgroundColor(Color.BLACK);setContentView(stage);
    playerView=new PlayerView(this);playerView.setUseController(false);stage.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
    video=new ExoPlayer.Builder(this).build();playerView.setPlayer(video);
    video.addListener(new Player.Listener(){@Override public void onPlaybackStateChanged(int state){
      if(state!=Player.STATE_READY||currentItem!=item)return;
      channelHadSignal=true;
      if(advanceTimeout!=null){controlHandler.removeCallbacks(advanceTimeout);advanceTimeout=null;}
      if(recoveryTimeout!=null){controlHandler.removeCallbacks(recoveryTimeout);recoveryTimeout=null;}
      if(findingOnline){findingOnline=false;View searching=stage.findViewWithTag("searching");if(searching!=null)stage.removeView(searching);}
      if(resumePosition>=0&&video!=null){video.seekTo(resumePosition);resumePosition=-1;if(resumePaused)video.pause();resumePaused=false;}
    }@Override public void onPlayerError(PlaybackException error){
      if(!playing||currentItem!=item)return;
      if(findingOnline)controlHandler.post(()->{if(playing&&findingOnline&&currentItem==item)tryNextOnline(item);});
      else if(item.id.startsWith("live")&&channelHadSignal&&recoveryAttempts<3){
        recoveryAttempts++;
        Toast.makeText(MainActivity.this,"Reconectando este canal…",Toast.LENGTH_SHORT).show();
        recoveryTimeout=()->{if(playing&&currentItem==item&&video!=null){video.seekToDefaultPosition();video.prepare();video.play();}};
        controlHandler.postDelayed(recoveryTimeout,4000);
      }
      else unavailable(stage,item,null);
    }});
    video.setMediaItem(MediaItem.fromUri(item.url));video.prepare();video.play();
    View tapLayer=new View(this);stage.addView(tapLayer,new FrameLayout.LayoutParams(-1,-1));tapLayer.setOnClickListener(v->{if(playerControls!=null&&playerControls.getVisibility()==View.VISIBLE)playerControls.setVisibility(View.GONE);else showPlayerControls();});
    playerControls=new FrameLayout(this);stage.addView(playerControls,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(d(12),d(10),d(12),d(10));top.setBackground(gradient(0xe6071729,0x99071729,0));
    playerControls.addView(top,new FrameLayout.LayoutParams(-1,d(65),Gravity.TOP));
    TextView back=action("←",this::render);top.addView(back,new LinearLayout.LayoutParams(d(49),d(45)));
    TextView label=text("SNAPTVNOW  ·  "+item.title,wide?18:15,WHITE,true);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(0,-1,1);labelParams.leftMargin=d(10);top.addView(label,labelParams);
    TextView fav=action("♡",()->{toggleFavorite(item);showPlayerControls();});top.addView(fav,new LinearLayout.LayoutParams(d(48),d(45)));
    LinearLayout bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER);bottom.setPadding(d(8),d(8),d(8),d(8));bottom.setBackground(gradient(0x33071729,0xc6071729,0));
    playerControls.addView(bottom,new FrameLayout.LayoutParams(-1,d(76),Gravity.BOTTOM));
    TextView pause=action("❚❚",()->{});pause.setOnClickListener(v->{if(video==null)return;if(video.isPlaying()){video.pause();pauseLabel(pause,false);}else{video.play();pauseLabel(pause,true);}showPlayerControls();});bottom.addView(pause,new LinearLayout.LayoutParams(d(54),d(48)));
    TextView guideButton=action("Guía",()->{if(client!=null&&item.id.startsWith("live"))loadEpg(item);else Toast.makeText(this,"Guía no disponible para este título",Toast.LENGTH_SHORT).show();showPlayerControls();});bottom.addView(guideButton,new LinearLayout.LayoutParams(0,d(48),1));
    TextView channelList=action("Canales",()->{render();});bottom.addView(channelList,new LinearLayout.LayoutParams(0,d(48),1));
    TextView aspect=action("⛶",()->{if(playerView==null)return;int mode=playerView.getResizeMode();playerView.setResizeMode(mode==AspectRatioFrameLayout.RESIZE_MODE_FIT?AspectRatioFrameLayout.RESIZE_MODE_ZOOM:AspectRatioFrameLayout.RESIZE_MODE_FIT);showPlayerControls();});bottom.addView(aspect,new LinearLayout.LayoutParams(d(51),d(48)));
    if(!item.id.startsWith("live")){
      SeekBar seek=new SeekBar(this);FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,d(36),Gravity.BOTTOM);sp.bottomMargin=d(80);playerControls.addView(seek,sp);
      seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){showPlayerControls();}public void onStopTrackingTouch(SeekBar bar){if(video!=null&&video.getDuration()>0)video.seekTo(video.getDuration()*bar.getProgress()/1000);}public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){}});
      Runnable updater=new Runnable(){public void run(){if(video!=null&&playing&&video.getDuration()>0){seek.setProgress((int)(video.getCurrentPosition()*1000/video.getDuration()));seek.postDelayed(this,800);}}};seek.post(updater);
    }
    if(findingOnline){
      TextView searching=text("Buscando el siguiente canal disponible…",wide?20:17,WHITE,true);
      searching.setTag("searching");searching.setGravity(Gravity.CENTER);searching.setBackgroundColor(0xb0071729);
      stage.addView(searching,new FrameLayout.LayoutParams(-1,d(62),Gravity.CENTER));
      advanceTimeout=()->{if(playing&&findingOnline&&currentItem==item)tryNextOnline(item);};
      controlHandler.postDelayed(advanceTimeout,5000);
    }
    back.requestFocus();showPlayerControls();
  }
  private void pauseLabel(TextView button,boolean playingNow){button.setText(playingNow?"❚❚":"▶");}
  @Override public boolean onKeyDown(int code,KeyEvent event){if(playing&&(code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN)){showPlayerControls();}return super.onKeyDown(code,event);}
  private void rememberPlayback(){if(playing&&video!=null){resumePosition=Math.max(0,video.getCurrentPosition());resumePaused=!video.getPlayWhenReady();}}
  @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);if(playing&&currentItem!=null){rememberPlayback();play(currentItem);}else render();}
  @Override protected void onStart(){super.onStart();if(playing&&video==null&&currentItem!=null)play(currentItem);}
  @Override protected void onStop(){rememberPlayback();releaseVideo();super.onStop();}
  @Override public void onBackPressed(){if(playing){render();return;}if(logged&&!section.equals("Inicio")){section="Inicio";render();return;}super.onBackPressed();}
}

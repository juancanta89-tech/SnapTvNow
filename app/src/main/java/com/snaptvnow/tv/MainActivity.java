package com.snaptvnow.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Build;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.net.VpnService;
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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.C;
import androidx.media3.common.TrackSelectionParameters;
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
  private static final String[] SECTIONS={"Inicio","TV en vivo","PPV HOY","Películas","Series","Continuar viendo","Mi lista"};
  private static final String[] ICONS={"⌂","▣","★","▶","▤","↻","♡"};
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
  private PlaybackHistory playbackHistory;
  private VodPlaybackSession vodSession;
  private EpisodePlaybackController episodeController;
  private EpisodeEndView episodeEndView;
  private TextView nextEpisodeButton, automaticEpisodeButton;
  private List<Catalog.Item> episodeQueue=new ArrayList<>();
  private String episodeQueueSeries="";
  private XtreamClient episodeQueueClient;
  private int episodeLoadGeneration;
  private boolean resumeEpisodeEnded;
  private LinearLayout root, body;
  private ExoPlayer video;
  private PlayerView playerView;
  private FrameLayout playerControls;
  private final android.os.Handler controlHandler=new android.os.Handler(android.os.Looper.getMainLooper());
  private Runnable hideControls;
  private Runnable advanceTimeout;
  private Runnable countdownTick;
  private LivePlaybackRecovery liveRecovery;
  private LivePlaybackRecovery.RetryState liveRetries=new LivePlaybackRecovery.RetryState();
  private boolean findingOnline;
  private int advanceAttempts,advanceLimit;

  private String section="Inicio", query="";
  private Catalog.Item currentItem;
  private boolean logged,wide,playing;
  private static final int VPN_PERMISSION_REQUEST=1407;
  private EditText loginUser,loginPass;
  private VpnLocations.Config pendingVpn;
  private long resumePosition=-1;
  private boolean resumePaused;
  private View initialFocus;
  private CatalogSearch.Scope searchScope=CatalogSearch.Scope.MOVIES;
  private String searchOrigin="Inicio";
  private CatalogSearch searchIndex;
  private XtreamClient searchClient;
  private volatile int searchGeneration;
  private int searchScreenGeneration;
  private Runnable searchDebounce;
  private SearchInput searchInput;
  private PlaybackHistory.Kind continueKind=PlaybackHistory.Kind.ALL;
  private String continueFocusId="";
  private final java.util.concurrent.ExecutorService searchExecutor=java.util.concurrent.Executors.newSingleThreadExecutor();
  private AlertDialog trackDialog;
  private TrackSelectionParameters resumeTrackParameters;
  private boolean television(){return (getResources().getConfiguration().uiMode&Configuration.UI_MODE_TYPE_MASK)==Configuration.UI_MODE_TYPE_TELEVISION||getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK);}
  private int catalogWindowWidth(){int width=getResources().getConfiguration().screenWidthDp;return width>0?width:Math.round(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);}
  private int d(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
  private GradientDrawable shape(int c,int radius){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(d(radius));return g;}
  private GradientDrawable gradient(int first,int second,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{first,second});g.setCornerRadius(d(radius));return g;}
  private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);t.setGravity(Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
  private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
  private void gap(LinearLayout l,int height){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,d(height)));}
  private TextView action(String label,Runnable task){TextView v=text(label,15,WHITE,true);v.setGravity(Gravity.CENTER);v.setPadding(d(12),d(7),d(12),d(7));v.setBackground(shape(PANEL,12));v.setFocusable(true);v.setOnClickListener(w->task.run());v.setOnFocusChangeListener((w,focus)->{v.setBackground(focus?gradient(CYAN,0xff10abce,12):shape(PANEL,12));v.setTextColor(focus?NAVY:WHITE);v.setScaleX(focus?1.025f:1f);v.setScaleY(focus?1.025f:1f);});return v;}
  private void title(String s){body.addView(text(s,wide?25:22,WHITE,true));gap(body,12);}
  @Override public void onCreate(Bundle b){
    super.onCreate(b);CrashDiagnostics.install(this);prefs=getSharedPreferences("demo",MODE_PRIVATE);playbackHistory=new PlaybackHistory(this);VpnTunnel.initialize(this);
    final String previousCrash=CrashDiagnostics.consume(this);
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
        if(previousCrash!=null)new AlertDialog.Builder(this).setTitle("Diagnóstico VPN")
          .setMessage(previousCrash).setPositiveButton("Copiar",(dialog,which)->{
            ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico VPN",previousCrash));
            Toast.makeText(this,"Diagnóstico copiado",Toast.LENGTH_SHORT).show();
          }).setNegativeButton("Cerrar",null).show();
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
    if(searchInput!=null){searchInput.leaveEditor();searchInput=null;}
    searchGeneration++;searchScreenGeneration++;
    if(searchDebounce!=null){controlHandler.removeCallbacks(searchDebounce);searchDebounce=null;}
    if(adNext!=null){controlHandler.removeCallbacks(adNext);adNext=null;}adView=null;
    findingOnline=false;releaseVideo();playing=false;wide=television()||(!logged&&getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE);
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
      TextView find=action("⌕",this::openSearch);header.addView(find,new LinearLayout.LayoutParams(d(42),d(42)));
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
  private void releaseVideo(){episodeLoadGeneration++;if(episodeController!=null){episodeController.close();episodeController=null;}episodeEndView=null;nextEpisodeButton=null;automaticEpisodeButton=null;if(trackDialog!=null){trackDialog.setOnDismissListener(null);trackDialog.dismiss();trackDialog=null;}if(vodSession!=null){vodSession.close();vodSession=null;}if(hideControls!=null){controlHandler.removeCallbacks(hideControls);hideControls=null;}if(advanceTimeout!=null){controlHandler.removeCallbacks(advanceTimeout);advanceTimeout=null;}if(countdownTick!=null){controlHandler.removeCallbacks(countdownTick);countdownTick=null;}if(liveRecovery!=null){liveRecovery.close();liveRecovery=null;}playerControls=null;if(playerView!=null){playerView.setPlayer(null);playerView=null;}if(video!=null){video.release();video=null;}}
  private void brand(LinearLayout holder,int size){TextView t=text("SNAPTVNOW",size,CYAN,true);t.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD_ITALIC));holder.addView(t);}
  private void navigation(LinearLayout holder,boolean horizontal){for(int i=0;i<SECTIONS.length;i++){String s=SECTIONS[i],label=ICONS[i]+"  "+s;TextView t=action(label,()->{section=s;render();});t.setTextSize(horizontal?13:14);t.setGravity(horizontal?Gravity.CENTER:Gravity.CENTER_VERTICAL);if(s.equals(section)){t.setBackground(gradient(0xff106580,0xff0b354e,12));t.setTextColor(CYAN);}LinearLayout.LayoutParams lp=horizontal?new LinearLayout.LayoutParams(d(105),d(46)):new LinearLayout.LayoutParams(-1,d(49));lp.setMargins(0,0,horizontal?d(5):0,horizontal?0:d(4));holder.addView(t,lp);if(s.equals(section))initialFocus=t;}}
  private void content(LinearLayout holder){
    if(wide){LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);holder.addView(top);
      TextView page=text(section,22,WHITE,true);top.addView(page,new LinearLayout.LayoutParams(0,d(40),1));
      top.addView(action("⌕",this::openSearch),new LinearLayout.LayoutParams(d(48),d(40)));
      TextView accountButton=action("◎",()->{section="Cuenta";render();});top.addView(accountButton,new LinearLayout.LayoutParams(d(48),d(40)));gap(holder,8);}
    ScrollView sc=new ScrollView(this);sc.setFillViewport(true);holder.addView(sc,new LinearLayout.LayoutParams(-1,0,1));body=col();sc.addView(body);
    switch(section){case "Inicio":home();break;case "Explorar":explore();break;case "PPV HOY":sports();break;
      case "Cuenta":account();break;
      case "Buscar":search();break;case "Continuar viendo":continueWatching(false);break;case "Mi lista":favoritesPage();break;
      default:continueSectionPreview();if(client!=null&&!section.equals(loadedSection))categoryPicker();else catalog();}
  }
  private void login(){
    LinearLayout panel=col();panel.setPadding(d(24),d(28),d(24),d(24));root.addView(panel);
    FrameLayout splash=new FrameLayout(this);splash.setBackgroundResource(R.drawable.entertainment_hero);panel.addView(splash,new LinearLayout.LayoutParams(-1,d(wide?160:230)));
    LinearLayout heading=col();heading.setPadding(d(15),d(15),d(15),d(12));heading.setBackground(gradient(0xdd071729,0x55071729,12));splash.addView(heading,new FrameLayout.LayoutParams(-1,-1));
    brand(heading,wide?31:30);gap(heading,30);heading.addView(text("Acceso de clientes",24,WHITE,true));
    gap(panel,22);
    EditText user=new EditText(this);user.setSingleLine(true);user.setHint("Usuario de la línea");user.setTextColor(WHITE);user.setHintTextColor(MUTED);user.setBackground(shape(PANEL,12));user.setPadding(d(16),0,d(16),0);panel.addView(user,new LinearLayout.LayoutParams(wide?d(350):-1,d(55)));
    gap(panel,10);EditText pass=new EditText(this);pass.setSingleLine(true);pass.setInputType(129);pass.setHint("Contraseña de la línea");pass.setTextColor(WHITE);pass.setHintTextColor(MUTED);pass.setBackground(shape(PANEL,12));pass.setPadding(d(16),0,d(16),0);panel.addView(pass,new LinearLayout.LayoutParams(wide?d(350):-1,d(55)));
    loginUser=user;loginPass=pass;
    gap(panel,15);TextView enter=action("Entrar",()->{
      String u=user.getText().toString().trim(),p=pass.getText().toString();if(u.isEmpty()||p.isEmpty()){Toast.makeText(this,"Escribe usuario y contraseña",Toast.LENGTH_SHORT).show();return;}
      enterLine(u,p);
    });enter.setBackground(gradient(CYAN,0xff0fb5cd,12));enter.setTextColor(NAVY);panel.addView(enter,new LinearLayout.LayoutParams(wide?d(350):-1,d(54)));
    gap(panel,14);TextView vpn=action("VPN",this::openVpn);panel.addView(vpn,new LinearLayout.LayoutParams(wide?d(350):-1,d(48)));
    if(VpnTunnel.isConnected()){gap(panel,8);panel.addView(action("Desconectar VPN",this::disconnectVpn),new LinearLayout.LayoutParams(wide?d(350):-1,d(48)));}
    gap(panel,9);panel.addView(text("Tu sesión se guarda cifrada en este dispositivo hasta que cierres sesión.",13,MUTED,false));
  }
  private void openVpn(){
    String u=client!=null?client.username():loginUser!=null?loginUser.getText().toString().trim():"";
    String p=client!=null?client.password():loginPass!=null?loginPass.getText().toString():"";
    if(u.isEmpty()||p.isEmpty()){
      new AlertDialog.Builder(this).setTitle("VPN").setMessage("Introduce el usuario y la contraseña de tu línea para cargar los países disponibles.").setPositiveButton("Aceptar",null).show();return;
    }
    new Thread(()->{try{
      List<VpnLocations.Location> locations=VpnLocations.load();
      runOnUiThread(()->{
        if(isFinishing()||isDestroyed())return;
        if(locations.isEmpty()){new AlertDialog.Builder(this).setTitle("VPN").setMessage("No hay países VPN habilitados en el panel.").setPositiveButton("Aceptar",null).show();return;}
        String[] labels=new String[locations.size()];
        for(int i=0;i<locations.size();i++)labels[i]=locations.get(i).label();
        new AlertDialog.Builder(this).setTitle("Elegir VPN").setItems(labels,(dialog,which)->connectVpn(locations.get(which),u,p))
          .setNegativeButton("Cerrar",null).show();
      });
    }catch(Exception error){runOnUiThread(()->{
      if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("VPN").setMessage(error.getMessage()).setPositiveButton("Aceptar",null).show();
    });}}).start();
  }
  private void connectVpn(VpnLocations.Location location,String username,String password){
    Toast.makeText(this,"Preparando "+location.country+"…",Toast.LENGTH_SHORT).show();
    new Thread(()->{try{
      VpnLocations.Config config=VpnLocations.config(location,username,password);
      runOnUiThread(()->{
        if(isFinishing()||isDestroyed())return;
        pendingVpn=config;
        Intent consent=VpnService.prepare(this);
        if(consent!=null)startActivityForResult(consent,VPN_PERMISSION_REQUEST);
        else startPendingVpn();
      });
    }catch(Exception error){showVpnError(error.getMessage());}}).start();
  }
  @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
    super.onActivityResult(requestCode,resultCode,data);
    if(requestCode==VPN_PERMISSION_REQUEST){
      if(resultCode==RESULT_OK)startPendingVpn();
      else {pendingVpn=null;showVpnError("Android no autorizó el túnel VPN.");}
    }
  }
  private void startPendingVpn(){
    VpnLocations.Config config=pendingVpn;pendingVpn=null;
    if(config==null)return;
    Toast.makeText(this,"Conectando VPN…",Toast.LENGTH_LONG).show();
    new Thread(()->{
      try{VpnTunnel.start(this,config);}
      catch(Exception e){CrashDiagnostics.finished(this);showVpnError(e.getMessage());}
    },"vpn-start").start();
  }
  private void showVpnError(String message){runOnUiThread(()->{
    if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("VPN")
      .setMessage(message==null?"No se pudo iniciar la VPN.":message).setPositiveButton("Aceptar",null).show();
  });}
  private void disconnectVpn(){
    if(VpnTunnel.disconnect(this))Toast.makeText(this,"Desconectando VPN…",Toast.LENGTH_SHORT).show();
    else showVpnError("No hay un túnel VPN activo.");
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
    if(vodSession!=null)vodSession.saveNow();
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
        searchIndex=null;searchClient=null;activeGroup=newGroup;activeGroupSection=newGroup==null?"":category;loadedSection=newGroup==null?"":category;refreshing=false;render();loadAdvertisement();
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
    gap(body,18);continueWatching(true);
    if(!items.isEmpty()){gap(body,18);title(client==null?"Vista previa":"De tu última categoría");catalogRow(items.subList(0,Math.min(12,items.size())));}
    gap(body,12);body.addView(text(client==null?"Vista de demostración. Conecta una línea para consultar su catálogo.":"Explora las categorías de tu línea para ver canales y títulos disponibles.",13,MUTED,false));
  }
  private void continueWatching(boolean preview){
    if(preview||!television())title("Continuar viendo");
    if(!preview){
      List<PlaybackHistory.Entry> all=playbackHistory.recent(playbackAccount());int movies=0;for(PlaybackHistory.Entry entry:all)if(entry.item.id.startsWith("movie"))movies++;
      LinearLayout filters=new LinearLayout(this);body.addView(filters);String[] names={"Todo · "+all.size(),"Películas · "+movies,"Series · "+(all.size()-movies)};PlaybackHistory.Kind[] kinds=PlaybackHistory.Kind.values();
      for(int index=0;index<kinds.length;index++){
        PlaybackHistory.Kind kind=kinds[index];boolean active=kind==continueKind;TextView tab=action(names[index],()->{continueKind=kind;continueFocusId="";render();});tab.setTextSize(13);
        tab.setTextColor(active?NAVY:WHITE);tab.setBackground(active?gradient(CYAN,0xff12a9c6,12):shape(PANEL,12));
        tab.setOnFocusChangeListener((v,focus)->{tab.setTextColor(active||focus?NAVY:WHITE);tab.setBackground(active||focus?gradient(CYAN,0xff12a9c6,12):shape(PANEL,12));});
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,d(44),1);p.setMargins(0,0,d(4),0);filters.addView(tab,p);
      }gap(body,10);
    }
    List<PlaybackHistory.Entry> entries=playbackHistory.recent(playbackAccount(),preview?PlaybackHistory.Kind.ALL:continueKind);
    if(!preview){String capacity=continueKind==PlaybackHistory.Kind.ALL?entries.size()+" de 16 títulos":entries.size()+" de 8 "+(continueKind==PlaybackHistory.Kind.MOVIES?"películas":"series");body.addView(text(capacity,12,MUTED,false));gap(body,8);}
    if(entries.isEmpty())body.addView(text("Aquí aparecerán las películas y el último capítulo que dejes pendiente en este dispositivo.",14,MUTED,false));
    appendContinueEntries(preview?entries.subList(0,Math.min(4,entries.size())):entries,!preview&&television());
    if(preview){body.addView(action("Ver todo · Continuar viendo",()->{continueKind=PlaybackHistory.Kind.ALL;section="Continuar viendo";render();}),new LinearLayout.LayoutParams(-1,d(44)));}
    else{gap(body,8);String note=continueKind==PlaybackHistory.Kind.ALL?"Máximo 8 películas y 8 series. Sale el título menos reciente del mismo tipo.":continueKind==PlaybackHistory.Kind.MOVIES?"Al agregar la 9.ª película, sale la menos reciente.":"Al agregar la 9.ª serie, sale la menos reciente.";body.addView(text(note,12,MUTED,false));}
  }
  private void continueSectionPreview(){
    if(!section.equals("Películas")&&!section.equals("Series"))return;
    playbackHistory.rememberBookmarks(playbackAccount(),items);
    PlaybackHistory.Kind kind=section.equals("Películas")?PlaybackHistory.Kind.MOVIES:PlaybackHistory.Kind.SERIES;
    List<PlaybackHistory.Entry> entries=playbackHistory.recent(playbackAccount(),kind);
    title("Continuar "+section.toLowerCase(Locale.ROOT));
    if(entries.isEmpty())body.addView(text("Los títulos que dejes pendientes aparecerán aquí.",14,MUTED,false));
    appendContinueEntries(entries.subList(0,Math.min(2,entries.size())),false);
    body.addView(action("Ver todo · Continuar "+section.toLowerCase(Locale.ROOT),()->{continueKind=kind;section="Continuar viendo";render();}),new LinearLayout.LayoutParams(-1,d(44)));gap(body,18);
  }
  private void appendContinueEntries(List<PlaybackHistory.Entry> entries,boolean tv){
    if(entries.isEmpty())return;
    ContinueWatchingView view=new ContinueWatchingView(this,entries,tv,continueFocusId,this::resumeEntry,this::removeContinueEntry,this::bindContinueCover);
    body.addView(view,new LinearLayout.LayoutParams(-1,-2));
    if(tv)initialFocus=view.initialFocus();
  }
  private void bindContinueCover(ImageView image,PlaybackHistory.Entry entry){
    Catalog.Item known=seen.get(entry.item.id);if((known==null||known.artwork.isEmpty())&&!entry.item.seriesId.isEmpty())known=seen.get(entry.item.seriesId);
    Artwork.intoHistory(image,PlaybackHistory.artworkKey(playbackAccount(),entry.item),known==null?entry.item.artwork:known.artwork);
  }
  private void removeContinueEntry(PlaybackHistory.Entry entry){
    List<PlaybackHistory.Entry> before=playbackHistory.recent(playbackAccount(),continueKind);int index=0;
    for(int i=0;i<before.size();i++)if(before.get(i).item.id.equals(entry.item.id)){index=i;break;}
    int scroll=body.getParent() instanceof ScrollView?((ScrollView)body.getParent()).getScrollY():0;
    playbackHistory.removeFromContinue(playbackAccount(),entry);
    List<PlaybackHistory.Entry> after=playbackHistory.recent(playbackAccount(),continueKind);
    continueFocusId=after.isEmpty()?"":after.get(Math.min(index,after.size()-1)).item.id;
    Toast.makeText(this,"Quitado de Continuar viendo. Conserva tu progreso.",Toast.LENGTH_SHORT).show();render();
    if(!television()&&body.getParent() instanceof ScrollView){ScrollView target=(ScrollView)body.getParent();target.post(()->target.scrollTo(0,scroll));}
  }
  private void resumeEntry(PlaybackHistory.Entry entry){
    if(client==null){Toast.makeText(this,"Conecta tu línea para continuar",Toast.LENGTH_LONG).show();return;}
    if(entry.completed&&!entry.item.seriesId.isEmpty()){
      openItem(new Catalog.Item(entry.item.seriesId,entry.item.seriesTitle,"Series","",""));return;
    }
    try{play(client.resume(entry));}catch(Exception error){Toast.makeText(this,"No se pudo recuperar este título",Toast.LENGTH_LONG).show();}
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
        body.removeAllViews();continueSectionPreview();title(requested);
        List<XtreamClient.Group> visible=new ArrayList<>();
        for(XtreamClient.Group group:groups){
          if(requested.equals("PPV HOY")&&!group.name.toLowerCase(Locale.ROOT).matches(".*(ppv|evento|event|pay.per.view|ufc|boxeo).*"))continue;
          visible.add(group);
        }
        if(visible.isEmpty())body.addView(text("No hay categorías disponibles.",16,MUTED,false));
        else categoryGrid(visible,requested);
      });
    }catch(Exception e){runOnUiThread(()->{
      if(section.equals(requested)&&body==target){body.removeAllViews();continueSectionPreview();title(requested);body.addView(text("No se pudieron cargar categorías: "+e.getMessage(),15,MUTED,false));}
    });}}).start();
  }
  private void categoryGrid(List<XtreamClient.Group> groups,String requested){
    int columns=CatalogGridLayout.columns(television(),catalogWindowWidth());
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
          LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,d(wide?84:80),1);
          lp.setMargins(d(3),d(3),d(3),d(6));row.addView(button,lp);
        }else row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
      }
    }
  }
  private void loadGroup(String requested,XtreamClient.Group group){body.removeAllViews();title(group.name);body.addView(text("Cargando contenido…",15,MUTED,false));new Thread(()->{try{List<Catalog.Item> loaded=client.loadCategory(requested,group.id,group.name);runOnUiThread(()->{if(!section.equals(requested))return;items=loaded;for(Catalog.Item item:loaded){seen.put(item.id,item);if(prefs.getBoolean("fav_"+item.id,false))favorites.add(item.id);}loadedSection=requested;activeGroup=group;activeGroupSection=requested;render();});}catch(Exception e){runOnUiThread(()->new AlertDialog.Builder(this).setTitle("No se pudo cargar la categoría").setMessage(e.getMessage()).setPositiveButton("Aceptar",null).show());}}).start();}
  private void catalog(){title(section);if(client!=null&&!section.equals("Mi lista")){TextView choose=action("Cambiar categoría",()->{loadedSection="";render();});body.addView(choose,new LinearLayout.LayoutParams(d(190),d(44)));gap(body,12);}List<Catalog.Item> matches=new ArrayList<>();for(Catalog.Item i:items)if(i.category.equals(section)||section.equals("TV en vivo")&&(i.category.equals("Deportes")||i.category.equals("PPV HOY"))||section.equals("Mi lista")&&favorites.contains(i.id))matches.add(i);if(matches.isEmpty()){body.addView(text("Aún no hay títulos en esta sección.",16,MUTED,false));return;}body.addView(text(client==null?"Contenido de demostración · selecciona una tarjeta":"Contenido de tu línea · selecciona una tarjeta",14,MUTED,false));gap(body,12);if(section.equals("TV en vivo")||section.equals("PPV HOY"))catalogGrid(matches);else catalogRow(matches);}
  private void catalogGrid(List<Catalog.Item> list){
    CatalogCardGrid grid=new CatalogCardGrid(this,list,television(),catalogWindowWidth(),item->poster(item,PANEL,item.id.startsWith("live")));
    body.addView(grid,new LinearLayout.LayoutParams(-1,-2));
  }
  private void catalogRow(List<Catalog.Item> list){catalogGrid(list);}
  private FrameLayout poster(Catalog.Item item,int shade,boolean live){
      if(live)return new ChannelCard(this,item,favorites.contains(item.id),()->openItem(item),()->{toggleFavorite(item);if(section.equals("Mi lista"))render();},()->detail(item));
      FrameLayout card=new FrameLayout(this);card.setBackground(gradient(shade,PANEL,12));card.setClipToOutline(true);
      ImageView art=new ImageView(this);art.setScaleType(ImageView.ScaleType.CENTER_CROP);card.addView(art,new FrameLayout.LayoutParams(-1,-1));Artwork.into(art,item.artwork);
      LinearLayout caption=col();caption.setPadding(d(9),d(6),d(8),d(8));caption.setBackground(gradient(0x66071729,0xee071729,9));
      FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);card.addView(caption,cp);
      TextView type=text(live?"●  EN VIVO":item.category.toUpperCase(Locale.ROOT),10,CYAN,true);caption.addView(type);
      if(item.id.startsWith("movie")){long position=playbackHistory.position(playbackAccount(),item.id);if(position>0){TextView resume=text("Continuar · "+PlaybackHistory.time(position),11,CYAN,true);resume.setSingleLine(true);resume.setEllipsize(TextUtils.TruncateAt.END);caption.addView(resume);}}
      TextView name=text(item.title,14,WHITE,true);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.addView(name);
      TextView save=action(favorites.contains(item.id)?"♥":"♡",()->{toggleFavorite(item);render();});save.setTextSize(20);
      FrameLayout.LayoutParams saveParams=new FrameLayout.LayoutParams(d(42),d(40),Gravity.TOP|Gravity.RIGHT);saveParams.setMargins(0,d(5),d(5),0);card.addView(save,saveParams);
      card.setFocusable(true);card.setOnClickListener(v->openItem(item));card.setOnLongClickListener(v->{detail(item);return true;});
      card.setOnFocusChangeListener((v,focus)->{card.setBackground(focus?gradient(CYAN,shade,12):gradient(shade,PANEL,12));card.setScaleX(focus?1.04f:1f);card.setScaleY(focus?1.04f:1f);});
      return card;
  }
  private int nIndex(Catalog.Item item){return Math.max(0,items.indexOf(item));}
  private void openItem(Catalog.Item item){
    if(MovieResumeDialog.show(this,playbackHistory,playbackAccount(),item,()->play(item)))return;
    if(client==null||!item.id.startsWith("series")){play(item);return;}
    final XtreamClient session=client;final LinearLayout origin=body;
    new Thread(()->{try{
      List<Catalog.Item> episodes=session.episodes(item);
      runOnUiThread(()->{
        if(client!=session||body!=origin||playing||isFinishing()||isDestroyed())return;
        if(episodes.isEmpty()){Toast.makeText(this,"Sin episodios disponibles",Toast.LENGTH_LONG).show();return;}
        episodeQueue=new ArrayList<>(episodes);episodeQueueSeries=item.id;episodeQueueClient=session;
        int recommended=EpisodeSelection.recommended(episodes,playbackHistory,playbackAccount());
        Catalog.Item next=episodes.get(recommended);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(item.title)
            .setSingleChoiceItems(EpisodeSelection.labels(episodes,playbackHistory,playbackAccount()),recommended,(picker,index)->{picker.dismiss();play(episodes.get(index));})
            .setNeutralButton(playbackHistory.position(playbackAccount(),next.id)>0?"Continuar":"Ver capítulo",(picker,which)->play(next))
            .setNegativeButton("Cerrar",null).create();
        dialog.setOnShowListener(visible->{dialog.getListView().requestFocus();dialog.getListView().setSelection(recommended);});dialog.show();
      });
    }catch(Exception error){runOnUiThread(()->{if(client==session&&body==origin&&!isFinishing()&&!isDestroyed())Toast.makeText(this,"No se pudieron cargar episodios",Toast.LENGTH_LONG).show();});}}).start();
  }
  private void detail(Catalog.Item i){new AlertDialog.Builder(this).setTitle(i.title).setMessage(i.description+(client==null?"\n\nContenido de demostración.":"\n\nContenido de tu línea.")).setPositiveButton("Reproducir",(a,b)->openItem(i)).setNeutralButton(favorites.contains(i.id)?"Quitar favorito":"Añadir favorito",(a,b)->{if(!favorites.add(i.id))favorites.remove(i.id);prefs.edit().putBoolean("fav_"+i.id,favorites.contains(i.id)).apply();render();}).setNegativeButton("Cerrar",null).show();}
  private void openSearch(){
    if(!section.equals("Buscar")){searchOrigin=section;searchScope=CatalogSearch.Scope.forSection(section,searchScope);query="";}
    section="Buscar";render();
  }
  private void search(){
    title("Buscar en todas las carpetas");
    LinearLayout scopes=new LinearLayout(this);body.addView(scopes);
    for(CatalogSearch.Scope scope:CatalogSearch.Scope.values()){
      TextView tab=action(scope==CatalogSearch.Scope.CHANNELS?"Canales":scope.section,()->{searchScope=scope;render();});
      if(scope==searchScope){tab.setTextColor(NAVY);tab.setBackground(gradient(CYAN,0xff12a9c6,12));}
      scopes.addView(tab,new LinearLayout.LayoutParams(0,d(46),1));
    }
    gap(body,10);
    SearchInput input=new SearchInput(this);searchInput=input;input.setHint("Buscar "+(searchScope==CatalogSearch.Scope.CHANNELS?"canales":searchScope.section.toLowerCase(Locale.ROOT))+" en todas las carpetas");
    input.setTextColor(WHITE);input.setHintTextColor(MUTED);input.setText(query);body.addView(input);
    LinearLayout buttons=new LinearLayout(this);body.addView(buttons);
    TextView status=text("",14,MUTED,false);body.addView(status);gap(body,12);
    LinearLayout results=col();body.addView(results);
    final int page=searchScreenGeneration;
    Runnable submit=()->submitSearch(input.getText().toString(),status,results,page);
    TextView searchButton=action("Buscar",submit);buttons.addView(searchButton,new LinearLayout.LayoutParams(0,d(44),1));input.bind(searchButton,submit);
    buttons.addView(action("Volver",()->{section=searchOrigin;render();}),new LinearLayout.LayoutParams(0,d(44),1));
    input.addTextChangedListener(new android.text.TextWatcher(){
      public void beforeTextChanged(CharSequence t,int start,int count,int after){}
      public void onTextChanged(CharSequence t,int start,int before,int count){
        query=t.toString();searchGeneration++;
        if(searchDebounce!=null)controlHandler.removeCallbacks(searchDebounce);
        searchDebounce=submit;controlHandler.postDelayed(searchDebounce,300);
      }
      public void afterTextChanged(android.text.Editable text){}
    });
    submit.run();
    if(wide)input.requestFocus();
  }
  private void submitSearch(String text,TextView status,LinearLayout results,int page){
    if(page!=searchScreenGeneration||isFinishing()||isDestroyed()||!section.equals("Buscar")||results.getParent()==null)return;
    if(searchDebounce!=null){controlHandler.removeCallbacks(searchDebounce);searchDebounce=null;}
    query=text.trim();int request=++searchGeneration;
    results.removeAllViews();
    if(query.isEmpty()){status.setText("Escribe el nombre del contenido. Se buscará dentro de todas las carpetas de esta sección.");return;}
    final XtreamClient session=client;final CatalogSearch.Scope scope=searchScope;final String searched=query;
    if(searchIndex==null||searchClient!=session){
      searchClient=session;
      searchIndex=new CatalogSearch(type->session==null?Catalog.demo():session.loadAll(type));
    }
    final CatalogSearch index=searchIndex;
    status.setText("Buscando en todas las carpetas de "+(scope==CatalogSearch.Scope.CHANNELS?"Canales":scope.section)+"…");
    searchExecutor.execute(()->{
      if(request!=searchGeneration)return;
      try{
        List<Catalog.Item> all=index.all(scope);
        List<Catalog.Item> matches=CatalogSearch.filter(all,scope,searched);
        runOnUiThread(()->{
          if(request!=searchGeneration||client!=session||!section.equals("Buscar")||isFinishing()||isDestroyed())return;
          status.setText(matches.isEmpty()?"Sin resultados en todas las carpetas de esta sección.":matches.size()+" resultados · todas las carpetas de "+(scope==CatalogSearch.Scope.CHANNELS?"Canales":scope.section));
          showSearchResults(results,matches,all,scope,status);
        });
      }catch(Exception error){runOnUiThread(()->{
        if(request!=searchGeneration||!section.equals("Buscar")||client!=session||isFinishing()||isDestroyed())return;
        status.setText("No se pudo cargar la búsqueda completa. Pulsa Buscar para reintentar.");
      });}
    });
  }
  private void showSearchResults(LinearLayout results,List<Catalog.Item> matches,List<Catalog.Item> all,CatalogSearch.Scope scope,TextView status){
    playbackHistory.rememberBookmarks(playbackAccount(),matches);
    results.removeAllViews();
    CatalogCardGrid grid=new CatalogCardGrid(this,matches,television(),catalogWindowWidth(),item->{
      seen.put(item.id,item);if(prefs.getBoolean("fav_"+item.id,false))favorites.add(item.id);
      LinearLayout cell=col();
      FrameLayout card=poster(item,PANEL,scope==CatalogSearch.Scope.CHANNELS);cell.addView(card,new LinearLayout.LayoutParams(-1,0,1));
      card.setOnClickListener(v->{
        if(scope==CatalogSearch.Scope.CHANNELS){items=new ArrayList<>();for(Catalog.Item candidate:all)if(candidate.description.equals(item.description))items.add(candidate);loadedSection="TV en vivo";activeGroup=null;activeGroupSection="";}
        openItem(item);
      });
      TextView folder=text(item.description,11,MUTED,false);folder.setMaxLines(1);folder.setEllipsize(TextUtils.TruncateAt.END);cell.addView(folder);
      return cell;
    });
    results.addView(grid,new LinearLayout.LayoutParams(-1,-2));
  }
  private String formatExpiry(String value){try{long unix=Long.parseLong(value);if(unix<=0)return "Sin fecha";return new java.text.SimpleDateFormat("dd/MM/yyyy",Locale.getDefault()).format(new java.util.Date(unix*1000));}catch(Exception e){return "No disponible";}}
  private void account(){title("Cuenta y configuración");body.addView(text(client==null?"Sesión de demostración":"Línea activa",18,WHITE,true));gap(body,10);body.addView(text(client==null?"La fecha de vencimiento y el límite de dispositivos se mostrarán al conectar una línea.":"Vencimiento: "+formatExpiry(client.expires)+"  ·  Conexiones: "+client.maxConnections,15,MUTED,false));gap(body,20);if(client!=null){body.addView(text("Seguridad: el proveedor utiliza HTTP sin cifrado; las credenciales pueden ser visibles en la red.",13,MUTED,false));gap(body,20);body.addView(action("VPN",this::openVpn),new LinearLayout.LayoutParams(d(185),d(52)));gap(body,14);body.addView(text("VPN: "+(VpnTunnel.isConnected()?"conectada":"sin conexión"),14,MUTED,false));gap(body,10);body.addView(action("Desconectar VPN",this::disconnectVpn),new LinearLayout.LayoutParams(d(185),d(52)));gap(body,14);}body.addView(action("Cerrar sesión",()->{SessionStore.clear(this);logged=false;client=null;items=Catalog.demo();loadedSection="";activeGroup=null;activeGroupSection="";recentlyPlayed.clear();seen.clear();render();}),new LinearLayout.LayoutParams(d(185),d(52)));}
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
    if(episodeEndView!=null)return;
    if(vodSession!=null&&playerView!=null){playerView.showController();return;}
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
    final boolean vod=!item.id.startsWith("live");
    final boolean sameSession=playing&&currentItem==item;
    final long lifecyclePosition=sameSession&&vod?resumePosition:-1;
    final boolean lifecyclePaused=sameSession&&resumePaused;
    final boolean restoreEpisodeEnd=sameSession&&resumeEpisodeEnded;
    if(!sameSession)liveRetries=new LivePlaybackRecovery.RetryState();
    // Flush the OLD item's bookmark while its player and identity still belong together.
    releaseVideo();currentItem=item;seen.put(item.id,item);recentlyPlayed.remove(item);recentlyPlayed.add(0,item);playing=true;
    wide=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    FrameLayout stage=new FrameLayout(this);stage.setTag("player_stage");stage.setBackgroundColor(Color.BLACK);setContentView(stage);
    playerView=new PlayerView(this);playerView.setUseController(false);playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);playerView.setKeepScreenOn(true);if(vod)VodPlayerControls.configure(playerView);stage.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
    video=new ExoPlayer.Builder(this,new androidx.media3.exoplayer.DefaultRenderersFactory(this).setEnableDecoderFallback(true))
        .setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true)
        .setHandleAudioBecomingNoisy(true).setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build();playerView.setPlayer(video);
    final ExoPlayer activePlayer=video;
    final PlayerView activeView=playerView;
    if(sameSession && resumeTrackParameters!=null)video.setTrackSelectionParameters(resumeTrackParameters);
    resumeTrackParameters=null;
    video.addListener(new Player.Listener(){@Override public void onPlaybackStateChanged(int state){
      if(state!=Player.STATE_READY||currentItem!=item||video!=activePlayer||!playing)return;
      if(advanceTimeout!=null){controlHandler.removeCallbacks(advanceTimeout);advanceTimeout=null;}
      if(findingOnline){findingOnline=false;View searching=stage.findViewWithTag("searching");if(searching!=null)stage.removeView(searching);}
    }@Override public void onPlayerError(PlaybackException error){
      if(!playing||currentItem!=item||video!=activePlayer)return;
      if(findingOnline)controlHandler.post(()->{if(playing&&findingOnline&&currentItem==item)tryNextOnline(item);});
      else if(vod)unavailable(stage,item,null);
    }});
    if(!vod){
      liveRecovery=new LivePlaybackRecovery(activePlayer,liveRetries,reason->{
        if(!playing||currentItem!=item||video!=activePlayer)return;
        if(findingOnline){tryNextOnline(item);return;}
        android.util.Log.w("LiveRecovery",reason); // No stream URL, account or credentials in logs.
        resumePaused=false;resumeTrackParameters=null;
        play(item); // Release the old audio/video renderers before creating a fresh session.
      });
      final LivePlaybackRecovery recovery=liveRecovery;
      activePlayer.setVideoFrameMetadataListener((presentationTimeUs,releaseTimeNs,format,mediaFormat)->recovery.videoFrame());
      activePlayer.addListener(new Player.Listener(){@Override public void onTracksChanged(androidx.media3.common.Tracks tracks){
        recovery.videoExpected(tracks.isTypeSelected(C.TRACK_TYPE_VIDEO));
      }});
      activePlayer.addAnalyticsListener(new androidx.media3.exoplayer.analytics.AnalyticsListener(){
        @Override public void onAudioSinkError(EventTime time,Exception error){recovery.failure("audio_sink_error");}
        @Override public void onAudioCodecError(EventTime time,Exception error){recovery.failure("audio_codec_error");}
        @Override public void onVideoCodecError(EventTime time,Exception error){recovery.failure("video_codec_error");}
      });
    }
    if(vod){playbackHistory.remember(playbackAccount(),item);Artwork.rememberHistory(getApplicationContext(),PlaybackHistory.artworkKey(playbackAccount(),item),item.artwork);vodSession=new VodPlaybackSession(video,playbackHistory,playbackAccount(),item.id);vodSession.start(MediaItem.fromUri(item.url),lifecyclePosition,lifecyclePaused);}
    else{video.setMediaItem(MediaItem.fromUri(item.url));video.setPlayWhenReady(!lifecyclePaused);video.prepare();}
    resumePosition=-1;resumePaused=false;resumeEpisodeEnded=false;
    if(!vod){View tapLayer=new View(this);stage.addView(tapLayer,new FrameLayout.LayoutParams(-1,-1));tapLayer.setOnClickListener(v->{if(playerControls!=null&&playerControls.getVisibility()==View.VISIBLE)playerControls.setVisibility(View.GONE);else showPlayerControls();});}
    playerControls=new FrameLayout(this);stage.addView(playerControls,new FrameLayout.LayoutParams(-1,-1));
    if(vod)playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener)visibility->{if(playerView==activeView&&playerControls!=null)playerControls.setVisibility(visibility);});
    LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(d(12),d(10),d(12),d(10));top.setBackground(gradient(0xe6071729,0x99071729,0));
    playerControls.addView(top,new FrameLayout.LayoutParams(-1,d(65),Gravity.TOP));
    TextView back=action("←",this::render);top.addView(back,new LinearLayout.LayoutParams(d(49),d(45)));
    TextView label=text("SNAPTVNOW  ·  "+item.title,wide?18:15,WHITE,true);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(0,-1,1);labelParams.leftMargin=d(10);top.addView(label,labelParams);
    TextView fav=action("♡",()->{toggleFavorite(item);showPlayerControls();});top.addView(fav,new LinearLayout.LayoutParams(d(48),d(45)));
    if(vod){
      HorizontalScrollView toolbar=new HorizontalScrollView(this);toolbar.setHorizontalScrollBarEnabled(false);
      FrameLayout.LayoutParams toolbarParams=new FrameLayout.LayoutParams(-1,d(48),Gravity.TOP);toolbarParams.topMargin=d(65);playerControls.addView(toolbar,toolbarParams);
      LinearLayout options=new LinearLayout(this);options.setPadding(d(12),0,d(12),0);options.setBackgroundColor(0xc0071729);toolbar.addView(options);
      if(EpisodeQueue.isEpisode(item)){
        nextEpisodeButton=action("Siguiente capítulo",()->{if(episodeController!=null)episodeController.nextNow();});
        nextEpisodeButton.setEnabled(false);options.addView(nextEpisodeButton,new LinearLayout.LayoutParams(d(160),d(45)));
        automaticEpisodeButton=action("",this::toggleEpisodeAutomatic);
        automaticEpisodeButton.setText(prefs.getBoolean("series_autoplay",true)?"Auto: activada":"Auto: desactivada");
        options.addView(automaticEpisodeButton,new LinearLayout.LayoutParams(d(150),d(45)));
      }
      options.addView(action("Audio",()->openTrackOptions(activePlayer,C.TRACK_TYPE_AUDIO)),new LinearLayout.LayoutParams(d(90),d(45)));
      options.addView(action("Subtítulos",()->openTrackOptions(activePlayer,C.TRACK_TYPE_TEXT)),new LinearLayout.LayoutParams(d(110),d(45)));
      TextView restart=action("Desde inicio",()->{if(video==activePlayer){playbackHistory.reset(playbackAccount(),item.id);video.seekTo(0);video.play();showPlayerControls();}});restart.setTextSize(12);options.addView(restart,new LinearLayout.LayoutParams(d(95),d(45)));
      options.addView(action("⛶",()->{if(playerView==activeView){int mode=playerView.getResizeMode();playerView.setResizeMode(mode==AspectRatioFrameLayout.RESIZE_MODE_FIT?AspectRatioFrameLayout.RESIZE_MODE_ZOOM:AspectRatioFrameLayout.RESIZE_MODE_FIT);showPlayerControls();}}),new LinearLayout.LayoutParams(d(48),d(45)));
    }else{
    LinearLayout bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER);bottom.setPadding(d(8),d(8),d(8),d(8));bottom.setBackground(gradient(0x33071729,0xc6071729,0));
    playerControls.addView(bottom,new FrameLayout.LayoutParams(-1,d(76),Gravity.BOTTOM));
    TextView pause=action("❚❚",()->{});pause.setOnClickListener(v->{if(video==null)return;if(video.getPlayWhenReady()){video.pause();pauseLabel(pause,false);}else{video.play();pauseLabel(pause,true);}showPlayerControls();});bottom.addView(pause,new LinearLayout.LayoutParams(d(54),d(48)));
    TextView guideButton=action("Guía",()->{if(client!=null&&item.id.startsWith("live"))loadEpg(item);else Toast.makeText(this,"Guía no disponible para este título",Toast.LENGTH_SHORT).show();showPlayerControls();});bottom.addView(guideButton,new LinearLayout.LayoutParams(0,d(48),1));
    TextView channelList=action("Canales",()->{render();});bottom.addView(channelList,new LinearLayout.LayoutParams(0,d(48),1));
    TextView aspect=action("⛶",()->{if(playerView==null)return;int mode=playerView.getResizeMode();playerView.setResizeMode(mode==AspectRatioFrameLayout.RESIZE_MODE_FIT?AspectRatioFrameLayout.RESIZE_MODE_ZOOM:AspectRatioFrameLayout.RESIZE_MODE_FIT);showPlayerControls();});bottom.addView(aspect,new LinearLayout.LayoutParams(d(51),d(48)));
    }
    if(findingOnline){
      TextView searching=text("Buscando el siguiente canal disponible…",wide?20:17,WHITE,true);
      searching.setTag("searching");searching.setGravity(Gravity.CENTER);searching.setBackgroundColor(0xb0071729);
      stage.addView(searching,new FrameLayout.LayoutParams(-1,d(62),Gravity.CENTER));
      advanceTimeout=()->{if(playing&&findingOnline&&currentItem==item)tryNextOnline(item);};
      controlHandler.postDelayed(advanceTimeout,5000);
    }
    if(vod)playerView.requestFocus();else back.requestFocus();showPlayerControls();
    if(EpisodeQueue.isEpisode(item))setupEpisodePlayback(item,activePlayer,stage,restoreEpisodeEnd);
  }
  private void setupEpisodePlayback(Catalog.Item item,ExoPlayer activePlayer,FrameLayout stage,boolean restoredEnd){
    episodeController=new EpisodePlaybackController(activePlayer,prefs.getBoolean("series_autoplay",true),new EpisodePlaybackController.Listener(){
      public void onStatus(EpisodePlaybackController.State state){
        if(video!=activePlayer||currentItem!=item||!playing||isFinishing()||isDestroyed())return;
        if(nextEpisodeButton!=null){nextEpisodeButton.setEnabled(!state.loading&&!state.failed&&state.next!=null&&!state.advancing);nextEpisodeButton.setAlpha(nextEpisodeButton.isEnabled()?1f:.5f);}
        if(automaticEpisodeButton!=null)automaticEpisodeButton.setText(state.automatic?"Auto: activada":"Auto: desactivada");
        if(!state.ended){
          View cover=stage.findViewWithTag("episode_end");if(cover!=null)stage.removeView(cover);
          if(episodeEndView!=null){episodeEndView=null;if(playerView!=null){playerView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);playerView.showController();}}
          return;
        }
        if(hideControls!=null){controlHandler.removeCallbacks(hideControls);hideControls=null;}
        if(playerControls!=null)playerControls.setVisibility(View.GONE);
        if(playerView!=null){playerView.hideController();playerView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);}
        if(trackDialog!=null){trackDialog.dismiss();trackDialog=null;}
        boolean first=episodeEndView==null;
        if(first){
          FrameLayout cover=new FrameLayout(MainActivity.this);cover.setTag("episode_end");cover.setBackgroundColor(0xe6071729);cover.setClickable(true);
          stage.addView(cover,new FrameLayout.LayoutParams(-1,-1));
          episodeEndView=new EpisodeEndView(MainActivity.this,()->{if(episodeController!=null)episodeController.nextNow();},
              ()->{if(episodeController!=null)episodeController.cancel();},MainActivity.this::toggleEpisodeAutomatic,
              ()->{Catalog.Item parent=new Catalog.Item(item.seriesId,item.seriesTitle,"Series","","",item.artwork);section="Series";render();openItem(parent);});
          cover.setPadding(d(12),d(12),d(12),d(12));
          ScrollView endScroll=new ScrollView(MainActivity.this);endScroll.setFillViewport(false);
          FrameLayout.LayoutParams card=new FrameLayout.LayoutParams(Math.min(d(540),getResources().getDisplayMetrics().widthPixels-d(24)),-2,Gravity.CENTER);cover.addView(endScroll,card);
          endScroll.addView(episodeEndView,new ScrollView.LayoutParams(-1,-2));
        }
        episodeEndView.update(state,()->loadEpisodeQueue(item,activePlayer));
        if(first){EpisodeEndView focusTarget=episodeEndView;focusTarget.post(()->{if(episodeEndView==focusTarget&&video==activePlayer)focusTarget.initialFocus().requestFocus();});}
      }
      public void onNext(Catalog.Item next){if(video==activePlayer&&currentItem==item&&playing&&!isFinishing()&&!isDestroyed())play(next);}
    });
    if(restoredEnd)episodeController.restoreEnded();
    if(episodeQueueClient==client&&episodeQueueSeries.equals(item.seriesId))episodeController.resolve(EpisodeQueue.next(episodeQueue,item));
    else loadEpisodeQueue(item,activePlayer);
    if(activePlayer.getPlaybackState()==Player.STATE_ENDED)episodeController.onPlaybackStateChanged(Player.STATE_ENDED);
  }
  private void toggleEpisodeAutomatic(){
    boolean automatic=!prefs.getBoolean("series_autoplay",true);prefs.edit().putBoolean("series_autoplay",automatic).apply();
    if(episodeController!=null)episodeController.setAutomatic(automatic);
  }
  private void loadEpisodeQueue(Catalog.Item item,ExoPlayer activePlayer){
    final XtreamClient session=client;final EpisodePlaybackController controller=episodeController;
    final int request=++episodeLoadGeneration;
    if(controller==null)return;controller.retrying();
    if(session==null){controller.resolutionFailed();return;}
    new Thread(()->{try{
      List<Catalog.Item> episodes=session.episodes(new Catalog.Item(item.seriesId,item.seriesTitle,"Series","","",item.artwork));
      runOnUiThread(()->{
        if(request!=episodeLoadGeneration||client!=session||video!=activePlayer||episodeController!=controller||currentItem!=item||!playing||isFinishing()||isDestroyed())return;
        episodeQueue=new ArrayList<>(episodes);episodeQueueSeries=item.seriesId;episodeQueueClient=session;
        controller.resolve(EpisodeQueue.next(episodes,item));
      });
    }catch(Exception ignored){runOnUiThread(()->{if(request==episodeLoadGeneration&&video==activePlayer&&episodeController==controller&&!isFinishing()&&!isDestroyed())controller.resolutionFailed();});}}).start();
  }
  private void openTrackOptions(Player player,int type){
    if(video!=player)return;
    if(trackDialog!=null){trackDialog.setOnDismissListener(null);trackDialog.dismiss();}
    trackDialog=PlayerTrackOptions.show(this,player,type,()->{trackDialog=null;if(video==player)showPlayerControls();});
  }
  @Override protected void onDestroy(){searchGeneration++;if(searchDebounce!=null)controlHandler.removeCallbacks(searchDebounce);searchExecutor.shutdownNow();releaseVideo();super.onDestroy();}
  private String playbackAccount(){return client!=null?client.username():"demo";}
  private void pauseLabel(TextView button,boolean playingNow){button.setText(playingNow?"❚❚":"▶");}
  @Override public boolean dispatchKeyEvent(KeyEvent event){if(playing&&episodeController!=null&&event.getKeyCode()==KeyEvent.KEYCODE_MEDIA_NEXT){if(event.getAction()==KeyEvent.ACTION_DOWN)episodeController.nextNow();return true;}if(playing&&episodeEndView==null&&vodSession!=null&&playerView!=null&&VodPlayerControls.dispatchKeyEvent(playerView,event))return true;return super.dispatchKeyEvent(event);}
  @Override public boolean onKeyDown(int code,KeyEvent event){if(playing&&(code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN)){showPlayerControls();}return super.onKeyDown(code,event);}
  private void rememberPlayback(){
    if(video!=null)resumeTrackParameters=video.getTrackSelectionParameters();if(playing&&video!=null){resumeEpisodeEnded=episodeController!=null&&episodeController.state().ended;resumePosition=vodSession!=null?vodSession.lifecyclePosition():Math.max(0,video.getCurrentPosition());resumePaused=!video.getPlayWhenReady()||(vodSession!=null&&video.getPlaybackState()==Player.STATE_ENDED);}}
  @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);if(playing&&currentItem!=null){rememberPlayback();play(currentItem);}else render();}
  @Override protected void onStart(){super.onStart();if(playing&&video==null&&currentItem!=null)play(currentItem);}
  @Override protected void onStop(){rememberPlayback();releaseVideo();super.onStop();}
  @Override public void onBackPressed(){if(playing){render();return;}if(section.equals("Buscar")){if(searchInput!=null&&searchInput.hasFocus()){searchInput.leaveEditor();return;}section=searchOrigin;render();return;}if(logged&&!section.equals("Inicio")){section="Inicio";render();return;}super.onBackPressed();}
}

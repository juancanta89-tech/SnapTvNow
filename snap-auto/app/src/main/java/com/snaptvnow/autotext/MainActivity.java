package com.snaptvnow.autotext;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.app.AlarmManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.ContactsContract;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int TEAL=Color.rgb(0,159,169), DARK=Color.rgb(8,36,46), PALE=Color.rgb(232,249,249);
    private static final int IMPORT=100, EXPORT=101, RESTORE=102, SQLITE_EXPORT=103, SQLITE_RESTORE=104, BACKUP_FOLDER=105, ASK_SMS=71, ASK_RECEIVE_SMS=72, ASK_NOTICES=73, ASK_CONTACTS=74;
    private LinearLayout root, content, cards; private FrameLayout screen; private Store db; private String filter=Store.PENDING, taskFilter="Todas", search="";private int visibleLimit=40;
    private boolean awaitingExactPermission=false, lastExactGranted, lastSmsGranted, permissionsInitialized=false;
    private EditText pendingContactSelection;
    private final ArrayDeque<String> backStack=new ArrayDeque<>();
    private String currentRoute="home";
    private boolean returning=false;
    private final java.util.Map<String,String> selectedContactNames=new java.util.HashMap<>();
    @Override public void onCreate(Bundle b){super.onCreate(b);db=new Store(this);List<Store.Task> activated=db.activateImportedFutureOnce();activated.addAll(db.repairImportedRecipients());db.rebuildContactsFromTasks();AutoBackup.schedule(this);for(Store.Task t:activated)Scheduler.schedule(this,t);home();if(!activated.isEmpty())requestNeededPermissions();long id=getIntent().getLongExtra("open_task",-1);if(id>0)openWhatsApp(db.get(id));}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);long id=i.getLongExtra("open_task",-1);if(id>0)openWhatsApp(db.get(id));}
    @Override protected void onResume(){super.onResume();if(db==null)return;
        boolean exact=exactAlarmsAllowed(),sms=checkSelfPermission(Manifest.permission.SEND_SMS)==PackageManager.PERMISSION_GRANTED;
        if(!permissionsInitialized){permissionsInitialized=true;lastExactGranted=exact;lastSmsGranted=sms;return;}
        boolean changed=exact!=lastExactGranted||sms!=lastSmsGranted;lastExactGranted=exact;lastSmsGranted=sms;
        if(changed){if(exact)for(Store.Task task:db.tasks())if(Store.PENDING.equals(task.status))Scheduler.schedule(this,task);home();}
        if(awaitingExactPermission){awaitingExactPermission=false;alert(exact?"Alarmas puntuales activadas. Las tareas pendientes se reprogramaron.":"Android aún no permitió las alarmas puntuales. Activa el interruptor para SNAP Auto en esa pantalla y vuelve.");}
    }
    private boolean exactAlarmsAllowed(){return Build.VERSION.SDK_INT<31||((AlarmManager)getSystemService(ALARM_SERVICE)).canScheduleExactAlarms();}
    private void requestExactAlarms(){if(exactAlarmsAllowed()){alert("Alarmas puntuales: activadas.");return;}awaitingExactPermission=true;try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}catch(Exception ex){awaitingExactPermission=false;alert("No se pudo abrir el permiso. Ve a Ajustes del teléfono → Apps → SNAP Auto → Alarmas y recordatorios.");}}
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(10),dp(8),dp(10),dp(8));return v;}
    private Button button(String label,Runnable r){Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setOnClickListener(v->r.run());return b;}
    private void page(String route,String title){
        if(!returning){if("home".equals(route))backStack.clear();else if(!route.equals(currentRoute))backStack.push(currentRoute);}
        currentRoute=route;
        root=column();root.setFitsSystemWindows(true);root.setBackgroundColor(Color.rgb(246,250,251));setContentView(root);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setBackgroundColor(DARK);root.addView(toolbar);
        if(!backStack.isEmpty()){
            TextView back=text("‹",36,Color.WHITE);back.setGravity(Gravity.CENTER);back.setPadding(0,0,0,0);back.setContentDescription("Regresar sin guardar cambios");
            toolbar.addView(back,new LinearLayout.LayoutParams(dp(56),dp(76)));back.setOnClickListener(v->goBack());
        }
        TextView bar=text(title,23,Color.WHITE);bar.setTypeface(null,Typeface.BOLD);bar.setPadding(dp(12),dp(25),dp(12),dp(24));toolbar.addView(bar,new LinearLayout.LayoutParams(0,-2,1));
        screen=new FrameLayout(this);root.addView(screen,new LinearLayout.LayoutParams(-1,0,1));ScrollView scroll=new ScrollView(this);screen.addView(scroll,new FrameLayout.LayoutParams(-1,-1));content=column();content.setPadding(dp(14),dp(12),dp(14),dp(100));scroll.addView(content);
    }
    private void goBack(){if(backStack.isEmpty()){home();return;}if("edit".equals(currentRoute))selectedContactNames.clear();String previous=backStack.pop();returning=true;try{switch(previous){case "contacts":contacts();break;case "clients":clients();break;case "templates":templates();break;case "settings":settings();break;case "history":history();break;case "archive":legacyArchive();break;case "edit":backStack.clear();home();break;default:home();}}finally{returning=false;}}
    @Override public void onBackPressed(){if(backStack.isEmpty())super.onBackPressed();else goBack();}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void card(String title,String sub,Runnable action){LinearLayout box=column();box.setBackgroundColor(Color.WHITE);box.setPadding(dp(12),dp(9),dp(12),dp(9));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);content.addView(box,p);TextView h=text(title,17,DARK);h.setTypeface(null,Typeface.BOLD);box.addView(h);box.addView(text(sub,14,Color.DKGRAY));if(action!=null)box.setOnClickListener(v->action.run());}
    private void nav(){LinearLayout line=new LinearLayout(this);line.setBackgroundColor(DARK);for(String s:new String[]{"Tareas","Contactos","Plantillas","Ajustes"}){Button b=button(s,()->{switch(s){case "Contactos":contacts();break;case "Plantillas":templates();break;case "Ajustes":settings();break;default:home();}});b.setTextSize(11);line.addView(b,new LinearLayout.LayoutParams(0,dp(58),1));}root.addView(line);}
    private void home(){page("home","SNAP Auto  ·  Todas las tareas");content.setBackgroundColor(Color.rgb(19,25,27));
        java.util.List<Store.Task> all=db.tasks();java.util.Map<String,Integer> counts=new java.util.HashMap<>();for(Store.Task t:all)counts.put(t.status,counts.getOrDefault(t.status,0)+1);
        LinearLayout tools=new LinearLayout(this);tools.setGravity(Gravity.END);content.addView(tools);
        EditText find=new EditText(this);find.setSingleLine(true);find.setTextColor(Color.WHITE);find.setHintTextColor(Color.LTGRAY);find.setHint("⌕ Buscar por letras o número");find.setTextSize(14);find.setText(search);tools.addView(find,new LinearLayout.LayoutParams(0,dp(48),1));
        Button select=button("▽ Filtro: "+taskFilter, this::showFilters);tools.addView(select,new LinearLayout.LayoutParams(0,dp(48),1));
        LinearLayout tabs=new LinearLayout(this);tabs.setBackgroundColor(Color.rgb(40,58,62));for(String status:new String[]{Store.PENDING,Store.DONE,Store.FAILED}){
            String label=Store.DONE.equals(status)?"Hechas":Store.FAILED.equals(status)?"Fallidas":"Pendientes";
            TextView tab=text(label+"  "+counts.getOrDefault(status,0),15,status.equals(filter)?Color.WHITE:Color.LTGRAY);tab.setGravity(Gravity.CENTER);tab.setTypeface(null,status.equals(filter)?Typeface.BOLD:Typeface.NORMAL);tab.setOnClickListener(v->{filter=status;visibleLimit=40;home();});tabs.addView(tab,new LinearLayout.LayoutParams(0,dp(58),1));}
        content.addView(tabs);if(counts.getOrDefault(Store.PENDING,0)>0&&!exactAlarmsAllowed())content.addView(button("⚠ Permitir alarmas puntuales",this::requestExactAlarms));
        boolean pendingSms=false;for(Store.Task t:all)if(Store.PENDING.equals(t.status)&&"SMS".equals(t.channel)&&"Programar".equals(t.type)){pendingSms=true;break;}
        if(pendingSms&&checkSelfPermission(Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED)content.addView(button("⚠ Permitir envío de SMS",this::requestSmsPermission));
        LinearLayout others=new LinearLayout(this);for(String status:new String[]{Store.PAUSED,"Acción necesaria"}){
            Button b=button(status+" ("+counts.getOrDefault(status,0)+")",()->{filter=status;visibleLimit=40;home();});b.setTextSize(12);others.addView(b,new LinearLayout.LayoutParams(0,dp(50),1));}content.addView(others);
        cards=column();content.addView(cards);renderTaskCards();
        find.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){search=s.toString().trim();visibleLimit=40;renderTaskCards();}public void afterTextChanged(Editable e){}});
        content.addView(button("Control de envíos e historial",this::history));
        TextView add=text("+",36,Color.BLACK);add.setGravity(Gravity.CENTER);add.setPadding(0,0,0,0);add.setBackground(round(Color.rgb(244,247,190),100));add.setElevation(dp(8));add.setContentDescription("Nueva tarea");add.setOnClickListener(v->chooseType());
        FrameLayout.LayoutParams floating=new FrameLayout.LayoutParams(dp(72),dp(72),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);floating.bottomMargin=dp(20);screen.addView(add,floating);nav();}
    private void renderTaskCards(){if(cards==null)return;cards.removeAllViews();int shown=0;for(Store.Task t:db.tasks()){if(!filter.equals(t.status)||!matchesFilter(t))continue;shown++;if(shown<=visibleLimit)taskCard(t);}
        if(shown==0)cards.addView(text("No hay tareas que coincidan.",16,Color.WHITE));
        if(shown>visibleLimit)cards.addView(button("Mostrar más ("+(shown-visibleLimit)+" restantes)",()->{visibleLimit+=40;renderTaskCards();}));}
    private void showSearch(){EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Nombre, número o texto");input.setText(search);new AlertDialog.Builder(this).setTitle("Buscar tareas").setView(input).setNegativeButton("Cancelar",null).setNeutralButton("Limpiar",(d,w)->{search="";visibleLimit=40;home();}).setPositiveButton("Buscar",(d,w)->{search=input.getText().toString().trim();visibleLimit=40;home();}).show();}
    private void showFilters(){String[] options={"Todas","Hoy","Mañana","Esta semana","Este mes","Repetidas","Recordatorio","SMS","WhatsApp","Telegram","Messenger"};new AlertDialog.Builder(this).setTitle("Filtrar tareas").setItems(options,(d,n)->{taskFilter=options[n];visibleLimit=40;home();if(n>=9)alert("Este canal todavía no está disponible en SNAP Auto; no hay tareas para mostrar.");}).show();}
    private boolean matchesFilter(Store.Task t){
        if(!search.isEmpty()){String q=search.toLowerCase(Locale.ROOT);if(!(t.name+" "+t.recipient+" "+t.body).toLowerCase(Locale.ROOT).contains(q))return false;}
        if("Todas".equals(taskFilter))return true;
        if("SMS".equals(taskFilter)||"WhatsApp".equals(taskFilter))return taskFilter.equals(t.channel);
        if("Telegram".equals(taskFilter)||"Messenger".equals(taskFilter))return false;
        if("Repetidas".equals(taskFilter))return "Programar".equals(t.type)&&!"Nunca".equals(t.repeat);
        if("Recordatorio".equals(taskFilter))return "Recordatorio".equals(t.type);
        if(!"Programar".equals(t.type))return false;
        Calendar start=Calendar.getInstance();start.set(Calendar.HOUR_OF_DAY,0);start.set(Calendar.MINUTE,0);start.set(Calendar.SECOND,0);start.set(Calendar.MILLISECOND,0);
        if("Mañana".equals(taskFilter))start.add(Calendar.DAY_OF_MONTH,1);
        if("Esta semana".equals(taskFilter))start.add(Calendar.DAY_OF_MONTH,-((start.get(Calendar.DAY_OF_WEEK)-start.getFirstDayOfWeek()+7)%7));
        if("Este mes".equals(taskFilter))start.set(Calendar.DAY_OF_MONTH,1);
        Calendar end=(Calendar)start.clone();if("Esta semana".equals(taskFilter))end.add(Calendar.DAY_OF_MONTH,7);else if("Este mes".equals(taskFilter))end.add(Calendar.MONTH,1);else end.add(Calendar.DAY_OF_MONTH,1);
        return t.at>=start.getTimeInMillis()&&t.at<end.getTimeInMillis();
    }
    private void taskCard(Store.Task t){LinearLayout box=column();box.setPadding(dp(14),dp(12),dp(14),dp(14));box.setBackgroundColor(Color.rgb(43,45,47));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(12);cards.addView(box,params);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);box.addView(top);
        String date="Programar".equals(t.type)?new SimpleDateFormat("EEE d/M (h:mm a)",new Locale("es","US")).format(new java.util.Date(t.at)):"Al recibir SMS";
        FrameLayout emblem=new FrameLayout(this);top.addView(emblem,new LinearLayout.LayoutParams(dp(60),dp(54)));
        TextView clock=text("◷",28,Color.LTGRAY);clock.setGravity(Gravity.CENTER);clock.setPadding(0,0,0,0);clock.setBackground(round(Color.rgb(61,88,101),100));emblem.addView(clock,new FrameLayout.LayoutParams(dp(46),dp(46),Gravity.TOP|Gravity.LEFT));
        boolean wa="WhatsApp".equals(t.channel);View badge;
        if(wa){ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.ic_whatsapp_mark);mark.setPadding(dp(4),dp(4),dp(4),dp(4));mark.setBackground(round(Color.rgb(37,178,94),100));mark.setContentDescription("WhatsApp");badge=mark;}
        else{TextView txt=text("TXT",9,Color.WHITE);txt.setGravity(Gravity.CENTER);txt.setTypeface(null,Typeface.BOLD);txt.setPadding(0,0,0,0);txt.setBackground(round(Color.rgb(65,166,217),100));txt.setContentDescription("SMS");badge=txt;}
        FrameLayout.LayoutParams iconPlace=new FrameLayout.LayoutParams(dp(27),dp(27),Gravity.RIGHT|Gravity.BOTTOM);emblem.addView(badge,iconPlace);
        TextView chip=text(date,15,Color.WHITE);chip.setTypeface(null,Typeface.BOLD);chip.setBackgroundColor(Store.PENDING.equals(t.status)?Color.rgb(0,118,130):Color.rgb(92,98,101));top.addView(chip,new LinearLayout.LayoutParams(0,-2,1));
        TextView menu=text("⋮",25,Color.rgb(231,235,175));menu.setGravity(Gravity.CENTER);top.addView(menu,new LinearLayout.LayoutParams(dp(46),dp(46)));menu.setOnClickListener(v->taskMenu(t));
        TextView who=text((t.pinned?"📌  ":"")+(t.name.isEmpty()?t.recipient:t.name),18,Color.WHITE);who.setTypeface(null,Typeface.BOLD);box.addView(who);
        if("Programar".equals(t.type))box.addView(text("Destinatario: "+(Messaging.valid(t.recipient)?t.recipient:"Número pendiente de corregir"),14,Color.LTGRAY));
        TextView body=text(t.body,15,Color.LTGRAY);body.setMaxLines(2);body.setEllipsize(android.text.TextUtils.TruncateAt.END);box.addView(body);
        if(!t.error.isEmpty()){TextView warning=text(t.error,12,Color.rgb(255,215,128));box.addView(warning);}box.setOnClickListener(v->taskMenu(t));}
    private void taskMenu(Store.Task t){
        List<String> opts=new ArrayList<>();
        if("Programar".equals(t.type))opts.add("▷ Enviar ahora");
        opts.add("✎ Editar");opts.add("▣ Duplicar");opts.add(t.pinned?"♧ Desfijar":"♧ Fijar");
        if(Store.PENDING.equals(t.status)||"Acción necesaria".equals(t.status))opts.add("Ⅱ Pausar");
        else if(Store.PAUSED.equals(t.status)||Store.FAILED.equals(t.status))opts.add("▷ Reactivar");
        if(!Store.DONE.equals(t.status))opts.add("☑ Marcar completada");
        opts.add("▤ Eliminar");
        new AlertDialog.Builder(this).setTitle("Tarea #"+t.id).setItems(opts.toArray(new String[0]),(d,n)->{
            String a=opts.get(n);
            if(a.contains("Enviar ahora")){runNow(t);return;}
            if(a.contains("Editar")){edit(t);return;}
            if(a.contains("Duplicar")){duplicate(t);return;}
            if(a.contains("Fijar")||a.contains("Desfijar")){t.pinned=!t.pinned;db.save(t);home();return;}
            if(a.contains("Pausar")){Scheduler.cancel(this,t.id);t.status=Store.PAUSED;db.save(t);home();return;}
            if(a.contains("Reactivar")){reactivate(t);return;}
            if(a.contains("completada")){new AlertDialog.Builder(this).setTitle("Marcar completada").setMessage("La tarea dejará de ejecutarse. Esto no envía ningún mensaje.").setNegativeButton("Cancelar",null).setPositiveButton("Completar",(x,y)->{Scheduler.cancel(this,t.id);t.status=Store.DONE;t.error="Completada manualmente, sin envío";db.save(t);home();}).show();return;}
            new AlertDialog.Builder(this).setMessage("¿Eliminar esta tarea?").setNegativeButton("Cancelar",null).setPositiveButton("Eliminar",(x,y)->{Scheduler.cancel(this,t.id);db.delete(t.id);home();}).show();
        }).show();
    }
    private void duplicate(Store.Task t){
        if("Programar".equals(t.type)&&(!Messaging.valid(t.recipient)||t.body.trim().isEmpty())){alert("Corrige primero el número y mensaje de esta tarea.");return;}
        Store.Task copy=copy(t);copy.id=0;copy.sourceFingerprint="";copy.sourceId=0;copy.pinned=false;copy.status=Store.PENDING;copy.error="";
        if("Programar".equals(t.type)&&copy.at<=System.currentTimeMillis())copy.at=System.currentTimeMillis()+3600000;
        copy.id=db.save(copy);Scheduler.schedule(this,copy);home();
    }
    private void reactivate(Store.Task t){
        if("Programar".equals(t.type)&&(!Messaging.valid(t.recipient)||t.body.trim().isEmpty())){alert("Corrige primero el número y mensaje de esta tarea.");return;}
        t.status=Store.PENDING;t.error="";if("Programar".equals(t.type)&&t.at<=System.currentTimeMillis())t.at=System.currentTimeMillis()+3600000;
        db.save(t);Scheduler.schedule(this,t);home();
    }
    private void runNow(Store.Task t){
        if(!Messaging.valid(t.recipient)||t.body.trim().isEmpty()){alert("Corrige primero el número y mensaje de esta tarea.");return;}
        boolean consumesOccurrence="SMS".equals(t.channel)&&"Programar".equals(t.type)&&Store.PENDING.equals(t.status);
        String message="Enviar ahora a "+(t.name.isEmpty()?t.recipient:t.name+" ("+t.recipient+")")+"? "+("SMS".equals(t.channel)?"Puede generar cargos por SMS.":"Se abrirá WhatsApp y deberás pulsar Enviar.")+(consumesOccurrence?" Esta ocasión reemplazará el envío programado.":" La programación original conservará su fecha.");
        new AlertDialog.Builder(this).setTitle("Enviar ahora").setMessage(message).setNegativeButton("Cancelar",null).setPositiveButton("Continuar",(d,w)->{
            if("WhatsApp".equals(t.channel))openWhatsAppNow(t);
            else if("SMS".equals(t.channel)){if(checkSelfPermission(Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED){requestSmsPermission();alert("Concede el permiso SMS y vuelve a pulsar Enviar ahora.");return;}
                boolean pending="Programar".equals(t.type)&&Store.PENDING.equals(t.status);
                if(pending){Scheduler.cancel(this,t.id);if(!db.claimScheduledSms(t.id,t.at)){alert("Esta tarea ya se envió o está en proceso. Revisa Historial SMS.");return;}}
                Messaging.send(this,t,t.recipient,db.render(t),pending);
                alert(pending?"Se solicitó el envío ahora. Esta ocasión no volverá a enviarse a la hora programada; consulta Historial SMS.":"Se solicitó el envío. Consulta el resultado en Historial SMS.");}
            else alert("Este canal todavía no está disponible.");
        }).show();
    }
    private void openWhatsAppNow(Store.Task t){
        Uri uri=Uri.parse("https://wa.me/"+t.recipient.replace("+","")+"?text="+Uri.encode(db.render(t)));
        try{startActivity(new Intent(Intent.ACTION_VIEW,uri));alert("Pulsa Enviar en WhatsApp. La tarea programada conserva su fecha original.");}
        catch(Exception ex){alert("No se pudo abrir WhatsApp en este dispositivo.");}
    }
    private Store.Task copy(Store.Task t){Store.Task x=new Store.Task();x.id=t.id;x.type=t.type;x.channel=t.channel;x.recipient=t.recipient;x.name=t.name;x.body=t.body;x.at=t.at;x.repeat=t.repeat;x.status=t.status;x.keyword=t.keyword;x.start=t.start;x.end=t.end;x.cooldown=t.cooldown;x.error=t.error;x.pinned=t.pinned;x.sourceFingerprint=t.sourceFingerprint;x.sourceId=t.sourceId;x.clientId=t.clientId;return x;}
    private void chooseType(){
        Dialog dialog=new Dialog(this);LinearLayout sheet=column();sheet.setPadding(dp(18),dp(20),dp(18),dp(28));sheet.setBackground(round(Color.rgb(38,50,56),22));
        TextView title=text("Selecciona una tarea",23,Color.WHITE);title.setTypeface(null,Typeface.BOLD);sheet.addView(title);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);sheet.addView(row);
        String[] symbols={"◷","↶","↷"},labels={"Programar","Respuesta automática","Reenvío automático"},types={"Programar","Responder","Reenviar"};
        for(int n=0;n<3;n++){final int selected=n;LinearLayout item=column();item.setGravity(Gravity.CENTER);item.setPadding(dp(3),dp(18),dp(3),0);
            TextView icon=text(symbols[n],34,Color.BLACK);icon.setGravity(Gravity.CENTER);icon.setPadding(0,0,0,0);icon.setBackground(round(Color.rgb(173,192,223),100));LinearLayout.LayoutParams circle=new LinearLayout.LayoutParams(dp(62),dp(62));circle.gravity=Gravity.CENTER;item.addView(icon,circle);
            TextView label=text(labels[n],13,Color.WHITE);label.setGravity(Gravity.CENTER);item.addView(label);row.addView(item,new LinearLayout.LayoutParams(0,dp(132),1));item.setOnClickListener(v->{dialog.dismiss();editNew(types[selected]);});}
        dialog.setContentView(sheet);dialog.setCancelable(true);android.view.Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setGravity(Gravity.BOTTOM);window.setLayout(-1,-2);}dialog.show();window=dialog.getWindow();if(window!=null)window.setLayout(-1,-2);
    }
    private Spinner spinner(String[] choices){Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices);s.setAdapter(a);return s;}
    private int index(String[] a,String v){for(int n=0;n<a.length;n++)if(a[n].equals(v))return n;return 0;}
    private EditText field(LinearLayout layout,String label,String value,boolean multi){layout.addView(text(label,14,DARK));EditText e=new EditText(this);e.setSingleLine(!multi);e.setText(value);e.setTextSize(16);if(multi)e.setMinLines(3);layout.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    private void editNew(String type){Store.Task t=new Store.Task();t.type=type;t.channel="SMS";t.recipient="";t.name="";t.body="";t.at=System.currentTimeMillis()+3600000;t.repeat="Nunca";t.status=Store.PENDING;t.keyword="";t.start="";t.end="";t.cooldown=60;t.error="";edit(t);}
    private void edit(Store.Task t){selectedContactNames.clear();page("edit",(t.id==0?"Nueva tarea: ":"Editar: ")+t.type);content.addView(text("Los SMS pueden tener costo según tu operador. Usa solo contactos que aceptaron recibirlos.",14,Color.DKGRAY));
        String[] channels="Programar".equals(t.type)?new String[]{"SMS","WhatsApp"}:new String[]{"SMS"};content.addView(text("Canal",14,DARK));Spinner channel=spinner(channels);channel.setSelection(index(channels,t.channel));content.addView(channel);
        String recipientLabel="Reenviar".equals(t.type)?"Filtro de remitente (opcional, deja vacío para todos)":"Destinatarios: números con prefijo de país, separados por coma";
        EditText recipient=field(content,recipientLabel,t.recipient,false);
        if("Programar".equals(t.type))content.addView(button("Añadir desde contactos",()->chooseContacts(recipient)));
        String msgLabel="Reenviar".equals(t.type)?"Número de destino del reenvío":"Mensaje (usa {NOMBRE}, {PLAN}, {VENCE})";
        EditText body=field(content,msgLabel,t.body,!"Reenviar".equals(t.type));
        if(!"Reenviar".equals(t.type))content.addView(button("Usar plantilla",()->chooseTemplate(body)));
        if("Programar".equals(t.type))content.addView(button("Vista previa y partes de SMS",()->{String phone=recipient.getText().toString().split("[,;\\n]")[0].trim();String name=selectedContactNames.containsKey(phone)?selectedContactNames.get(phone):findName(phone);String preview=Messaging.render(body.getText().toString(),name);Store.Client client=db.clientForTask(t);if(client!=null)preview=preview.replace("{PLAN}",client.plan).replace("{VENCE}",new SimpleDateFormat("dd/MM/yyyy",Locale.getDefault()).format(new java.util.Date(client.expires)));int parts=android.telephony.SmsMessage.calculateLength(preview,false)[0];alert("Para: "+(name.isEmpty()?phone:name+" ("+phone+")")+"\n\n"+preview+"\n\n"+("SMS".equals(channel.getSelectedItem().toString())?"Partes SMS estimadas: "+parts+". Tu operador puede cobrar cada parte.":"WhatsApp: habrá que confirmar el envío manualmente."));}));
        Calendar when=Calendar.getInstance();when.setTimeInMillis(t.at);
        if("Programar".equals(t.type)){
            TextView date=text("Fecha y hora: "+DateFormat.getDateTimeInstance().format(when.getTime()),16,TEAL);content.addView(date);
            content.addView(button("Cambiar fecha y hora",()->new DatePickerDialog(this,(v,y,m,day)->{when.set(y,m,day);new TimePickerDialog(this,(tv,h,min)->{when.set(Calendar.HOUR_OF_DAY,h);when.set(Calendar.MINUTE,min);when.set(Calendar.SECOND,0);date.setText("Fecha y hora: "+DateFormat.getDateTimeInstance().format(when.getTime()));},when.get(Calendar.HOUR_OF_DAY),when.get(Calendar.MINUTE),false).show();},when.get(Calendar.YEAR),when.get(Calendar.MONTH),when.get(Calendar.DAY_OF_MONTH)).show()));
        }
        String[] repeats={"Nunca","Cada hora","Diario","Semanal","Mensual","Anual"};Spinner repeat=spinner(repeats);if("Programar".equals(t.type)){content.addView(text("Repetir",14,DARK));repeat.setSelection(index(repeats,t.repeat));content.addView(repeat);}
        EditText keyword=null,start=null,end=null,cooldown=null;
        if(!"Programar".equals(t.type)){keyword=field(content,"Palabra clave (opcional)",t.keyword,false);start=field(content,"Activo desde (HH:mm; opcional)",t.start,false);end=field(content,"Hasta (HH:mm; opcional)",t.end,false);cooldown=field(content,"Pausa por remitente en minutos",String.valueOf(t.cooldown),false);cooldown.setInputType(InputType.TYPE_CLASS_NUMBER);}
        final EditText key=keyword,from=start,to=end,wait=cooldown;
        content.addView(button("Guardar tarea",()->{
            String rec=recipient.getText().toString().trim(),msg=body.getText().toString().trim();String selected=channel.getSelectedItem().toString();
            if(msg.isEmpty()){alert("Escribe el mensaje o número de destino.");return;}
            if("Reenviar".equals(t.type)&&!Messaging.valid(msg)){alert("Número de reenvío inválido.");return;}
            if("Programar".equals(t.type)&&when.getTimeInMillis()<=System.currentTimeMillis()){alert("Selecciona una fecha futura.");return;}
            String[] phones="Programar".equals(t.type)?rec.split("[,;\\n]"):new String[]{rec};
            Set<String> unique=new HashSet<>();for(String raw:phones){String p=raw.trim();if(p.isEmpty()&&!"Programar".equals(t.type))continue;if(!Messaging.valid(p)){alert("Número inválido: "+p+". Usa solo dígitos y prefijo de país.");return;}unique.add(p);}
            if("Programar".equals(t.type)&&(unique.isEmpty()||unique.size()>50)){alert("Indica de 1 a 50 destinatarios.");return;}
            String k=key==null?"":key.getText().toString().trim(),a=from==null?"":from.getText().toString().trim(),b=to==null?"":to.getText().toString().trim();
            if((a.isEmpty()!=b.isEmpty())||(!a.isEmpty()&&(!a.matches("[0-2][0-9]:[0-5][0-9]")||!b.matches("[0-2][0-9]:[0-5][0-9]")))){alert("Usa ambas horas en formato HH:mm o deja ambas vacías.");return;}
            int parsedDelay=60;try{if(wait!=null)parsedDelay=Integer.parseInt(wait.getText().toString());}catch(Exception ex){alert("Pausa inválida.");return;}if(parsedDelay<1||parsedDelay>10080){alert("La pausa debe estar entre 1 y 10080 minutos.");return;}final int delay=parsedDelay;
            Runnable save=()->{if(t.id!=0)Scheduler.cancel(this,t.id);for(String p:unique){Store.Task x=copy(t);if(unique.size()>1)x.id=0;x.channel=selected;x.recipient=p;x.name=selectedContactNames.containsKey(p)?selectedContactNames.get(p):findName(p);x.body=msg;x.at=when.getTimeInMillis();x.repeat="Programar".equals(t.type)?repeat.getSelectedItem().toString():"Nunca";x.keyword=k;x.start=a;x.end=b;x.cooldown=delay;x.status=Store.PENDING;x.error="";x.id=db.save(x);if(selectedContactNames.containsKey(p))db.addContact(x.name,p);Scheduler.schedule(this,x);}if(unique.isEmpty()){Store.Task x=copy(t);x.channel=selected;x.recipient="";x.body=msg;x.keyword=k;x.start=a;x.end=b;x.cooldown=delay;x.status=Store.PENDING;x.error="";db.save(x);}home();requestNeededPermissions();};
            if(unique.size()>5)new AlertDialog.Builder(this).setTitle("Confirmar "+unique.size()+" mensajes").setMessage("Se crearán tareas separadas para "+unique.size()+" números. Los SMS podrían generar cargos.").setNegativeButton("Cancelar",null).setPositiveButton("Confirmar",(d,w)->save.run()).show();else save.run();
        }));content.addView(button("Cancelar",this::home));nav();}
    private String findName(String phone){for(String[] c:db.contacts())if(c[1].equals(phone))return c[0];return "";}
    private void chooseContacts(EditText dest){
        if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){pendingContactSelection=dest;requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},ASK_CONTACTS);return;}
        showContactsDialog(dest,loadSelectableContacts());
    }
    private String normalizePhone(String raw){if(raw==null)return "";String digits=raw.replaceAll("[^0-9]","");return raw.trim().startsWith("+")?"+"+digits:digits;}
    private List<String[]> loadSelectableContacts(){
        LinkedHashMap<String,String[]> entries=new LinkedHashMap<>();
        for(String[] person:db.contacts())if(Messaging.valid(person[1]))entries.put(person[1],person);
        if(checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED){
            String[] columns={ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER};
            try(Cursor c=getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,columns,null,null,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" ASC")){
                if(c!=null){int count=0;while(c.moveToNext()&&count++<10000){String phone=normalizePhone(c.getString(1));if(!Messaging.valid(phone))continue;
                    String name=c.getString(0);if(name==null||name.trim().isEmpty())name=phone;
                    String[] old=entries.get(phone);if(old==null||old[0].equals(phone))entries.put(phone,new String[]{name,phone});
                }}
            }catch(SecurityException denied){alert("Android no permitió leer la agenda. Revisa el permiso Contactos.");}
        }
        List<String[]> result=new ArrayList<>(entries.values());result.sort((a,b)->a[0].compareToIgnoreCase(b[0]));return result;
    }
    private void showContactsDialog(EditText dest,List<String[]> all){
        if(all.isEmpty()){alert("No hay números guardados en las tareas ni contactos importados. Añade uno desde Contactos o importa un CSV.");return;}
        boolean[] checked=new boolean[all.size()];List<Integer> visible=new ArrayList<>();
        LinearLayout panel=column();panel.setPadding(dp(12),0,dp(12),0);
        EditText query=new EditText(this);query.setSingleLine(true);query.setHint("Escribe nombre, letras o número");panel.addView(query);
        ListView list=new ListView(this);list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        panel.addView(list,new LinearLayout.LayoutParams(-1,dp(390)));
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_multiple_choice,new ArrayList<>());list.setAdapter(adapter);
        Runnable refresh=()->{String q=searchKey(query.getText().toString());visible.clear();adapter.clear();
            for(int n=0;n<all.size();n++){String[] person=all.get(n);if(searchKey(person[0]+" "+person[1]).contains(q)){visible.add(n);adapter.add(person[0]+" · "+person[1]);}}
            adapter.notifyDataSetChanged();for(int n=0;n<visible.size();n++)list.setItemChecked(n,checked[visible.get(n)]);
        };
        list.setOnItemClickListener((parent,view,pos,id)->checked[visible.get(pos)]=list.isItemChecked(pos));
        query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){refresh.run();}public void afterTextChanged(Editable e){}});
        refresh.run();
        new AlertDialog.Builder(this).setTitle("Seleccionar contactos ("+all.size()+")").setView(panel).setNegativeButton("Cancelar",null).setPositiveButton("Añadir",(d,w)->{
            StringBuilder b=new StringBuilder(dest.getText().toString().trim());Set<String> existing=new HashSet<>();for(String phone:b.toString().split("[,;\\n]"))existing.add(phone.trim());
            for(int n=0;n<all.size();n++)if(checked[n]){String phone=all.get(n)[1];selectedContactNames.put(phone,all.get(n)[0]);if(existing.add(phone)){if(b.length()>0)b.append(", ");b.append(phone);}}
            dest.setText(b.toString());
        }).show();
    }
    private String searchKey(String value){return java.text.Normalizer.normalize(value==null?"":value,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).trim();}
    private void chooseTemplate(EditText dest){List<String[]> all=db.templates();if(all.isEmpty()){alert("Crea una plantilla primero.");return;}String[] names=new String[all.size()];for(int n=0;n<all.size();n++)names[n]=all.get(n)[0];new AlertDialog.Builder(this).setTitle("Plantillas").setItems(names,(d,n)->dest.setText(all.get(n)[1])).show();}
    private void alert(String msg){new AlertDialog.Builder(this).setMessage(msg).setPositiveButton("Entendido",null).show();}
    private void openWhatsApp(Store.Task t){if(t==null||!"WhatsApp".equals(t.channel))return;Store.Client client=db.clientForTask(t);if(t.clientId>0&&(client==null||!client.consent||client.paid)){alert("El cliente ya no tiene recordatorios activos.");return;}String number=t.recipient.replace("+","");Uri uri=Uri.parse("https://wa.me/"+number+"?text="+Uri.encode(db.render(t)));Intent view=new Intent(Intent.ACTION_VIEW,uri);try{startActivity(view);new AlertDialog.Builder(this).setMessage("Confirma el envío dentro de WhatsApp. ¿Marcaste el mensaje como enviado?").setNegativeButton("Todavía no",null).setPositiveButton("Sí, enviado",(d,w)->{t.status=Store.DONE;db.save(t);db.log(t.id,t.recipient,Store.DONE,"Confirmado manualmente por el usuario");long next=Scheduler.next(t.at,t.repeat);if(next>0){t.at=next;t.status=Store.PENDING;db.save(t);Scheduler.schedule(this,t);}home();}).show();}catch(Exception ex){alert("No se pudo abrir WhatsApp en este dispositivo.");}}
    private void requestSmsPermission(){if(checkSelfPermission(Manifest.permission.SEND_SMS)==PackageManager.PERMISSION_GRANTED){alert("Permiso para enviar SMS: activado.");home();return;}requestPermissions(new String[]{Manifest.permission.SEND_SMS},ASK_SMS);}
    private void requestNeededPermissions(){
        if(checkSelfPermission(Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED){requestSmsPermission();return;}
        if(checkSelfPermission(Manifest.permission.RECEIVE_SMS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS},ASK_RECEIVE_SMS);return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},ASK_NOTICES);return;}
        alert("Permisos SMS y notificaciones: activados.");
    }
    private void openAppPermissionSettings(){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);
        boolean allowed=results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED;
        if(code==ASK_CONTACTS){EditText field=pendingContactSelection;pendingContactSelection=null;
            if(allowed&&field!=null)chooseContacts(field);
            else if(!allowed)new AlertDialog.Builder(this).setMessage("Para mostrar la agenda del teléfono, concede acceso a Contactos para SNAP Auto.").setNegativeButton("Ahora no",null).setNeutralButton("Usar guardados",(d,w)->{if(field!=null)showContactsDialog(field,db.contacts());}).setPositiveButton("Abrir ajustes",(d,w)->openAppPermissionSettings()).show();return;}
        if(code==ASK_SMS){lastSmsGranted=allowed;home();if(allowed)alert("Permiso para enviar SMS: activado. Ya no debe aparecer la alerta.");
            else new AlertDialog.Builder(this).setMessage("Android no concedió el permiso de envío SMS. Revísalo en los permisos de SNAP Auto. No se enviarán SMS programados hasta que esté activo.").setNegativeButton("Ahora no",null).setPositiveButton("Abrir ajustes",(d,w)->openAppPermissionSettings()).show();return;}
        if(code==ASK_RECEIVE_SMS||code==ASK_NOTICES)alert(allowed?"Permiso activado.":"Permiso rechazado. Puedes activarlo en Ajustes del teléfono → Apps → SNAP Auto → Permisos.");
    }
    private void clients(){page("clients","Clientes y vencimientos");content.addView(text("Los recordatorios se programan solo cuando registras consentimiento. Pago registrado detiene los recordatorios pendientes.",14,Color.DKGRAY));content.addView(button("+ Nuevo cliente",()->editClient(new Store.Client())));for(Store.Client client:db.clients()){String due=DateFormat.getDateInstance().format(new java.util.Date(client.expires));card(client.name+" · "+due,client.phone+" · "+client.plan+" · "+client.channel+"\n"+(client.paid?"Pago registrado":client.consent?"Recordatorios activos":"Sin consentimiento"),()->clientMenu(client));}nav();}
    private void clientMenu(Store.Client c){new AlertDialog.Builder(this).setTitle(c.name).setItems(new String[]{"Editar ficha y vencimiento","Registrar pago y detener recordatorios","Ver próximos recordatorios"},(d,n)->{if(n==0)editClient(c);else if(n==1)new AlertDialog.Builder(this).setMessage("¿Registrar pago de "+c.name+"? Se cancelarán sus recordatorios pendientes.").setNegativeButton("Cancelar",null).setPositiveButton("Registrar pago",(x,w)->{for(Store.Task t:db.clientPending(c.id))Scheduler.cancel(this,t.id);db.pauseClientPending(c.id);c.paid=true;db.saveClient(c);clients();}).show();else{StringBuilder list=new StringBuilder();for(Store.Task t:db.clientPending(c.id))list.append(DateFormat.getDateTimeInstance().format(new java.util.Date(t.at))).append(" · ").append(t.channel).append("\n");alert(list.length()==0?"No hay recordatorios pendientes.":list.toString());}}).show();}
    private void editClient(Store.Client original){Store.Client v=new Store.Client();v.id=original.id;v.name=original.name;v.phone=original.phone;v.plan=original.plan;v.expires=original.expires>0?original.expires:System.currentTimeMillis()+30L*86400000;v.channel=original.channel;v.consent=original.consent;v.paid=original.paid;v.days=original.days;v.message=original.message;
        page("client_edit",v.id==0?"Nuevo cliente":"Editar cliente");EditText name=field(content,"Nombre",v.name,false),phone=field(content,"Teléfono con código de país",v.phone,false),plan=field(content,"Plan",v.plan,false);
        content.addView(text("Canal de recordatorio",14,DARK));Spinner channel=spinner(new String[]{"SMS","WhatsApp"});channel.setSelection(index(new String[]{"SMS","WhatsApp"},v.channel));content.addView(channel);
        Calendar date=Calendar.getInstance();date.setTimeInMillis(v.expires);TextView expiry=text("Vence: "+DateFormat.getDateInstance().format(date.getTime()),16,TEAL);content.addView(expiry);
        content.addView(button("Cambiar vencimiento",()->new DatePickerDialog(this,(picker,y,m,d)->{date.set(y,m,d,10,0,0);date.set(Calendar.MILLISECOND,0);expiry.setText("Vence: "+DateFormat.getDateInstance().format(date.getTime()));},date.get(Calendar.YEAR),date.get(Calendar.MONTH),date.get(Calendar.DAY_OF_MONTH)).show()));
        EditText days=field(content,"Avisar días antes (0 = día del vencimiento, máximo 30)",v.days,false);
        EditText message=field(content,"Mensaje · {NOMBRE}, {PLAN}, {VENCE}",v.message.isEmpty()?"Hola {NOMBRE}, tu plan {PLAN} vence el {VENCE}. Si deseas renovar, responde a este mensaje.":v.message,true);
        content.addView(button("Usar plantilla",()->chooseTemplate(message)));
        CheckBox consent=new CheckBox(this);consent.setText("Este cliente aceptó recibir recordatorios");consent.setChecked(v.consent);content.addView(consent);
        CheckBox paid=new CheckBox(this);paid.setText("Pago registrado (sin recordatorios)");paid.setChecked(v.paid);content.addView(paid);
        content.addView(button("Vista previa",()->{Store.Task preview=new Store.Task();preview.name=name.getText().toString().trim();preview.body=message.getText().toString();String result=Messaging.render(preview.body,preview.name).replace("{PLAN}",plan.getText().toString().trim()).replace("{VENCE}",new SimpleDateFormat("dd/MM/yyyy",Locale.getDefault()).format(date.getTime()));alert(result);}));
        content.addView(button("Guardar cliente y programar",()->{
            v.name=name.getText().toString().trim();v.phone=phone.getText().toString().trim();v.plan=plan.getText().toString().trim();v.expires=date.getTimeInMillis();v.channel=channel.getSelectedItem().toString();v.consent=consent.isChecked();v.paid=paid.isChecked();v.message=message.getText().toString().trim();v.days=days.getText().toString().trim();
            if(v.name.isEmpty()||!Messaging.valid(v.phone)||v.message.isEmpty()){alert("Escribe nombre, número válido y mensaje.");return;}
            Set<Integer> offsets=new HashSet<>();try{for(String entry:v.days.split(",")){int n=Integer.parseInt(entry.trim());if(n<0||n>30)throw new NumberFormatException();offsets.add(n);}if(offsets.isEmpty()||offsets.size()>6)throw new NumberFormatException();}catch(NumberFormatException ex){alert("Escribe de 1 a 6 días únicos entre 0 y 30, separados por coma.");return;}
            for(Store.Client other:db.clients())if(other.id!=v.id&&other.phone.replaceAll("\\D","").equals(v.phone.replaceAll("\\D",""))){alert("Este número ya pertenece a otra ficha: "+other.name);return;}
            if(v.consent&&!v.paid&&v.expires<System.currentTimeMillis()){alert("El vencimiento ya pasó. Cambia la fecha o registra el pago.");return;}
            int planned=0;for(int offset:offsets){Calendar run=(Calendar)date.clone();run.add(Calendar.DAY_OF_YEAR,-offset);if(run.getTimeInMillis()>System.currentTimeMillis())planned++;}
            final int count=planned;new AlertDialog.Builder(this).setTitle("Confirmar recordatorios").setMessage("Se programarán "+count+" recordatorios para "+v.name+" por "+v.channel+". Los SMS podrían generar cargos; WhatsApp exige confirmar el envío. Se cancelarán los recordatorios anteriores de esta ficha. ¿Guardar?").setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(d,w)->{try{
                if(v.id>0){for(Store.Task old:db.clientPending(v.id))Scheduler.cancel(this,old.id);db.pauseClientPending(v.id);}v.id=db.saveClient(v);db.addContact(v.name,v.phone);
                if(v.consent&&!v.paid)for(int offset:offsets){Calendar run=(Calendar)date.clone();run.add(Calendar.DAY_OF_YEAR,-offset);if(run.getTimeInMillis()<=System.currentTimeMillis())continue;Store.Task task=new Store.Task();task.clientId=v.id;task.type="Programar";task.channel=v.channel;task.recipient=v.phone;task.name=v.name;task.body=v.message;task.at=run.getTimeInMillis();task.repeat="Nunca";task.status=Store.PENDING;task.keyword="";task.start="";task.end="";task.error="";task.cooldown=60;task.id=db.save(task);Scheduler.schedule(this,task);}
                clients();if(count>0)requestNeededPermissions();
            }catch(Exception ex){alert("No se pudo guardar el cliente: "+ex.getMessage());}}).show();
        }));content.addView(button("Regresar sin guardar",this::goBack));nav();}
    private void contacts(){page("contacts","Contactos");content.addView(button("Clientes y vencimientos",this::clients));content.addView(button("Importar CSV",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("text/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMPORT);}));content.addView(text("CSV con columnas name,phone o nombre,telefono. Exporta Excel como CSV UTF-8.",14,Color.DKGRAY));content.addView(button("Añadir contacto",()->{LinearLayout box=column();EditText name=field(box,"Nombre","",false),phone=field(box,"Teléfono con prefijo de país","",false);new AlertDialog.Builder(this).setTitle("Contacto").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(d,w)->{if(Messaging.valid(phone.getText().toString().trim()))db.addContact(name.getText().toString().trim(),phone.getText().toString().trim());contacts();}).show();}));for(String[] c:db.contacts())card(c[0],c[1],null);nav();}
    private void templates(){page("templates","Plantillas");content.addView(button("+ Nueva plantilla",()->{LinearLayout box=column();EditText title=field(box,"Título","",false),body=field(box,"Mensaje con {NOMBRE}","",true);new AlertDialog.Builder(this).setTitle("Nueva plantilla").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(d,w)->{if(!title.getText().toString().trim().isEmpty()&&!body.getText().toString().trim().isEmpty())db.addTemplate(title.getText().toString().trim(),body.getText().toString().trim());templates();}).show();}));for(String[] t:db.templates())card(t[0],t[1],null);nav();}
    private void history(){page("history","Control de envíos");String[] counts=db.deliveryCounts();long next=Long.MAX_VALUE;for(Store.Task t:db.tasks())if(Store.PENDING.equals(t.status)&&"Programar".equals(t.type)&&t.at<next)next=t.at;
        android.telephony.TelephonyManager phone=(android.telephony.TelephonyManager)getSystemService(TELEPHONY_SERVICE);boolean sim=phone!=null&&phone.getSimState()==android.telephony.TelephonyManager.SIM_STATE_READY;
        card("Estado de programación","Pendientes: "+counts[0]+" · Aceptados por red: "+counts[1]+" · Fallidos: "+counts[2]+" · Otros: "+counts[3]+"\nPróximo: "+(next==Long.MAX_VALUE?"Ninguno":DateFormat.getDateTimeInstance().format(new java.util.Date(next)))+"\nAlarmas puntuales: "+(exactAlarmsAllowed()?"Sí":"No")+" · Permiso SMS: "+(checkSelfPermission(Manifest.permission.SEND_SMS)==PackageManager.PERMISSION_GRANTED?"Sí":"No")+" · SIM: "+(sim?"Lista":"Revisar"),null);
        content.addView(text("«Enviado» significa aceptado por la red móvil; no confirma entrega o lectura. WhatsApp requiere confirmación manual.",14,Color.DKGRAY));for(String[] h:db.history())card(h[1]+" · "+h[0],DateFormat.getDateTimeInstance().format(Long.parseLong(h[3]))+"\n"+h[2],null);content.addView(button("Volver",this::goBack));nav();}
    private void settings(){page("settings","Ajustes y permisos");card("Estado de permisos","Alarmas puntuales: "+(exactAlarmsAllowed()?"ACTIVADAS":"PENDIENTES")+"\nEnviar SMS: "+(checkSelfPermission(Manifest.permission.SEND_SMS)==PackageManager.PERMISSION_GRANTED?"ACTIVADO":"PENDIENTE"),null);content.addView(button("Solicitar permiso de envío SMS",this::requestSmsPermission));content.addView(button("Otros permisos: recibir SMS y notificaciones",this::requestNeededPermissions));content.addView(button("Permitir alarmas exactas",this::requestExactAlarms));content.addView(button("Crear backup .sqlite3",()->{String name="snap_auto_backup_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new java.util.Date())+".sqlite3";Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/x-sqlite3").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,SQLITE_EXPORT);}));content.addView(button("Elegir carpeta de copias diarias",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,BACKUP_FOLDER);}));content.addView(button("Crear copia diaria ahora",()->{new Thread(()->{try{AutoBackup.backup(this);runOnUiThread(()->{settings();alert("Copia guardada en tu carpeta elegida.");});}catch(Exception ex){runOnUiThread(()->alert("La copia falló: "+ex.getMessage()));}},"manual-backup").start();}));content.addView(button("Desactivar copias diarias",()->new AlertDialog.Builder(this).setMessage("Se dejarán de crear copias automáticas. Las existentes seguirán en tu carpeta.").setNegativeButton("Cancelar",null).setPositiveButton("Desactivar",(d,w)->{getSharedPreferences("backup_schedule",MODE_PRIVATE).edit().remove("tree").apply();AutoBackup.schedule(this);settings();}).show()));content.addView(button("Restaurar o importar .sqlite3",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,SQLITE_RESTORE);}));content.addView(button("Exportar copia JSON",()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"snap-auto-backup.json");startActivityForResult(i,EXPORT);}));content.addView(button("Restaurar copia JSON",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,RESTORE);}));long lastBackup=AutoBackup.prefs(this).getLong("last_ok",0);card("Copias diarias",AutoBackup.configured(this)?"Activas · últimas 7 copias rotativas en la carpeta elegida. Última: "+(lastBackup==0?"Aún no se ha creado":DateFormat.getDateTimeInstance().format(new java.util.Date(lastBackup)))+"\n"+AutoBackup.prefs(this).getString("last_error",""):"Sin carpeta elegida. Las copias manuales siguen disponibles.",null);if(db.legacyCount()>0)content.addView(button("Ver respuestas de Auto Text archivadas",this::legacyArchive));card("Privacidad","Los contactos y tareas se guardan en el teléfono. La exportación manual crea un archivo que tú eliges.",null);card("WhatsApp","El aviso abre un chat con el texto listo. Debes pulsar Enviar. No hay acceso a mensajes entrantes de WhatsApp.",null);nav();}
    private void legacyArchive(){page("archive","Archivo de Auto Text");card("Registros conservados","Se guardaron "+db.legacyCount()+" tareas originales con todos sus campos en el backup SQLite de SNAP Auto. Estas respuestas no pueden ejecutarse automáticamente.",null);for(String[] row:db.unsupportedAutoText())card(row[0].isEmpty()?row[1]:row[0],"Estado: "+row[4]+" · "+row[1]+"\n"+row[2],()->alert("Palabras clave originales:\n"+row[3]));content.addView(button("Volver",this::settings));nav();}
    @Override protected void onActivityResult(int code,int result,Intent data){super.onActivityResult(code,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;try{if(code==BACKUP_FOLDER){Uri tree=data.getData();getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);AutoBackup.prefs(this).edit().putString("tree",tree.toString()).apply();AutoBackup.schedule(this);settings();alert("Carpeta guardada. La primera copia se creará mañana a las 3:00 a. m. Puedes crear una ahora desde Ajustes.");}else if(code==IMPORT)importCsv(data.getData());else if(code==EXPORT)exportJson(data.getData());else if(code==RESTORE)restoreJson(data.getData());else if(code==SQLITE_EXPORT)exportSqlite(data.getData());else if(code==SQLITE_RESTORE)restoreSqlite(data.getData());}catch(Exception e){alert("No se pudo procesar el archivo: "+e.getMessage());}}
    private void exportSqlite(Uri uri)throws Exception {File snapshot=db.snapshot();try(InputStream in=new FileInputStream(snapshot);OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IllegalArgumentException("No se pudo abrir el destino");Store.copy(in,out);alert("Backup SQLite guardado. Consérvalo en un lugar seguro.");}finally{snapshot.delete();}}
    private void restoreSqlite(Uri uri)throws Exception {File staged=File.createTempFile("snap-auto-restore-",".sqlite3",getCacheDir());try{try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(staged)){if(in==null)throw new IllegalArgumentException("No se pudo abrir el archivo");byte[] bytes=new byte[65536];int n;long count=0;while((n=in.read(bytes))!=-1){count+=n;if(count>100_000_000)throw new IllegalArgumentException("Archivo demasiado grande");out.write(bytes,0,n);}}
            boolean own=false;
            try(android.database.sqlite.SQLiteDatabase check=android.database.sqlite.SQLiteDatabase.openDatabase(staged.getAbsolutePath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY)){try{Store.validate(check);own=true;}catch(IllegalArgumentException otherApp){/* Try the observed Auto Text schema below. */}}
            if(!own){AutoTextImporter.Summary preview=AutoTextImporter.preview(staged);
                String summary="Encontré "+preview.originals+" tareas originales: "+preview.sms+" SMS, "+preview.whatsapp+" WhatsApp y "+preview.replies+" respuestas de WhatsApp. También "+preview.templates+" plantillas.\n\nLas tareas futuras con número válido quedarán Pendientes y con alarma programada. Los SMS se enviarán desde este teléfono si tiene permisos y servicio móvil. WhatsApp mostrará un aviso para que confirmes el envío. "+preview.missing+" tareas no tienen un número utilizable y quedarán pausadas. ¿Importar?";
                new AlertDialog.Builder(this).setTitle("Importar Auto Text").setMessage(summary).setNegativeButton("Cancelar",(d,w)->staged.delete()).setPositiveButton("Importar",(d,w)->{try{AutoTextImporter.Summary imported=AutoTextImporter.importFile(db,staged);for(Store.Task t:db.tasks())if(Store.PENDING.equals(t.status)&&imported.fingerprint.equals(t.sourceFingerprint))Scheduler.schedule(this,t);filter=Store.PENDING;home();requestNeededPermissions();alert("Importación completa: "+imported.created+" tareas visibles. Las futuras válidas están Pendientes. WhatsApp requiere confirmar el envío cuando llegue el aviso.");}catch(Exception ex){alert("No se pudo importar: "+ex.getMessage());}finally{staged.delete();}}).setOnCancelListener(d->staged.delete()).show();return;}
            int taskCount=0,contactCount=0,clientCount=0;try(android.database.sqlite.SQLiteDatabase previewDb=android.database.sqlite.SQLiteDatabase.openDatabase(staged.getAbsolutePath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY)){try(Cursor c=previewDb.rawQuery("SELECT COUNT(*) FROM tasks",null)){if(c.moveToFirst())taskCount=c.getInt(0);}try(Cursor c=previewDb.rawQuery("SELECT COUNT(*) FROM contacts",null)){if(c.moveToFirst())contactCount=c.getInt(0);}try(Cursor c=previewDb.rawQuery("SELECT COUNT(*) FROM clients",null)){if(c.moveToFirst())clientCount=c.getInt(0);}catch(android.database.sqlite.SQLiteException older){/* Backups 1.10 and older do not contain client profiles. */}}new AlertDialog.Builder(this).setTitle("Restaurar backup SQLite").setMessage("El archivo contiene "+taskCount+" tareas, "+contactCount+" contactos y "+clientCount+" clientes.\n\nEsto reemplazará las tareas, contactos, clientes, plantillas e historial actuales. Las tareas futuras válidas se reprogramarán. ¿Continuar?").setNegativeButton("Cancelar",(d,w)->staged.delete()).setPositiveButton("Restaurar",(d,w)->{try{for(Store.Task t:db.tasks())Scheduler.cancel(this,t.id);int count=db.restore(staged);for(Store.Task t:db.tasks())if(Store.PENDING.equals(t.status))Scheduler.schedule(this,t);filter=Store.PENDING;home();requestNeededPermissions();alert("Restauración completa: "+count+" tareas. Las futuras pendientes ya tienen alarma.");}catch(Exception ex){for(Store.Task old:db.tasks())if(Store.PENDING.equals(old.status))Scheduler.schedule(this,old);alert("No se pudo restaurar: "+ex.getMessage());}finally{staged.delete();}}).setOnCancelListener(d->staged.delete()).show();
        }catch(Exception ex){staged.delete();throw ex;}}
    private void importCsv(Uri uri)throws Exception {
        List<String[]> accepted=new ArrayList<>();Set<String> seen=new HashSet<>();for(String[] c:db.contacts())seen.add(c[1].replaceAll("\\D",""));
        int invalid=0,duplicate=0,blank=0,rows=0;char separator=',';
        try(BufferedReader input=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri),StandardCharsets.UTF_8))){
            String header=input.readLine();if(header==null)throw new IllegalArgumentException("CSV vacío");separator=header.indexOf(';')>=0&&header.indexOf(',')<0?';':',';
            List<String> titles=csv(header,separator);int nameCol=-1,phoneCol=-1;
            for(int n=0;n<titles.size();n++){String key=searchKey(titles.get(n).replace("\ufeff",""));if(key.equals("name")||key.equals("nombre"))nameCol=n;if(key.equals("phone")||key.equals("telefono")||key.equals("mobile"))phoneCol=n;}
            if(nameCol<0||phoneCol<0)throw new IllegalArgumentException("Faltan columnas name,phone o nombre,telefono");
            String line;while((line=input.readLine())!=null){if(++rows>5000)throw new IllegalArgumentException("Máximo 5000 filas por importación");
                // A quoted message can include line breaks; read through its closing quote.
                int quotes=0;for(int k=0;k<line.length();k++)if(line.charAt(k)=='"')quotes++;
                while(quotes%2!=0){String continued=input.readLine();if(continued==null)throw new IllegalArgumentException("Comillas sin cerrar en CSV");line+="\n"+continued;if(line.length()>10000)throw new IllegalArgumentException("Fila demasiado larga");for(int k=0;k<continued.length();k++)if(continued.charAt(k)=='"')quotes++;}
                List<String> cells=csv(line,separator);if(cells.size()<=Math.max(nameCol,phoneCol)){invalid++;continue;}
                String name=cells.get(nameCol).trim(),phone=cells.get(phoneCol).trim().replaceAll("[ ()-]","");
                if(name.isEmpty()&&phone.isEmpty()){blank++;continue;}
                if(name.isEmpty()||!Messaging.valid(phone)){invalid++;continue;}
                if(!seen.add(phone.replaceAll("\\D",""))){duplicate++;continue;}
                accepted.add(new String[]{name,phone});
            }
        }
        String summary="Nuevos válidos: "+accepted.size()+"\nDuplicados (archivo o agenda): "+duplicate+"\nInválidos: "+invalid+"\nVacíos: "+blank+"\n\nNo se crearán tareas ni se enviarán mensajes.";
        new AlertDialog.Builder(this).setTitle("Revisar importación CSV").setMessage(summary).setNegativeButton("Cancelar",null).setPositiveButton("Importar",(d,w)->{for(String[] c:accepted)db.addContact(c[0],c[1]);contacts();alert(accepted.size()+" contactos importados.");}).show();
    }
    private List<String> csv(String line){return csv(line,line.indexOf(';')>=0&&line.indexOf(',')<0?';':',');}
    private List<String> csv(String line,char sep){List<String> out=new ArrayList<>();StringBuilder cur=new StringBuilder();boolean quoted=false;for(int n=0;n<line.length();n++){char c=line.charAt(n);if(c=='"'){if(quoted&&n+1<line.length()&&line.charAt(n+1)=='"'){cur.append('"');n++;}else quoted=!quoted;}else if(c==sep&&!quoted){out.add(cur.toString());cur.setLength(0);}else cur.append(c);}out.add(cur.toString());return out;}
    private void exportJson(Uri uri)throws Exception {org.json.JSONObject data=new org.json.JSONObject();org.json.JSONArray tasks=new org.json.JSONArray(),contacts=new org.json.JSONArray(),templates=new org.json.JSONArray(),clients=new org.json.JSONArray();for(Store.Task t:db.tasks()){org.json.JSONObject o=new org.json.JSONObject();o.put("type",t.type);o.put("channel",t.channel);o.put("recipient",t.recipient);o.put("name",t.name);o.put("body",t.body);o.put("at",t.at);o.put("repeat",t.repeat);o.put("status",t.status);o.put("keyword",t.keyword);o.put("start",t.start);o.put("end",t.end);o.put("cooldown",t.cooldown);o.put("pinned",t.pinned);o.put("client_id",t.clientId);tasks.put(o);}for(Store.Client c:db.clients()){org.json.JSONObject x=new org.json.JSONObject();x.put("name",c.name).put("phone",c.phone).put("plan",c.plan).put("expires",c.expires).put("channel",c.channel).put("consent",c.consent).put("paid",c.paid).put("days",c.days).put("message",c.message);clients.put(x);}for(String[] c:db.contacts())contacts.put(new org.json.JSONArray().put(c[0]).put(c[1]));for(String[] t:db.templates())templates.put(new org.json.JSONArray().put(t[0]).put(t[1]));data.put("format",1).put("tasks",tasks).put("contacts",contacts).put("templates",templates).put("clients",clients);try(OutputStreamWriter out=new OutputStreamWriter(getContentResolver().openOutputStream(uri),StandardCharsets.UTF_8)){out.write(data.toString(2));}alert("Copia exportada.");}
    private void restoreJson(Uri uri)throws Exception {StringBuilder raw=new StringBuilder();try(BufferedReader in=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri),StandardCharsets.UTF_8))){String line;while((line=in.readLine())!=null){raw.append(line);if(raw.length()>5_000_000)throw new IllegalArgumentException("Archivo demasiado grande");}}org.json.JSONObject doc=new org.json.JSONObject(raw.toString());if(doc.getInt("format")!=1)throw new IllegalArgumentException("Versión no compatible");org.json.JSONArray ts=doc.getJSONArray("tasks"),cs=doc.getJSONArray("contacts"),ms=doc.getJSONArray("templates"),customer=doc.optJSONArray("clients");if(customer==null)customer=new org.json.JSONArray();final org.json.JSONArray restoredClients=customer;if(ts.length()>5000||cs.length()>5000||ms.length()>5000||restoredClients.length()>5000)throw new IllegalArgumentException("Demasiados registros");new AlertDialog.Builder(this).setTitle("Restaurar copia").setMessage("Se añadirán "+ts.length()+" tareas, "+cs.length()+" contactos y "+ms.length()+" plantillas y "+restoredClients.length()+" fichas de clientes. Las tareas futuras quedarán pausadas para revisarlas antes de activarlas.").setNegativeButton("Cancelar",null).setPositiveButton("Restaurar",(d,w)->{try{for(int n=0;n<cs.length();n++){org.json.JSONArray c=cs.getJSONArray(n);if(Messaging.valid(c.getString(1)))db.addContact(c.getString(0),c.getString(1));}for(int n=0;n<ms.length();n++){org.json.JSONArray m=ms.getJSONArray(n);db.addTemplate(m.getString(0),m.getString(1));}for(int n=0;n<restoredClients.length();n++){org.json.JSONObject x=restoredClients.getJSONObject(n);Store.Client c=new Store.Client();c.name=x.optString("name");c.phone=x.optString("phone");c.plan=x.optString("plan");c.expires=x.optLong("expires");c.channel=x.optString("channel","SMS");c.consent=x.optBoolean("consent");c.paid=x.optBoolean("paid");c.days=x.optString("days","3,1,0");c.message=x.optString("message");boolean existing=false;for(Store.Client old:db.clients())if(old.phone.equals(c.phone))existing=true;if(!existing&&Messaging.valid(c.phone))db.saveClient(c);}for(int n=0;n<ts.length();n++){org.json.JSONObject o=ts.getJSONObject(n);Store.Task t=new Store.Task();t.type=o.getString("type");t.channel=o.getString("channel");t.recipient=o.getString("recipient");t.name=o.optString("name");t.body=o.getString("body");t.at=o.getLong("at");t.repeat=o.optString("repeat","Nunca");t.status=Store.PAUSED;t.keyword=o.optString("keyword");t.start=o.optString("start");t.end=o.optString("end");t.cooldown=o.optInt("cooldown",60);t.pinned=o.optBoolean("pinned",false);t.error="";if(o.optLong("client_id",0)>0)for(Store.Client customerProfile:db.clients())if(customerProfile.phone.equals(t.recipient)){t.clientId=customerProfile.id;break;}db.save(t);}home();alert("Copia añadida. Revisa y reactiva las tareas que necesites.");}catch(Exception ex){alert("Restauración incompleta: "+ex.getMessage());}}).show();}
}

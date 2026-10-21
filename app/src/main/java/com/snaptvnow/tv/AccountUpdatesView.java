package com.snaptvnow.tv;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/** The installed label comes from this device, never from the published release. */
final class AccountUpdatesView extends LinearLayout {
  private static final int NAVY=0xff071729,PANEL=0xff10283d,CYAN=0xff29dce8,WHITE=0xffffffff,MUTED=0xffb5c9d3;
  private final Activity activity;
  private final TextView check,status,name,details,download;
  private final LinearLayout offer;
  private UpdateChecker.Task task;
  private int generation;
  AccountUpdatesView(Activity activity){
    super(activity);this.activity=activity;setOrientation(VERTICAL);setPadding(dp(12),0,dp(12),dp(12));
    addView(label("Versión instalada",18,WHITE,true));
    TextView version=label(UpdateChecker.installedName(activity),34,WHITE,true);version.setPadding(0,dp(6),0,dp(8));addView(version);
    addView(label(BoxPanel.name(),14,MUTED,false));
    check=button("Buscar actualizaciones",this::checkNow);LayoutParams checkParams=new LayoutParams(-1,-2);checkParams.topMargin=dp(18);addView(check,checkParams);
    status=label("Comprueba si hay una nueva versión para tu dispositivo.",13,MUTED,false);status.setPadding(0,dp(12),0,dp(12));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);addView(status);
    offer=new LinearLayout(activity);offer.setOrientation(VERTICAL);offer.setPadding(dp(16),dp(16),dp(16),dp(16));
    GradientDrawable card=background(PANEL);card.setStroke(dp(1),CYAN);offer.setBackground(card);offer.setVisibility(GONE);addView(offer,new LayoutParams(-1,-2));
    offer.addView(label("Actualización disponible",14,CYAN,true));
    name=label("",21,WHITE,true);name.setPadding(0,dp(10),0,dp(8));offer.addView(name);
    details=label("",14,MUTED,false);offer.addView(details);
    download=button("Descargar e instalar",()->{});LayoutParams downloadParams=new LayoutParams(-1,-2);downloadParams.topMargin=dp(16);offer.addView(download,downloadParams);
    TextView note=label("Confirma la instalación en tu dispositivo.",12,MUTED,false);note.setPadding(0,dp(12),0,0);offer.addView(note);
  }
  void checkNow(){
    if(task!=null)return;
    final int current=++generation;check.setEnabled(false);check.setText("Buscando actualizaciones…");offer.setVisibility(GONE);status.setText("Consultando la versión publicada…");
    task=UpdateChecker.manual(activity,(update,error)->{
      if(current!=generation)return;task=null;check.setEnabled(true);check.setText("Buscar actualizaciones");
      if(error!=null){status.setText(error);return;}
      if(update==null){status.setText("Tu app está actualizada. No hay una versión más reciente publicada.");return;}
      status.setText("Nueva versión disponible.");name.setText("Nueva versión "+update.name);
      details.setText((update.size>0?String.format(Locale.getDefault(),"Tamaño: %.1f MB\n",update.size/1048576d):"")+update.notes);
      download.setOnClickListener(view->update.open(activity));offer.setVisibility(VISIBLE);
    });
  }
  @Override protected void onDetachedFromWindow(){generation++;if(task!=null){task.cancel();task=null;}super.onDetachedFromWindow();}
  private TextView label(String value,int size,int color,boolean bold){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
  private TextView button(String value,Runnable action){
    TextView v=label(value,15,NAVY,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(12),dp(16),dp(12),dp(16));v.setMinHeight(dp(54));v.setBackground(background(CYAN));v.setFocusable(true);v.setClickable(true);v.setOnClickListener(view->action.run());
    v.setOnFocusChangeListener((view,focused)->{GradientDrawable b=background(CYAN);b.setStroke(dp(focused?3:0),WHITE);v.setBackground(b);});return v;
  }
  private GradientDrawable background(int color){GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(12));return b;}
  private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}

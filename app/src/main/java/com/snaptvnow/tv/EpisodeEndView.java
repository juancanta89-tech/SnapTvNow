package com.snaptvnow.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Phone and remote-friendly end card; no transport keys reach the ended video underneath. */
final class EpisodeEndView extends LinearLayout {
  private static final int NAVY=0xff071729, CYAN=0xff29dce8, WHITE=0xffffffff;
  private final TextView chapter, status, next, cancel, automatic, episodes;
  EpisodeEndView(Context context, Runnable playNext, Runnable stop, Runnable toggle,
      Runnable returnToEpisodes, Runnable retry) {
    super(context);
    this.playNext=playNext;
    setOrientation(VERTICAL); setPadding(dp(20),dp(16),dp(20),dp(16));
    GradientDrawable background=new GradientDrawable();background.setColor(0xff10283d);
    background.setCornerRadius(dp(14));setBackground(background);
    addView(label("Capítulo terminado",23,true),new LayoutParams(-1,-2));
    chapter=label("",16,true);chapter.setMaxLines(2);
    LayoutParams titleParams=new LayoutParams(-1,-2);titleParams.topMargin=dp(8);addView(chapter,titleParams);
    status=label("",15,false);status.setMinHeight(dp(40));addView(status,new LayoutParams(-1,-2));
    next=button("Ver siguiente ahora",playNext);cancel=button("Detener automático",stop);
    automatic=button("",toggle);episodes=button("Volver a episodios",returnToEpisodes);
    next.setTag("episode_next");cancel.setTag("episode_cancel");
    automatic.setTag("episode_auto");episodes.setTag("episode_list");
    status.setTag("episode_status");
    View[] buttons={next,cancel,automatic,episodes};
    for(int i=0;i<buttons.length;i++){
      if(i>0)buttons[i].setNextFocusUpId(buttons[i-1].getId());
      if(i+1<buttons.length)buttons[i].setNextFocusDownId(buttons[i+1].getId());
    }
  }
  void update(EpisodePlaybackController.State state, Runnable retry) {
    chapter.setText(state.next==null?"":state.next.title);
    next.setVisibility(state.next==null&&!state.failed?GONE:VISIBLE);
    next.setText(state.failed?"Reintentar carga de capítulos":"Ver siguiente ahora");
    next.setEnabled(!state.loading&&!state.advancing&&(state.next!=null||state.failed));
    next.setOnClickListener(v->{if(state.failed)retry.run();else playNext.run();});
    boolean canCancel=state.seconds>0&&!state.advancing;
    boolean moveFocus=cancel.hasFocus()&&!canCancel;
    cancel.setVisibility(canCancel?VISIBLE:GONE);
    automatic.setText("Reproducción automática: "+(state.automatic?"activada":"desactivada"));
    automatic.setEnabled(!state.advancing);
    episodes.setEnabled(!state.advancing);
    if(state.loading)status.setText("Cargando el siguiente capítulo…");
    else if(state.failed)status.setText("No se pudieron cargar los capítulos.");
    else if(state.next==null)status.setText("No hay más capítulos disponibles.");
    else if(state.advancing)status.setText("Abriendo el siguiente capítulo…");
    else if(state.seconds>0)status.setText("Siguiente capítulo en "+state.seconds+" s");
    else if(state.cancelled)status.setText("Reproducción automática detenida. Puedes seguir cuando quieras.");
    else status.setText("Elige cuándo reproducir el siguiente capítulo.");
    if(moveFocus){if(next.getVisibility()==VISIBLE)next.requestFocus();else episodes.requestFocus();}
  }
  private final Runnable playNext;
  View initialFocus(){return cancel.getVisibility()==VISIBLE?cancel:next.getVisibility()==VISIBLE?next:episodes;}
  private TextView label(String value,int size,boolean bold){
    TextView v=new TextView(getContext());v.setText(value);v.setTextSize(size);v.setTextColor(WHITE);
    if(bold)v.setTypeface(null,Typeface.BOLD);return v;
  }
  private TextView button(String value,Runnable task){
    TextView v=label(value,15,true);v.setId(View.generateViewId());v.setGravity(Gravity.CENTER);
    v.setFocusable(true);v.setPadding(dp(10),0,dp(10),0);v.setOnClickListener(w->task.run());
    v.setBackgroundColor(0xff183b52);v.setOnFocusChangeListener((w,focus)->{
      v.setBackgroundColor(focus?CYAN:0xff183b52);v.setTextColor(focus?NAVY:WHITE);
    });
    LayoutParams p=new LayoutParams(-1,dp(46));p.topMargin=dp(7);addView(v,p);return v;
  }
  private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
}

package com.snaptvnow.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** An explicit choice after unsuccessful recovery, navigable by touch or TV remote. */
final class LiveRecoveryView extends LinearLayout {
  private final TextView retry;
  LiveRecoveryView(Context context,String channel,String alternative,Runnable tryAgain,Runnable use720,Runnable back){
    super(context);setOrientation(VERTICAL);setPadding(dp(20),dp(16),dp(20),dp(16));setBackgroundColor(0xff10283d);
    addView(label("No se pudo recuperar la señal",22,true));
    TextView title=label(channel,16,true);title.setMaxLines(2);addView(title);
    addView(label(alternative==null?"Puedes reintentar o volver a canales.":"Elige cómo continuar. La alternativa puede tardar en cargar.",15,false));
    List<TextView> buttons=new ArrayList<>();
    retry=button("Reintentar este canal","live_retry",tryAgain);buttons.add(retry);
    if(alternative!=null){TextView target=label("Alternativa del catálogo: "+alternative,14,false);target.setMaxLines(2);addView(target);buttons.add(button("Probar alternativa 720","live_720",use720));}
    buttons.add(button("Volver a canales","live_back",back));
    for(int i=0;i<buttons.size();i++){
      if(i>0)buttons.get(i).setNextFocusUpId(buttons.get(i-1).getId());
      if(i+1<buttons.size())buttons.get(i).setNextFocusDownId(buttons.get(i+1).getId());
    }
  }
  View initialFocus(){return retry;}
  private TextView label(String s,int size,boolean bold){TextView v=new TextView(getContext());v.setText(s);v.setTextSize(size);v.setTextColor(0xffffffff);v.setPadding(0,dp(5),0,dp(5));if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
  private TextView button(String s,String tag,Runnable run){
    TextView v=label(s,15,true);v.setTag(tag);v.setId(View.generateViewId());v.setFocusable(true);v.setGravity(Gravity.CENTER);
    v.setBackgroundColor(0xff183b52);v.setOnClickListener(w->run.run());v.setOnFocusChangeListener((w,f)->{v.setBackgroundColor(f?0xff29dce8:0xff183b52);v.setTextColor(f?0xff071729:0xffffffff);});
    LayoutParams p=new LayoutParams(-1,dp(48));p.topMargin=dp(8);addView(v,p);return v;
  }
  private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}

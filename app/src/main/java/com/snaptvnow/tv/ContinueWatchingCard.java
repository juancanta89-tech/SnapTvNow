package com.snaptvnow.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Compact phone row; opening its options never starts playback. */
final class ContinueWatchingCard extends LinearLayout {
  private final ImageView artwork;
  private TextView options;
  ContinueWatchingCard(Context context,PlaybackHistory.Entry entry,Runnable resume){this(context,entry,resume,null);}
  ContinueWatchingCard(Context context,PlaybackHistory.Entry entry,Runnable resume,Runnable remove){
    super(context);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setPadding(dp(8),dp(8),dp(8),dp(8));setMinimumHeight(dp(92));
    FrameLayout cover=new FrameLayout(context);cover.setBackground(shape(0xff17374e,false));cover.setClipToOutline(true);
    boolean open=context.getResources().getConfiguration().screenWidthDp>=600;
    LayoutParams artParams=new LayoutParams(dp(open?112:72),dp(72));artParams.rightMargin=dp(10);addView(cover,artParams);
    TextView placeholder=new TextView(context);placeholder.setText("▶");placeholder.setTextColor(0xff29dce8);placeholder.setTextSize(24);placeholder.setGravity(Gravity.CENTER);cover.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
    artwork=new ImageView(context);artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.addView(artwork,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout text=new LinearLayout(context);text.setOrientation(VERTICAL);addView(text,new LayoutParams(0,-2,1));
    TextView title=new TextView(context);title.setText(entry.title());title.setTextColor(0xffffffff);title.setTextSize(16);title.setTypeface(null,Typeface.BOLD);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);text.addView(title);
    TextView detail=new TextView(context);detail.setText(entry.detail().replace('\n',' '));detail.setTextColor(0xffb5c9d3);detail.setTextSize(13);detail.setMaxLines(3);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);text.addView(detail);
    if(entry.duration>0){ProgressBar progress=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgress(entry.completed?1000:(int)Math.min(1000,entry.position*1000/entry.duration));progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff29dce8));LayoutParams p=new LayoutParams(-1,dp(4));p.topMargin=dp(6);text.addView(progress,p);}
    setFocusable(true);setClickable(true);setContentDescription(entry.title()+". "+entry.detail());setOnClickListener(v->resume.run());setOnFocusChangeListener((v,focus)->setBackground(shape(0xff10283d,focus)));setBackground(shape(0xff10283d,false));
    if(remove!=null){
      options=new TextView(context);options.setText("⋯");options.setTextSize(25);options.setTextColor(0xffffffff);options.setGravity(Gravity.CENTER);options.setFocusable(true);options.setClickable(true);options.setContentDescription("Opciones de "+entry.title());
      LayoutParams p=new LayoutParams(dp(48),dp(48));p.leftMargin=dp(4);addView(options,p);
      options.setBackground(shape(0xff17374e,false));options.setOnFocusChangeListener((v,focus)->options.setBackground(shape(0xff17374e,focus)));
      options.setOnClickListener(v->showOptions(remove));setOnLongClickListener(v->{showOptions(remove);return true;});
    }
  }
  private void showOptions(Runnable remove){PopupMenu menu=new PopupMenu(getContext(),options);menu.getMenu().add(0,1,0,"Quitar de Continuar viendo");menu.getMenu().add(0,2,1,"Conserva tu progreso").setEnabled(false);menu.setOnMenuItemClickListener(item->{if(item.getItemId()!=1)return false;remove.run();return true;});menu.show();}
  ImageView artwork(){return artwork;}
  TextView options(){return options;}
  private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
  private GradientDrawable shape(int color,boolean focus){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(12));if(focus)shape.setStroke(dp(3),0xff29dce8);return shape;}
}

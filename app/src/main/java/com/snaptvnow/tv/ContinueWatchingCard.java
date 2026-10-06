package com.snaptvnow.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

final class ContinueWatchingCard extends LinearLayout {
  ContinueWatchingCard(Context context,PlaybackHistory.Entry entry,Runnable resume){
    super(context);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setPadding(dp(14),dp(12),dp(14),dp(12));setMinimumHeight(dp(108));
    TextView icon=new TextView(context);icon.setText("▶");icon.setTextColor(0xff29dce8);icon.setTextSize(28);icon.setGravity(Gravity.CENTER);addView(icon,new LayoutParams(dp(52),dp(52)));
    LinearLayout text=new LinearLayout(context);text.setOrientation(VERTICAL);addView(text,new LayoutParams(0,-2,1));
    TextView title=new TextView(context);title.setText(entry.title());title.setTextColor(0xffffffff);title.setTextSize(16);title.setTypeface(null,Typeface.BOLD);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);text.addView(title);
    TextView detail=new TextView(context);detail.setText(entry.detail());detail.setTextColor(0xffb5c9d3);detail.setTextSize(13);detail.setMaxLines(3);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);text.addView(detail);
    if(entry.duration>0){ProgressBar progress=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgress(entry.completed?1000:(int)Math.min(1000,entry.position*1000/entry.duration));progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff29dce8));LayoutParams p=new LayoutParams(-1,dp(5));p.topMargin=dp(6);text.addView(progress,p);}
    setFocusable(true);setClickable(true);setContentDescription(entry.title()+". "+entry.detail());setOnClickListener(v->resume.run());setOnFocusChangeListener((v,focus)->background(focus));background(false);
  }
  private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
  private void background(boolean focus){GradientDrawable shape=new GradientDrawable();shape.setColor(0xff10283d);shape.setCornerRadius(dp(12));if(focus)shape.setStroke(dp(3),0xff29dce8);setBackground(shape);}
}

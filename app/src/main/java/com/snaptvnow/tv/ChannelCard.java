package com.snaptvnow.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Complete channel logo above its readable name; focus never crops or covers artwork. */
final class ChannelCard extends FrameLayout {
  private static final int PANEL=0xff102b40, CYAN=0xff29d8df, WHITE=0xfff4f8fc;
  private boolean saved;
  private final TextView heart;
  private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
  private GradientDrawable background(boolean focused){
    GradientDrawable drawable=new GradientDrawable();drawable.setColor(focused?0xff12364c:PANEL);
    drawable.setCornerRadius(dp(10));drawable.setStroke(dp(focused?3:1),focused?CYAN:0xff214357);return drawable;
  }
  ChannelCard(Context context,Catalog.Item item,boolean favorite,Runnable open,Runnable toggle,Runnable details){
    super(context);saved=favorite;setBackground(background(false));setFocusable(true);setClickable(true);
    setContentDescription(item.title);setClipToOutline(true);
    LinearLayout content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(dp(8),dp(18),dp(8),dp(8));addView(content,new FrameLayout.LayoutParams(-1,-1));
    ImageView logo=new ImageView(context);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
    logo.setImageResource(R.drawable.channel_placeholder);logo.setPadding(dp(14),0,dp(14),0);
    content.addView(logo,new LinearLayout.LayoutParams(-1,0,1));Artwork.into(logo,item.artwork);
    TextView name=new TextView(context);name.setText(item.title);name.setTextColor(WHITE);name.setTextSize(14);
    name.setTypeface(null,Typeface.BOLD);name.setGravity(Gravity.CENTER);name.setMinLines(2);name.setMaxLines(2);
    name.setEllipsize(TextUtils.TruncateAt.END);name.setIncludeFontPadding(false);name.setPadding(0,dp(6),0,0);
    content.addView(name,new LinearLayout.LayoutParams(-1,-2));
    heart=new TextView(context);heart.setTextSize(21);heart.setTextColor(WHITE);heart.setGravity(Gravity.CENTER);
    heart.setFocusable(true);heart.setClickable(true);updateHeart(item.title);
    FrameLayout.LayoutParams heartParams=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.RIGHT);
    addView(heart,heartParams);
    heart.setOnClickListener(v->{saved=!saved;updateHeart(item.title);toggle.run();});
    heart.setOnFocusChangeListener((v,focused)->{heart.setTextColor(focused?CYAN:WHITE);setBackground(background(focused||hasFocus()));});
    setOnClickListener(v->open.run());setOnLongClickListener(v->{details.run();return true;});
    setOnFocusChangeListener((v,focused)->setBackground(background(focused||heart.hasFocus())));
  }
  private void updateHeart(String title){heart.setText(saved?"♥":"♡");heart.setContentDescription((saved?"Quitar de favoritos: ":"Añadir a favoritos: ")+title);}
}

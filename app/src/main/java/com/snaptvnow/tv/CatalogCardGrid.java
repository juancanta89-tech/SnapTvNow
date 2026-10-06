package com.snaptvnow.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

/** Append rows in bounded pages; every catalog item remains reachable. */
final class CatalogCardGrid extends LinearLayout {
  interface Factory { View create(Catalog.Item item); }
  private final List<Catalog.Item> items;
  private final Factory factory;
  private final int columns,channelHeight,posterHeight;
  private int shown;
  private TextView more;
  private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
  CatalogCardGrid(Context context,List<Catalog.Item> items,boolean television,int windowWidthDp,Factory factory){
    super(context);this.items=items;this.factory=factory;setOrientation(VERTICAL);
    columns=CatalogGridLayout.columns(television,windowWidthDp);
    channelHeight=CatalogGridLayout.channelHeightDp(television,windowWidthDp);
    posterHeight=CatalogGridLayout.posterHeightDp(television,windowWidthDp);appendPage(false);
  }
  private void appendPage(boolean focusNew){
    if(more!=null){removeView(more);more=null;}
    int end=Math.min(items.size(),shown+columns*8);View firstNew=null;
    for(int start=shown;start<end;start+=columns){
      LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.TOP);addView(row,new LayoutParams(-1,-2));
      for(int column=0;column<columns;column++){
        int index=start+column;View card;
        int height;
        if(index<end){Catalog.Item item=items.get(index);card=factory.create(item);height=item.id.startsWith("live")?channelHeight:posterHeight;if(firstNew==null)firstNew=card;}
        else{card=new View(getContext());height=1;}
        LayoutParams params=new LayoutParams(0,dp(height),1);params.setMargins(dp(3),dp(2),dp(3),dp(8));row.addView(card,params);
      }
    }
    shown=end;
    if(shown<items.size()){
      more=new TextView(getContext());more.setText("Ver más ("+shown+" de "+items.size()+")");more.setTextColor(0xfff4f8fc);
      more.setTextSize(15);more.setTypeface(null,Typeface.BOLD);more.setGravity(Gravity.CENTER);more.setFocusable(true);more.setClickable(true);
      more.setBackground(buttonBackground(false));more.setOnFocusChangeListener((v,focused)->v.setBackground(buttonBackground(focused)));
      more.setOnClickListener(v->appendPage(true));addView(more,new LayoutParams(-1,dp(48)));
    }
    if(focusNew&&firstNew!=null)firstNew.requestFocus();
  }
  private GradientDrawable buttonBackground(boolean focused){
    GradientDrawable bg=new GradientDrawable();bg.setColor(0xff102b40);bg.setCornerRadius(dp(10));
    bg.setStroke(dp(focused?3:1),focused?0xff29d8df:0xff214357);return bg;
  }
}

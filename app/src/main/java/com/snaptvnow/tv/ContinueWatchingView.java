package com.snaptvnow.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Phone rows and a five-column TV grid with explicit remote-friendly actions. */
final class ContinueWatchingView extends LinearLayout {
  interface EntryAction {void run(PlaybackHistory.Entry entry);}
  interface CoverBinder {void bind(ImageView image,PlaybackHistory.Entry entry);}
  private final List<PlaybackHistory.Entry> entries;
  private final List<View> tiles=new ArrayList<>();
  private View initial;
  private int selected;
  private final boolean compact;
  private TextView selectedTitle,selectedDetail,continueButton,removeButton;
  ContinueWatchingView(Context context,List<PlaybackHistory.Entry> entries,boolean television,String preferred,EntryAction resume,EntryAction remove,CoverBinder covers){
    super(context);this.entries=entries;compact=context.getResources().getConfiguration().screenHeightDp<=600;setOrientation(VERTICAL);
    if(!television){
      for(PlaybackHistory.Entry entry:entries){ContinueWatchingCard card=new ContinueWatchingCard(context,entry,()->resume.run(entry),()->remove.run(entry));covers.bind(card.artwork(),entry);LayoutParams p=new LayoutParams(-1,-2);p.bottomMargin=dp(8);addView(card,p);tiles.add(card);}initial=tiles.isEmpty()?null:tiles.get(0);return;
    }
    for(int start=0;start<entries.size();start+=5){
      LinearLayout row=new LinearLayout(context);addView(row,new LayoutParams(-1,-2));
      for(int column=0;column<5;column++){
        int index=start+column;View tile;
        if(index<entries.size()){
          PlaybackHistory.Entry entry=entries.get(index);tile=tile(entry,covers);tile.setId(View.generateViewId());tiles.add(tile);
          final int position=index;tile.setOnFocusChangeListener((v,focused)->{if(focused)select(position);});tile.setOnClickListener(v->{select(position);resume.run(entry);});
          if(entry.item.id.equals(preferred))selected=index;
        }else tile=new View(context);
        LayoutParams p=new LayoutParams(0,index<entries.size()?dp(compact?112:170):1,1);p.setMargins(dp(3),0,dp(3),dp(10));row.addView(tile,p);
      }
    }
    if(entries.isEmpty())return;
    selectedTitle=label("",compact?16:18,true,0xffffffff);selectedTitle.setSingleLine(true);selectedTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);addView(selectedTitle);
    selectedDetail=label("",compact?12:13,false,0xffb5c9d3);selectedDetail.setSingleLine(true);selectedDetail.setEllipsize(android.text.TextUtils.TruncateAt.END);addView(selectedDetail);
    LinearLayout buttons=new LinearLayout(context);buttons.setPadding(0,dp(8),0,0);addView(buttons);
    continueButton=button("▶  Continuar",()->resume.run(entries.get(selected)));
    removeButton=button("Quitar de Continuar viendo",()->remove.run(entries.get(selected)));
    buttons.addView(continueButton,new LayoutParams(0,dp(48),1));LayoutParams removeParams=new LayoutParams(0,dp(48),1.5f);removeParams.leftMargin=dp(8);buttons.addView(removeButton,removeParams);
    TextView help=label("Conserva tu progreso",12,false,0xffb5c9d3);help.setGravity(Gravity.RIGHT);addView(help);
    continueButton.setNextFocusRightId(removeButton.getId());removeButton.setNextFocusLeftId(continueButton.getId());
    for(int index=0;index<tiles.size();index++){
      View tile=tiles.get(index);
      tile.setNextFocusDownId(index+5<tiles.size()?tiles.get(index+5).getId():continueButton.getId());
      if(index>=5)tile.setNextFocusUpId(tiles.get(index-5).getId());
      if(index%5>0)tile.setNextFocusLeftId(tiles.get(index-1).getId());
      if(index%5<4&&index+1<tiles.size())tile.setNextFocusRightId(tiles.get(index+1).getId());
    }
    select(selected);
    initial=tiles.get(selected);
  }
  private View tile(PlaybackHistory.Entry entry,CoverBinder covers){
    LinearLayout card=new LinearLayout(getContext());card.setOrientation(VERTICAL);card.setPadding(dp(6),dp(6),dp(6),dp(6));card.setFocusable(true);card.setClickable(true);card.setContentDescription(entry.title()+". "+entry.detail());
    FrameLayout cover=new FrameLayout(getContext());card.addView(cover,new LayoutParams(-1,dp(compact?44:94)));
    TextView placeholder=label("▶",23,true,0xff29dce8);placeholder.setGravity(Gravity.CENTER);cover.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));ImageView image=new ImageView(getContext());image.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.addView(image,new FrameLayout.LayoutParams(-1,-1));covers.bind(image,entry);
    TextView name=label(entry.title(),14,true,0xffffffff);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(name,new LayoutParams(-1,dp(compact?32:36)));
    String chapter=entry.item.seasonNumber>0&&entry.item.episodeNumber>0?String.format(java.util.Locale.ROOT,"T%02d · E%02d · ",entry.item.seasonNumber,entry.item.episodeNumber):"";
    TextView detail=label(chapter+(entry.completed?"Visto":PlaybackHistory.time(entry.position)),11,false,0xffb5c9d3);detail.setSingleLine(true);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(detail);
    ProgressBar progress=new ProgressBar(getContext(),null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgress(entry.completed?1000:entry.duration>0?(int)Math.min(1000,entry.position*1000/entry.duration):0);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff29dce8));card.addView(progress,new LayoutParams(-1,dp(4)));return card;
  }
  private void select(int index){selected=index;for(int i=0;i<tiles.size();i++)tiles.get(i).setBackground(shape(i==index));if(selectedTitle!=null){PlaybackHistory.Entry entry=entries.get(index);selectedTitle.setText(entry.title());selectedDetail.setText(entry.detail().replace('\n',' '));continueButton.setNextFocusUpId(tiles.get(index).getId());removeButton.setNextFocusUpId(tiles.get(index).getId());}}
  View initialFocus(){return initial;}
  TextView continueButton(){return continueButton;}
  TextView removeButton(){return removeButton;}
  int selectedIndex(){return selected;}
  private TextView label(String text,int size,boolean bold,int color){TextView label=new TextView(getContext());label.setText(text);label.setTextSize(size);label.setTextColor(color);if(bold)label.setTypeface(null,Typeface.BOLD);return label;}
  private TextView button(String text,Runnable clicked){TextView button=label(text,13,true,0xffffffff);button.setGravity(Gravity.CENTER);button.setFocusable(true);button.setClickable(true);button.setId(View.generateViewId());button.setBackground(shape(false));button.setOnFocusChangeListener((v,focus)->button.setBackground(shape(focus)));button.setOnClickListener(v->clicked.run());return button;}
  private GradientDrawable shape(boolean focused){GradientDrawable background=new GradientDrawable();background.setColor(0xff10283d);background.setCornerRadius(dp(10));if(focused)background.setStroke(dp(3),0xff29dce8);return background;}
  private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}

package com.snaptvnow.tv;

import static org.junit.Assert.*;
import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w700dp-h900dp-mdpi")
public class CatalogCardGridTest {
  private Activity activity;
  @Before public void setUp(){activity=Robolectric.buildActivity(Activity.class).setup().get();}
  @After public void tearDown(){activity.finish();}
  private List<Catalog.Item> items(int count){List<Catalog.Item> list=new ArrayList<>();for(int i=0;i<count;i++)list.add(new Catalog.Item("live"+i,"Canal "+i,"TV en vivo","","Deportes"));return list;}
  private CatalogCardGrid grid(int count,boolean tv,int width){return new CatalogCardGrid(activity,items(count),tv,width,item->{TextView v=new TextView(activity);v.setText(item.title);v.setFocusable(true);return v;});}
  @Test public void unfoldedPhoneActuallyMeasuresFourEqualCardsPerRow(){
    CatalogCardGrid grid=grid(8,false,700);activity.setContentView(grid);
    grid.measure(View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.AT_MOST));grid.layout(0,0,700,grid.getMeasuredHeight());
    LinearLayout row=(LinearLayout)grid.getChildAt(0);assertEquals(4,row.getChildCount());
    for(int i=0;i<4;i++)assertEquals(row.getChildAt(0).getMeasuredWidth(),row.getChildAt(i).getMeasuredWidth());
    assertTrue(row.getChildAt(0).getMeasuredWidth()>140);
  }
  @Test public void televisionActuallyHasFiveSlotsAndEmptyFinalSlotsDoNotOpenChannels(){
    CatalogCardGrid grid=grid(7,true,960);assertEquals(2,grid.getChildCount());
    assertEquals(5,((LinearLayout)grid.getChildAt(0)).getChildCount());
    LinearLayout last=(LinearLayout)grid.getChildAt(1);assertEquals("Canal 6",((TextView)last.getChildAt(1)).getText());
    assertFalse(last.getChildAt(2).isFocusable());assertFalse(last.getChildAt(2).isClickable());
  }
  @Test public void pagingReachesEveryChannelWithoutRecreatingPreviousCards(){
    CatalogCardGrid grid=grid(331,false,700);activity.setContentView(grid);
    View first=((LinearLayout)grid.getChildAt(0)).getChildAt(0);
    assertEquals(9,grid.getChildCount());assertTrue(((TextView)grid.getChildAt(8)).getText().toString().contains("32 de 331"));
    int presses=0;while(grid.getChildAt(grid.getChildCount()-1) instanceof TextView){assertTrue(grid.getChildAt(grid.getChildCount()-1).performClick());assertTrue(++presses<20);}
    assertSame(first,((LinearLayout)grid.getChildAt(0)).getChildAt(0));
    assertEquals(83,grid.getChildCount());
    LinearLayout finalRow=(LinearLayout)grid.getChildAt(82);assertEquals("Canal 330",((TextView)finalRow.getChildAt(2)).getText());
  }
}

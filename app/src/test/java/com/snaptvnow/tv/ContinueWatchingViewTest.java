package com.snaptvnow.tv;

import static org.junit.Assert.*;
import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPopupMenu;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w960dp-h540dp")
public class ContinueWatchingViewTest {
  private Activity activity;private PlaybackHistory history;private int resumes,removals;private String resumed="",removed="";
  @Before public void setUp(){
    activity=Robolectric.buildActivity(Activity.class).setup().get();activity.getSharedPreferences("vod_playback",0).edit().clear().commit();history=new PlaybackHistory(activity);
    for(int i=1;i<=8;i++){Catalog.Item item=new Catalog.Item("movie"+i,"Película "+i,"Películas","https://example.invalid/1.mp4","");history.remember("alice",item);history.save("alice",item.id,91_000,200_000,true);}
  }
  @After public void tearDown(){PopupMenu menu=ShadowPopupMenu.getLatestPopupMenu();if(menu!=null)menu.dismiss();activity.finish();idle();}
  private void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
  private ContinueWatchingView show(boolean tv,String preferred){
    ContinueWatchingView view=new ContinueWatchingView(activity,history.recent("alice"),tv,preferred,entry->{resumes++;resumed=entry.item.id;},entry->{removals++;removed=entry.item.id;history.removeFromContinue("alice",entry);},(image,entry)->{});
    activity.setContentView(view);view.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));view.layout(0,0,720,view.getMeasuredHeight());idle();return view;
  }
  @Test public void phoneMenuRemovesOnlyThatMovieAndNeverStartsPlayback(){
    ContinueWatchingView view=show(false,"");assertEquals(8,view.getChildCount());ContinueWatchingCard card=(ContinueWatchingCard)view.getChildAt(2);
    card.options().performClick();idle();PopupMenu menu=ShadowPopupMenu.getLatestPopupMenu();assertNotNull(menu);
    assertEquals("Quitar de Continuar viendo",menu.getMenu().findItem(1).getTitle());assertFalse(menu.getMenu().findItem(2).isEnabled());
    menu.getMenu().performIdentifierAction(1,0);idle();assertEquals(1,removals);assertEquals("movie6",removed);assertEquals(0,resumes);
    assertEquals(7,history.recent("alice").size());assertEquals(91_000,history.position("alice","movie6"));
  }
  @Test public void dismissingPhoneOptionsDoesNotModifyProgressOrStartMovie(){
    ContinueWatchingCard card=(ContinueWatchingCard)show(false,"").getChildAt(0);card.options().performClick();idle();ShadowPopupMenu.getLatestPopupMenu().dismiss();idle();
    assertEquals(0,removals);assertEquals(0,resumes);assertEquals(8,history.recent("alice").size());card.performClick();assertEquals(1,resumes);assertEquals("movie8",resumed);
  }
  @Test public void longPressAlsoOpensRemovalForSeriesAndPreservesAllChapters(){
    Catalog.Item episode=new Catalog.Item("episode8","Capítulo 2","Series","https://example.invalid/2.mp4","","","series1","Una serie",1,2);history.remember("alice",episode);history.save("alice",episode.id,750_000,2_000_000,true);
    ContinueWatchingCard card=(ContinueWatchingCard)show(false,"").getChildAt(0);assertTrue(card.performLongClick());idle();ShadowPopupMenu.getLatestPopupMenu().getMenu().performIdentifierAction(1,0);idle();
    assertEquals("episode8",removed);assertEquals(750_000,history.position("alice","episode8"));assertTrue(history.recent("alice",PlaybackHistory.Kind.SERIES).isEmpty());assertEquals(0,resumes);
  }
  @Test public void tvHasFiveColumnsAndEmptyLastCellsHaveNoActions(){
    ContinueWatchingView view=show(true,"");LinearLayout first=(LinearLayout)view.getChildAt(0),second=(LinearLayout)view.getChildAt(1);
    assertEquals(5,first.getChildCount());assertEquals(5,second.getChildCount());
    for(int i=0;i<3;i++)assertTrue(second.getChildAt(i).isFocusable());
    for(int i=3;i<5;i++){assertFalse(second.getChildAt(i).isFocusable());assertFalse(second.getChildAt(i).isClickable());}
    float heightDp=view.getMeasuredHeight()/activity.getResources().getDisplayMetrics().density;
    assertTrue("TV grid height "+heightDp+"dp for screen "+activity.getResources().getConfiguration().screenHeightDp+"dp",heightDp<420);
    assertTrue(view.continueButton().isFocusable());assertTrue(view.removeButton().isFocusable());
  }
  @Test public void tvRemoteCanReachFooterAndReturnToSelectedTitle(){
    ContinueWatchingView view=show(true,"");LinearLayout first=(LinearLayout)view.getChildAt(0),second=(LinearLayout)view.getChildAt(1);
    View tile=first.getChildAt(2),below=second.getChildAt(2);tile.requestFocus();idle();
    assertSame(below,tile.focusSearch(View.FOCUS_DOWN));below.requestFocus();idle();assertEquals(7,view.selectedIndex());
    assertSame(view.continueButton(),below.focusSearch(View.FOCUS_DOWN));view.continueButton().requestFocus();idle();
    assertSame(view.removeButton(),view.continueButton().focusSearch(View.FOCUS_RIGHT));view.removeButton().requestFocus();idle();
    assertSame(below,view.removeButton().focusSearch(View.FOCUS_UP));view.removeButton().performClick();assertEquals("movie1",removed);assertEquals(0,resumes);
  }
  @Test public void tvRemovalUsesFocusedMovieAndRebuildFocusStaysOnNextTitle(){
    ContinueWatchingView view=show(true,"movie4");view.initialFocus().requestFocus();idle();assertEquals(4,view.selectedIndex());
    view.removeButton().requestFocus();view.removeButton().performClick();assertEquals("movie4",removed);assertEquals(0,resumes);
    assertEquals(91_000,history.position("alice","movie4"));assertEquals(7,history.recent("alice").size());
    ContinueWatchingView rebuilt=show(true,"movie3");rebuilt.initialFocus().requestFocus();idle();rebuilt.continueButton().performClick();assertEquals("movie3",resumed);
  }
  @Test public void tvEpisodeDetailAndActionsFollowTheSelectedSeries(){
    Catalog.Item episode=new Catalog.Item("episode8","Capítulo 2","Series","https://example.invalid/2.mp4","","","series1","Una serie",1,2);history.remember("alice",episode);history.save("alice",episode.id,750_000,2_000_000,true);
    ContinueWatchingView view=show(true,episode.id);view.initialFocus().requestFocus();idle();view.continueButton().performClick();assertEquals(episode.id,resumed);
    view.removeButton().performClick();assertEquals(episode.id,removed);assertEquals(750_000,history.position("alice",episode.id));
  }
}

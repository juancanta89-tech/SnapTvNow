package com.snaptvnow.tv;

import static org.junit.Assert.*;
import android.app.Activity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w700dp-h900dp-mdpi")
public class ChannelCardTest {
  private Activity activity;
  private final Catalog.Item item=new Catalog.Item("live1","UEFA Nations League 1","TV en vivo","","Deportes");
  @Before public void setUp(){activity=Robolectric.buildActivity(Activity.class).setup().get();}
  @After public void tearDown(){activity.finish();}
  @Test public void completeLogoAndTwoLineNameOccupySeparateAreas(){
    ChannelCard card=new ChannelCard(activity,item,false,()->{},()->{},()->{});activity.setContentView(card);
    card.measure(View.MeasureSpec.makeMeasureSpec(160,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(148,View.MeasureSpec.EXACTLY));card.layout(0,0,160,148);
    LinearLayout content=(LinearLayout)card.getChildAt(0);ImageView logo=(ImageView)content.getChildAt(0);TextView name=(TextView)content.getChildAt(1);
    assertEquals(ImageView.ScaleType.FIT_CENTER,logo.getScaleType());assertNotNull(logo.getDrawable());
    assertEquals(2,name.getMaxLines());assertEquals(item.title,name.getText());assertTrue(logo.getBottom()<=name.getTop());
    assertTrue(logo.getHeight()>40);assertTrue(name.getHeight()>25);
  }
  @Test public void favoriteTouchChangesHeartWithoutStartingPlayback(){
    AtomicInteger opens=new AtomicInteger(),toggles=new AtomicInteger();ChannelCard card=new ChannelCard(activity,item,false,opens::incrementAndGet,toggles::incrementAndGet,()->{});
    TextView heart=(TextView)card.getChildAt(1);assertTrue(heart.performClick());
    assertEquals(1,toggles.get());assertEquals(0,opens.get());assertEquals("♥",heart.getText());
    assertTrue(heart.getContentDescription().toString().startsWith("Quitar"));
    card.performClick();assertEquals(1,opens.get());
  }
  @Test public void remoteCanFocusChannelAndFavoriteWithFullAccessibleName(){
    ChannelCard card=new ChannelCard(activity,item,false,()->{},()->{},()->{});activity.setContentView(card);
    assertTrue(card.isFocusable());assertEquals(item.title,card.getContentDescription());
    assertTrue(card.requestFocus());assertTrue(card.hasFocus());
    View heart=card.getChildAt(1);assertTrue(heart.isFocusable());assertTrue(heart.requestFocus());assertTrue(heart.hasFocus());
  }
}

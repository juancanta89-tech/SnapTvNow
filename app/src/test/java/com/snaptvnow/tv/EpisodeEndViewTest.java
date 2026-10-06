package com.snaptvnow.tv;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.media3.common.Player;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w960dp-h540dp")
@LooperMode(LooperMode.Mode.PAUSED)
public class EpisodeEndViewTest {
  private Activity activity;private EpisodePlaybackController controller;private EpisodeEndView view;
  private int starts,retries,returns;
  @Before public void setUp(){
    activity=Robolectric.buildActivity(Activity.class).setup().get();
    controller=new EpisodePlaybackController(mock(Player.class),true,new EpisodePlaybackController.Listener(){
      public void onStatus(EpisodePlaybackController.State state){if(view!=null)view.update(state,()->retries++);}
      public void onNext(Catalog.Item next){starts++;}
    });
    view=new EpisodeEndView(activity,controller::nextNow,controller::cancel,()->controller.setAutomatic(!controller.state().automatic),()->returns++);
    activity.setContentView(view);controller.resolve(EpisodeQueueTest.episode("2","series1",2,1));controller.onPlaybackStateChanged(Player.STATE_ENDED);
    int width=Math.round(540*activity.getResources().getDisplayMetrics().density);
    view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
    view.layout(0,0,width,view.getMeasuredHeight());Shadows.shadowOf(Looper.getMainLooper()).idle();
  }
  @After public void tearDown(){controller.close();activity.finish();}
  private TextView button(String tag){return view.findViewWithTag(tag);}
  @Test public void remoteInitiallyFocusesCancelAndCanReachNextAndSetting(){
    view.initialFocus().requestFocus();assertSame(button("episode_cancel"),activity.getCurrentFocus());
    assertSame(button("episode_next"),button("episode_cancel").focusSearch(View.FOCUS_UP));
    assertSame(button("episode_auto"),button("episode_cancel").focusSearch(View.FOCUS_DOWN));
    button("episode_cancel").performClick();assertTrue(controller.state().cancelled);assertEquals(View.GONE,button("episode_cancel").getVisibility());
    assertTrue(button("episode_next").isEnabled());
  }
  @Test public void touchingNextStartsOnceWithoutWaiting(){
    button("episode_next").performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(1,starts);assertFalse(button("episode_next").isEnabled());
  }
  @Test public void automaticToggleStopsCountdownAndShowsReadableSetting(){
    button("episode_auto").performClick();assertFalse(controller.state().automatic);assertTrue(button("episode_auto").getText().toString().contains("desactivada"));assertEquals(0,controller.state().seconds);
  }
  @Test public void lastChapterShowsEpisodesInsteadOfBrokenNextButton(){
    controller.resolve(null);assertEquals(View.GONE,button("episode_next").getVisibility());assertTrue(button("episode_status").getText().toString().contains("No hay más"));
    button("episode_list").performClick();assertEquals(1,returns);assertEquals(0,starts);
  }
  @Test public void metadataErrorOffersRetryWithoutStartingVideo(){
    controller.resolutionFailed();assertEquals("Reintentar carga de capítulos",button("episode_next").getText().toString());
    button("episode_next").performClick();assertEquals(1,retries);assertEquals(0,starts);
  }
}

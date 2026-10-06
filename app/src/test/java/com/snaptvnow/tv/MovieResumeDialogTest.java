package com.snaptvnow.tv;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.AlertDialog;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class MovieResumeDialogTest {
  private Activity activity;private PlaybackHistory history;private Catalog.Item movie;private int starts;
  @Before public void setUp(){activity=Robolectric.buildActivity(Activity.class).setup().get();activity.getSharedPreferences("vod_playback",0).edit().clear().commit();history=new PlaybackHistory(activity);movie=new Catalog.Item("movie17","Una película","Películas","https://example.invalid/movie.mp4","Drama");}
  @After public void tearDown(){AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();if(dialog!=null)dialog.dismiss();activity.finish();}
  private AlertDialog show(){history.save("alice",movie.id,1_935_000,7_200_000,true);assertTrue(MovieResumeDialog.show(activity,history,"alice",movie,()->starts++));return ShadowAlertDialog.getLatestAlertDialog();}
  @Test public void reopeningMovieExplicitlyOffersItsSavedTimeAndFocusesContinue(){AlertDialog dialog=show();assertTrue(Shadows.shadowOf(dialog).getMessage().toString().contains("32:15"));assertEquals("Continuar desde 32:15",dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText());assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).hasFocus());assertEquals(0,starts);}
  @Test public void continueKeepsTheBookmarkForThePlayer(){AlertDialog dialog=show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertEquals(1,starts);assertEquals(1_935_000,history.position("alice",movie.id));}
  @Test public void fromBeginningClearsTheMoviePositionBeforeStarting(){AlertDialog dialog=show();dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();assertEquals(1,starts);assertEquals(0,history.position("alice",movie.id));assertFalse(history.completed("alice",movie.id));}
  @Test public void cancelDoesNotStartOrModifyTheMovie(){AlertDialog dialog=show();dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertEquals(0,starts);assertEquals(1_935_000,history.position("alice",movie.id));}
  @Test public void noPromptForUnstartedCompletedOrAnotherAccountsMovie(){assertFalse(MovieResumeDialog.show(activity,history,"alice",movie,()->starts++));history.save("bob",movie.id,90_000,200_000,true);assertFalse(MovieResumeDialog.show(activity,history,"alice",movie,()->starts++));history.save("alice",movie.id,90_000,200_000,true);history.complete("alice",movie.id);assertFalse(MovieResumeDialog.show(activity,history,"alice",movie,()->starts++));assertEquals(0,starts);}
}

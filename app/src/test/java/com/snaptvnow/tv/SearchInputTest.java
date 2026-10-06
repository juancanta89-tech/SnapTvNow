package com.snaptvnow.tv;

import static org.junit.Assert.*;
import android.app.Activity;
import android.content.Context;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class SearchInputTest {
  private Activity activity;private SearchInput input;private TextView button;private InputMethodManager keyboard;private int searches;
  @Before public void setUp(){
    activity=Robolectric.buildActivity(Activity.class).setup().get();LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);input=new SearchInput(activity);root.addView(input);button=new TextView(activity);button.setFocusable(true);button.setText("Buscar");root.addView(button);activity.setContentView(root);input.bind(button,()->searches++);input.setText("comedia");input.requestFocus();keyboard=(InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);keyboard.showSoftInput(input,0);
  }
  @After public void tearDown(){activity.finish();}
  private void assertSubmitted(){assertEquals(1,searches);assertFalse(input.hasFocus());assertTrue(button.hasFocus());assertFalse(Shadows.shadowOf(keyboard).isSoftInputVisible());assertEquals("comedia",input.getText().toString());}
  @Test public void fireTvNextClosesKeyboardAndFocusesSearchWithoutLosingQuery(){assertTrue(Shadows.shadowOf(keyboard).isSoftInputVisible());input.onEditorAction(EditorInfo.IME_ACTION_NEXT);assertSubmitted();}
  @Test public void mobileSearchClosesKeyboardAndFocusesSearch(){input.onEditorAction(EditorInfo.IME_ACTION_SEARCH);assertSubmitted();}
  @Test public void doneActionAlsoEscapesTheKeyboard(){input.onEditorAction(EditorInfo.IME_ACTION_DONE);assertSubmitted();}
  @Test public void clickingSearchClosesKeyboardAsWellAsSubmitting(){button.performClick();assertSubmitted();}
  @Test public void remoteBackLeavesEditorWithoutClosingActivityOrSubmitting(){input.onKeyPreIme(KeyEvent.KEYCODE_BACK,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));input.onKeyPreIme(KeyEvent.KEYCODE_BACK,new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));assertEquals(0,searches);assertTrue(button.hasFocus());assertFalse(Shadows.shadowOf(keyboard).isSoftInputVisible());assertFalse(activity.isFinishing());}
  @Test public void physicalEnterDoesNotSubmitTwice(){input.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));input.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER));assertSubmitted();}
}

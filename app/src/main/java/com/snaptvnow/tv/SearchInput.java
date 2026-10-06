package com.snaptvnow.tv;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/** Transfers focus out of the editor as well as dismissing mobile/Fire TV keyboards. */
final class SearchInput extends EditText {
  private View searchButton;

  SearchInput(Context context){super(context);setSingleLine(true);setImeOptions(EditorInfo.IME_ACTION_SEARCH|EditorInfo.IME_FLAG_NO_EXTRACT_UI);}

  void bind(View button,Runnable submit){
    searchButton=button;
    if(button.getId()==View.NO_ID)button.setId(View.generateViewId());
    setNextFocusForwardId(button.getId());setNextFocusDownId(button.getId());
    button.setFocusableInTouchMode(true);
    button.setOnClickListener(v->{leaveEditor();submit.run();});
    setOnEditorActionListener((view,action,event)->{
      boolean enter=event!=null&&(event.getKeyCode()==KeyEvent.KEYCODE_ENTER||event.getKeyCode()==KeyEvent.KEYCODE_NUMPAD_ENTER);
      boolean accepted=action==EditorInfo.IME_ACTION_SEARCH||action==EditorInfo.IME_ACTION_NEXT||action==EditorInfo.IME_ACTION_DONE||action==EditorInfo.IME_ACTION_GO||enter;
      if(!accepted)return false;
      if(event==null||(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)){leaveEditor();submit.run();}
      return true;
    });
  }

  void leaveEditor(){
    InputMethodManager manager=(InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if(manager!=null)manager.hideSoftInputFromWindow(getWindowToken(),0);
    clearFocus();
    if(searchButton!=null)searchButton.requestFocus();
  }

  @Override public boolean onKeyPreIme(int code,KeyEvent event){
    if(code==KeyEvent.KEYCODE_BACK&&hasFocus()){
      if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())leaveEditor();
      return true;
    }
    return super.onKeyPreIme(code,event);
  }
}

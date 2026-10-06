package com.snaptvnow.tv;

import android.app.AlertDialog;
import android.content.Context;

/** Makes movie resume explicit when opening its poster, including pre-1.0.6 bookmarks. */
final class MovieResumeDialog {
  static boolean show(Context context,PlaybackHistory history,String account,Catalog.Item item,Runnable play){
    long position=history.position(account,item.id);
    if(!item.id.startsWith("movie")||position<=0)return false;
    AlertDialog dialog=new AlertDialog.Builder(context).setTitle(item.title)
        .setMessage("Dejaste esta película en "+PlaybackHistory.time(position)+". ¿Cómo quieres verla?")
        .setPositiveButton("Continuar desde "+PlaybackHistory.time(position),(picker,which)->play.run())
        .setNeutralButton("Desde inicio",(picker,which)->{history.reset(account,item.id);play.run();})
        .setNegativeButton("Cancelar",null).create();
    dialog.setOnShowListener(visible->dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus());dialog.show();return true;
  }
  private MovieResumeDialog(){}
}

package com.snaptvnow.tv;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

/** A fixed two by two home menu on phone, tablet and TV. */
final class HomeCategoryGrid extends LinearLayout {
  interface TileFactory {View create(String icon,String name,int color);}
  HomeCategoryGrid(Context context,boolean wide,TileFactory factory){
    super(context);setOrientation(VERTICAL);
    String[] names={"TV en vivo","Películas","Series",ExclusiveCatalog.SECTION},icons={"◉","▶","▤","★"};
    int[] colors={0xff0d6887,0xff71532c,0xff603e85,0xff006d70};View[] tiles=new View[4];
    float density=getResources().getDisplayMetrics().density;int margin=Math.round(4*density),height=Math.round((wide?144:138)*density);
    for(int row=0;row<2;row++){LinearLayout line=new LinearLayout(context);addView(line,new LayoutParams(-1,-2));
      for(int col=0;col<2;col++){int index=row*2+col;View tile=factory.create(icons[index],names[index],colors[index]);tiles[index]=tile;tile.setId(View.generateViewId());tile.setContentDescription(names[index]);LayoutParams lp=new LayoutParams(0,height,1);lp.setMargins(margin,margin,margin,margin);line.addView(tile,lp);}
    }
    for(int i=0;i<4;i++){if(i%2==0)tiles[i].setNextFocusRightId(tiles[i+1].getId());else tiles[i].setNextFocusLeftId(tiles[i-1].getId());if(i<2)tiles[i].setNextFocusDownId(tiles[i+2].getId());else tiles[i].setNextFocusUpId(tiles[i-2].getId());}
  }
}

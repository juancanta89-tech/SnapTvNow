package com.snaptvnow.tv;
import static org.junit.Assert.*;
import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,qualifiers="w960dp-h540dp")
public class HomeCategoryGridTest {
 @Test public void phoneAndTvExposeAllFourSectionsWithRemoteNavigation(){
  for(boolean wide:new boolean[]{false,true}){Activity activity=Robolectric.buildActivity(Activity.class).setup().get();try{List<String> opened=new ArrayList<>();HomeCategoryGrid grid=new HomeCategoryGrid(activity,wide,(icon,name,color)->{TextView tile=new TextView(activity);tile.setText(name);tile.setFocusable(true);tile.setOnClickListener(v->opened.add(name));return tile;});activity.setContentView(grid);grid.measure(View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));grid.layout(0,0,960,grid.getMeasuredHeight());
   LinearLayout top=(LinearLayout)grid.getChildAt(0),bottom=(LinearLayout)grid.getChildAt(1);View movies=top.getChildAt(1),series=bottom.getChildAt(0),exclusive=bottom.getChildAt(1);assertEquals(2,top.getChildCount());assertEquals(2,bottom.getChildCount());assertTrue(exclusive.getWidth()>300);assertEquals(exclusive.getId(),movies.getNextFocusDownId());assertEquals(exclusive.getId(),series.getNextFocusRightId());assertEquals(series.getId(),exclusive.getNextFocusLeftId());assertEquals(movies.getId(),exclusive.getNextFocusUpId());exclusive.performClick();assertEquals(ExclusiveCatalog.SECTION,opened.get(0));
  }finally{activity.finish();}}
 }
}

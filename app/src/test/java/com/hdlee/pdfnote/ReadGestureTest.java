package com.hdlee.pdfnote;

import android.app.Activity;
import android.graphics.*;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="mdpi")
public class ReadGestureTest {
    private PdfPageView view;
    private final List<Integer> swipes=new ArrayList<>();
    private final List<String> selections=new ArrayList<>();
    private long start;
    @Before public void setup(){
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        PdfPageView.Listener listener=(PdfPageView.Listener)Proxy.newProxyInstance(PdfPageView.Listener.class.getClassLoader(),new Class<?>[]{PdfPageView.Listener.class},(p,m,a)->{if(m.getName().equals("onPageSwipe"))swipes.add((Integer)a[0]);if(m.getName().equals("onTextSelectionFinished"))selections.add(((PdfPageView.TextSelection)a[0]).text);return null;});
        view=new PdfPageView(activity,listener);FrameLayout parent=new FrameLayout(activity);parent.addView(view);activity.setContentView(parent);parent.layout(0,0,1000,1000);view.layout(0,0,1000,1000);
        view.showPage(Bitmap.createBitmap(1000,1000,Bitmap.Config.ARGB_8888),0,new ArrayList<>(),new ArrayList<>(),new ArrayList<>());
        view.setTextRegions(Arrays.asList(new PdfPageView.TextRegion("One","One Two",new RectF(.4f,.4f,.5f,.45f),new RectF(.4f,.4f,.65f,.45f)),new PdfPageView.TextRegion("Two","One Two",new RectF(.55f,.4f,.65f,.45f),new RectF(.4f,.4f,.65f,.45f))),false);view.setPageSwipeEnabled(true);
    }
    private void event(int action,float x,float y,long elapsed){view.layout(0,0,1000,1000);if(action==MotionEvent.ACTION_DOWN)start=android.os.SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(start,start+elapsed,action,x,y,0);view.onTouchEvent(e);e.recycle();}
    private void drag(float x,float y,float endX,float endY,long duration){event(0,x,y,0);event(2,endX,endY,duration/2);event(1,endX,endY,duration);}
    @Test public void quickSwipeOverRecognizedTextTurnsPageWithoutSelection(){drag(450,425,200,425,180);assertEquals(Collections.singletonList(1),swipes);assertTrue(selections.isEmpty());}
    @Test public void jitterDiagonalSlowAndCanceledGesturesDoNotTurnPages(){drag(450,425,458,430,160);drag(450,425,200,700,180);drag(450,425,200,425,1600);event(0,450,425,0);event(2,200,425,100);event(3,200,425,140);shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));assertTrue(swipes.isEmpty());assertTrue(selections.isEmpty());}
    @Test public void heldTextSelectsAndExplicitToolDragsWithoutPaging(){event(0,450,425,0);shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(450));event(2,600,425,480);event(1,600,425,520);assertEquals(Collections.singletonList("One Two"),selections);assertTrue(swipes.isEmpty());view.setDirectTextSelection(true);drag(450,425,600,425,180);assertEquals(Arrays.asList("One Two","One Two"),selections);assertTrue(swipes.isEmpty());}
    @Test public void zoomPanIgnoresJitterAndEdgeSwipeStillTurnsPage()throws Exception{view.focusOnPoint(.5f,.5f);float initial=field("panX");drag(500,700,508,700,180);assertEquals(initial,field("panX"),.01);drag(500,700,580,700,180);assertEquals(initial+64,field("panX"),.01);assertTrue(swipes.isEmpty());drag(5,700,205,700,180);assertEquals(Collections.singletonList(-1),swipes);}
    @Test public void verticalModeAndDisabledModeRespectSettings(){view.setVerticalPageSwipe(true);drag(500,700,500,300,180);assertEquals(Collections.singletonList(1),swipes);view.setPageSwipeEnabled(false);drag(500,700,500,300,180);assertEquals(1,swipes.size());}
    @Test public void fingerInkNeverTurnsPage(){view.setFingerInk(true);view.setInkTool(1,Color.BLUE,.005f);drag(500,700,200,700,180);assertTrue(swipes.isEmpty());}
    private float field(String name)throws Exception{Field f=PdfPageView.class.getDeclaredField(name);f.setAccessible(true);return f.getFloat(view);}
}

package com.hdlee.pdfnote;

import android.app.Activity;
import android.graphics.*;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import java.lang.reflect.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PdfPageViewLassoTest {
    private PdfPageView view;
    private int finished,inkChanges;
    private long time;
    private final List<AnnotationStore.InkStroke> strokes=new ArrayList<>();
    private final List<AnnotationStore.Mark> marks=new ArrayList<>();
    @Before public void setup(){
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        PdfPageView.Listener listener=(PdfPageView.Listener)Proxy.newProxyInstance(PdfPageView.Listener.class.getClassLoader(),new Class<?>[]{PdfPageView.Listener.class},(proxy,method,args)->{if(method.getName().equals("onLassoSelectionFinished"))finished++;if(method.getName().equals("onInkChanged"))inkChanges++;return null;});
        view=new PdfPageView(activity,listener);FrameLayout frame=new FrameLayout(activity);frame.addView(view);activity.setContentView(frame);frame.layout(0,0,1000,1000);view.layout(0,0,1000,1000);
        Bitmap page=Bitmap.createBitmap(1000,1000,Bitmap.Config.ARGB_8888);page.eraseColor(Color.WHITE);new Canvas(page).drawColor(Color.WHITE);
        view.showPage(page,0,marks,strokes,new ArrayList<>());
        view.setTextRegions(Arrays.asList(new PdfPageView.TextRegion("Inside","Inside",new RectF(.22f,.22f,.28f,.28f),new RectF(.2f,.2f,.3f,.3f)),new PdfPageView.TextRegion("Outside","Outside",new RectF(.75f,.75f,.85f,.85f),new RectF(.7f,.7f,.9f,.9f))),false);
        view.setLassoMode(true);
    }
    private RectF dest()throws Exception{Method method=PdfPageView.class.getDeclaredMethod("contentRect");method.setAccessible(true);return (RectF)method.invoke(view);}
    private void event(int action,float x,float y)throws Exception{RectF d=dest();time+=20;MotionEvent e=MotionEvent.obtain(time,time,action,d.left+x*d.width(),d.top+y*d.height(),0);view.onTouchEvent(e);e.recycle();}
    private void triangle()throws Exception{event(MotionEvent.ACTION_DOWN,.1f,.1f);event(MotionEvent.ACTION_MOVE,.6f,.1f);event(MotionEvent.ACTION_MOVE,.1f,.6f);event(MotionEvent.ACTION_UP,.1f,.1f);}
    @Test public void triangleCapturesPolygonWithoutBlueSelectionOverlay()throws Exception{triangle();assertEquals(1,finished);Bitmap image=view.captureLasso();assertNotNull(image);assertEquals(500,image.getWidth(),1);assertEquals(Color.WHITE,image.getPixel(40,40));assertEquals(0,Color.alpha(image.getPixel(image.getWidth()-10,image.getHeight()-10)));assertEquals("Inside",view.lassoText());image.recycle();}
    @Test public void captureContainsExistingHighlightsAndInk()throws Exception{
        AnnotationStore.Mark mark=new AnnotationStore.Mark();mark.page=0;mark.left=.2f;mark.right=.3f;mark.top=.2f;mark.bottom=.3f;mark.color=Color.YELLOW;marks.add(mark);
        AnnotationStore.InkStroke stroke=new AnnotationStore.InkStroke();stroke.page=0;stroke.color=Color.RED;stroke.width=.02f;stroke.points.add(new AnnotationStore.InkPoint(.15f,.35f,1));stroke.points.add(new AnnotationStore.InkPoint(.3f,.35f,1));strokes.add(stroke);
        triangle();Bitmap image=view.captureLasso();assertEquals(Color.YELLOW,image.getPixel(150,150));assertEquals(Color.RED,image.getPixel(100,250));assertEquals(0,inkChanges);assertEquals(1,strokes.size());image.recycle();
    }
    @Test public void zoomedCaptureStillUsesOriginalPageCoordinates()throws Exception{view.focusOnPoint(.3f,.3f);triangle();Bitmap image=view.captureLasso();assertNotNull(image);assertEquals(500,image.getWidth(),1);assertEquals(Color.WHITE,image.getPixel(50,50));assertEquals("Inside",view.lassoText());image.recycle();}
    @Test public void canceledAndTinyGesturesNeverCapture()throws Exception{event(MotionEvent.ACTION_DOWN,.1f,.1f);event(MotionEvent.ACTION_MOVE,.6f,.1f);event(MotionEvent.ACTION_CANCEL,.6f,.1f);assertEquals(0,finished);assertNull(view.captureLasso());event(MotionEvent.ACTION_DOWN,.1f,.1f);event(MotionEvent.ACTION_UP,.1f,.1f);assertNull(view.captureLasso());triangle();assertEquals(1,finished);}
    @Test public void freshSelectionAndPageChangeDiscardOldArea()throws Exception{triangle();view.clearLassoSelection();assertNull(view.captureLasso());triangle();assertEquals(2,finished);view.showPage(Bitmap.createBitmap(1000,1000,Bitmap.Config.ARGB_8888),1,marks,strokes,new ArrayList<>());assertNull(view.captureLasso());assertTrue(view.isLassoMode());}
}

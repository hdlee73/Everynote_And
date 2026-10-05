package com.hdlee.pdfnote;

import android.app.Activity;
import android.graphics.*;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SpreadAndToolsTest {
    private Activity activity;
    private PdfPageView left,right;
    private final List<AnnotationStore.InkStroke> strokes=new ArrayList<>();
    @Before public void setup(){
        activity=Robolectric.buildActivity(Activity.class).setup().get();
        PdfPageView.Listener listener=(PdfPageView.Listener)Proxy.newProxyInstance(PdfPageView.Listener.class.getClassLoader(),new Class<?>[]{PdfPageView.Listener.class},(p,m,a)->null);
        left=new PdfPageView(activity,listener);right=new PdfPageView(activity,listener);
        LinearLayout papers=new LinearLayout(activity);papers.setClipChildren(false);papers.addView(left,new LinearLayout.LayoutParams(0,-1,1));papers.addView(right,new LinearLayout.LayoutParams(0,-1,1));activity.setContentView(papers);
        papers.measure(android.view.View.MeasureSpec.makeMeasureSpec(1200,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(600,android.view.View.MeasureSpec.EXACTLY));papers.layout(0,0,1200,600);
        left.showPage(page(),0,new ArrayList<>(),strokes,new ArrayList<>());right.showPage(page(),1,new ArrayList<>(),strokes,new ArrayList<>());
        left.setSpread(-1,right);right.setSpread(1,left);
    }
    private static Bitmap page(){Bitmap b=Bitmap.createBitmap(500,700,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);return b;}
    private float seamGap(){RectF a=left.pageRect(),b=right.pageRect();return (right.getLeft()+b.left)-(left.getLeft()+a.right);}
    @Test public void twoPagesTouchAndZoomTogether(){
        assertEquals("두 쪽 사이에 틈이 없습니다",0f,seamGap(),1f);
        assertEquals("왼쪽 페이지는 가운데 선에 붙습니다",600f,left.getLeft()+left.pageRect().right,1f);
        left.setZoom(2f);
        assertEquals(2f,right.zoom(),.001f);
        assertEquals("확대해도 두 쪽이 붙어 있습니다",0f,seamGap(),1f);
        assertEquals("같은 높이로 확대됩니다",left.pageRect().top,right.pageRect().top,1f);
        assertEquals(left.pageRect().height(),right.pageRect().height(),1f);
        left.setSpread(0,null);right.setSpread(0,null);left.setZoom(1f);
        assertTrue("한 쪽 보기는 원래대로 가운데에 놓입니다",Math.abs(left.pageRect().centerX()-300f)<1f);
    }
    @Test public void eraserRadiusDecidesWhatIsErased(){
        RectF r=left.pageRect();
        AnnotationStore.InkStroke s=new AnnotationStore.InkStroke();s.page=0;s.width=.002f;s.points.add(new AnnotationStore.InkPoint(.5f,.5f,.5f));strokes.add(s);
        left.setInkTool(2,0xFF000000,.004f);left.setFingerInk(true);
        float x=r.left+r.width()*.5f+30f,y=r.top+r.height()*.5f;
        left.setEraserRadius(10f);touch(x,y);assertEquals("작은 지우개는 30px 떨어진 획을 지우지 않습니다",1,strokes.size());
        left.setEraserRadius(40f);touch(x,y);assertEquals("큰 지우개는 지웁니다",0,strokes.size());
    }
    private void touch(float x,float y){long t=android.os.SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0);left.onTouchEvent(down);down.recycle();MotionEvent up=MotionEvent.obtain(t,t+10,MotionEvent.ACTION_UP,x,y,0);left.onTouchEvent(up);up.recycle();}
    @Test public void videoTypeComesFromTheFileNotTheMp4Name()throws Exception{
        assertEquals("video/x-msvideo",ShareProvider.videoMime(file(new byte[]{'R','I','F','F',0,0,0,0,'A','V','I',' '})));
        assertEquals("video/x-ms-wmv",ShareProvider.videoMime(file(new byte[]{0x30,0x26,(byte)0xB2,0x75,(byte)0x8E,0x66,(byte)0xCF,0x11})));
        assertEquals("video/mp4",ShareProvider.videoMime(file(new byte[]{0,0,0,0x18,'f','t','y','p','m','p','4','2'})));
        assertTrue(ShareProvider.videoUri(activity,new File("x.mp4")).getPathSegments().get(0).startsWith("."));
    }
    private File file(byte[] head)throws IOException{File f=File.createTempFile("video",".mp4");try(OutputStream out=new FileOutputStream(f)){out.write(head);out.write(new byte[32]);}return f;}
}

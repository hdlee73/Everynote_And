package com.hdlee.pdfnote;

import android.app.Activity;
import android.graphics.*;
import android.view.MotionEvent;
import android.widget.FrameLayout;
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
public class PageFollowTest {
    private PdfPageView view;
    private final List<String> log=new ArrayList<>();
    private final List<Integer> swipes=new ArrayList<>();
    private long start;
    @Before public void setup(){
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        PdfPageView.Listener listener=(PdfPageView.Listener)Proxy.newProxyInstance(PdfPageView.Listener.class.getClassLoader(),new Class<?>[]{PdfPageView.Listener.class},(p,m,a)->{if(m.getName().equals("onPageSwipe"))swipes.add((Integer)a[0]);return null;});
        view=new PdfPageView(activity,listener);FrameLayout parent=new FrameLayout(activity);parent.addView(view);activity.setContentView(parent);parent.layout(0,0,1000,1000);view.layout(0,0,1000,1000);
        Bitmap page=Bitmap.createBitmap(500,700,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(page);c.drawColor(Color.WHITE);Paint ink=new Paint();ink.setColor(Color.BLACK);c.drawRect(100,140,400,560,ink);
        view.showPage(page,0,new ArrayList<>(),new ArrayList<>(),new ArrayList<>());view.setPageSwipeEnabled(true);
    }
    private void event(int action,float x,float y,long elapsed){if(action==MotionEvent.ACTION_DOWN)start=android.os.SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(start,start+elapsed,action,x,y,0);view.onTouchEvent(e);e.recycle();}
    @Test public void pageFollowsTheFingerThenReportsRelease(){
        view.setPageDrag(new PdfPageView.PageDrag(){
            @Override public boolean start(int direction){log.add("start"+direction);return true;}
            @Override public void move(float distance){log.add("move");assertTrue(distance>=0);}
            @Override public void end(float velocity){log.add("end");}
        });
        event(0,800,500,0);event(2,700,505,40);event(2,500,505,80);event(2,300,505,120);event(1,300,505,160);
        assertEquals("start1",log.get(0));assertTrue(log.contains("move"));assertEquals("end",log.get(log.size()-1));
        assertTrue("손가락을 따라가는 동안 일반 스와이프로 중복 처리하지 않습니다",swipes.isEmpty());
    }
    @Test public void refusedDragFallsBackToNormalSwipe(){
        view.setPageDrag(new PdfPageView.PageDrag(){@Override public boolean start(int d){return false;}@Override public void move(float x){}@Override public void end(float v){}});
        event(0,800,500,0);event(2,500,505,90);event(1,200,505,180);
        assertEquals(Collections.singletonList(1),swipes);
    }
    @Test public void blankMarginsAreCroppedAndPaperColourFillsTheView(){
        RectF box=view.contentBounds();
        assertTrue(box.left>0.1f&&box.right<0.9f);assertEquals(0xFFFFFFFF,view.paperColor());
        RectF before=view.pageRect();view.setCrop(box);RectF after=view.pageRect();
        assertTrue("여백을 자르면 인쇄 영역이 더 크게 보입니다",after.width()>before.width());
        float contentLeft=after.left+box.left*after.width(),contentRight=after.left+box.right*after.width();
        assertTrue("잘린 영역은 화면을 채웁니다",contentLeft>=-1&&contentRight<=1001&&(contentRight-contentLeft>=999||after.height()*box.height()>=999));
    }
    @Test public void dragKeepsFollowingEvenWhenStartingItClearsTheSelectionState(){
        final List<Float> moves=new ArrayList<>();
        view.setPageDrag(new PdfPageView.PageDrag(){
            @Override public boolean start(int direction){view.clearTextSelectionOverlay();return true;}
            @Override public void move(float distance){moves.add(distance);}
            @Override public void end(float velocity){log.add("end");}
        });
        event(0,800,500,0);event(2,700,505,40);event(2,600,505,80);event(2,400,505,120);event(2,300,505,160);event(1,300,505,200);
        assertTrue("전환 중에도 손가락 이동이 전달됩니다",moves.size()>=3);assertTrue(moves.get(moves.size()-1)>moves.get(0));assertEquals("end",log.get(log.size()-1));
    }
    @Test public void longPressOnEmptyPaperOffersInsertionAtThatPoint(){
        final List<float[]> presses=new ArrayList<>();
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        PdfPageView.Listener listener=(PdfPageView.Listener)Proxy.newProxyInstance(PdfPageView.Listener.class.getClassLoader(),new Class<?>[]{PdfPageView.Listener.class},(p,m,a)->{if(m.getName().equals("onBlankLongPress"))presses.add(new float[]{(Integer)a[0],(Float)a[1],(Float)a[2]});return null;});
        PdfPageView page=new PdfPageView(activity,listener);FrameLayout parent=new FrameLayout(activity);parent.addView(page);activity.setContentView(parent);parent.layout(0,0,1000,1000);page.layout(0,0,1000,1000);
        Bitmap bitmap=Bitmap.createBitmap(500,700,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);
        page.showPage(bitmap,0,new ArrayList<>(),new ArrayList<>(),new ArrayList<>());
        long t=android.os.SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,500,500,0);page.onTouchEvent(down);down.recycle();
        org.robolectric.shadows.ShadowLooper shadow=org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper());
        assertNotEquals("오래 누르기 확인이 예약됩니다",java.time.Duration.ZERO,shadow.getNextScheduledTaskTime());
        shadow.idleFor(java.time.Duration.ofMillis(900));
        assertEquals("빈 종이를 오래 누르면 삽입 메뉴 요청이 옵니다",1,presses.size());
        assertEquals(.5f,presses.get(0)[1],.05f);assertEquals(.5f,presses.get(0)[2],.05f);
    }
}

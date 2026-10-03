package com.hdlee.pdfnote;

import android.app.Activity;
import android.graphics.*;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Proxy;
import java.util.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AttachmentTest {
    private Activity activity;
    private PdfPageView view;
    private AnnotationStore store;
    private final List<String> calls=new ArrayList<>();
    private long start;
    @Before public void setup()throws Exception{
        activity=Robolectric.buildActivity(Activity.class).setup().get();
        PdfPageView.Listener listener=(PdfPageView.Listener)Proxy.newProxyInstance(PdfPageView.Listener.class.getClassLoader(),new Class<?>[]{PdfPageView.Listener.class},(p,m,a)->{calls.add(m.getName());return null;});
        view=new PdfPageView(activity,listener);FrameLayout parent=new FrameLayout(activity);parent.addView(view);activity.setContentView(parent);parent.layout(0,0,1000,1000);view.layout(0,0,1000,1000);
        store=new AnnotationStore(activity);store.open(android.net.Uri.parse("content://attach/"+UUID.randomUUID()));
        Bitmap page=Bitmap.createBitmap(500,700,Bitmap.Config.ARGB_8888);page.eraseColor(Color.WHITE);
        view.showPage(page,0,store.marks,store.strokes,store.translations);view.setAnnotationStore(store);view.setPageSwipeEnabled(true);
    }
    private AnnotationStore.PageElement element(String kind,float l,float t,float r,float b){AnnotationStore.PageElement e=new AnnotationStore.PageElement();e.page=0;e.kind=kind;e.left=l;e.top=t;e.right=r;e.bottom=b;store.elements.add(e);return e;}
    private void event(int action,float x,float y,long elapsed){if(action==MotionEvent.ACTION_DOWN)start=android.os.SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(start,start+elapsed,action,x,y,0);view.onTouchEvent(e);e.recycle();}
    @Test public void cornerDragResizesPicturesKeepingTheirShape(){
        RectF pr=view.pageRect();float pw=pr.width(),ph=pr.height();
        AnnotationStore.PageElement e=element("image",.2f,.2f,.5f,.2f+.3f*pw/ph);view.selectElement(e);
        float cx=pr.left+e.right*pw,cy=pr.top+e.bottom*ph;
        event(0,cx,cy,0);event(2,cx+60,cy+5,40);event(2,cx+100,cy+8,80);event(1,cx+100,cy+8,120);
        float widthPx=(e.right-e.left)*pw,heightPx=(e.bottom-e.top)*ph;
        assertEquals("가로세로 비율을 유지합니다",widthPx,heightPx,2f);assertTrue("크기가 커졌습니다",widthPx>.3f*pw+80);assertTrue(e.right<=1f&&e.bottom<=1f);
        assertTrue("변경이 저장됩니다",calls.contains("onInkChanged"));
    }
    @Test public void draggingTheBodyMovesAndStaysInsideThePage(){
        RectF pr=view.pageRect();float pw=pr.width(),ph=pr.height();
        AnnotationStore.PageElement e=element("sticker",.3f,.3f,.5f,.3f+.2f*pw/ph);view.selectElement(e);
        float mx=pr.left+.4f*pw,my=pr.top+(e.top+e.bottom)/2*ph;float width=e.right-e.left;
        event(0,mx,my,0);event(2,mx+40,my+30,40);event(2,mx+4000,my,80);event(1,mx+4000,my,120);
        assertTrue("페이지 밖으로 나가지 않습니다",e.right<=1.0001f&&e.left>=0f);assertEquals("크기는 그대로입니다",width,e.right-e.left,.0005f);assertTrue(e.left>.3f);
    }
    @Test public void tapOnSelectedPictureOpensItsMenuAndOutsideTapClearsSelection(){
        RectF pr=view.pageRect();float pw=pr.width(),ph=pr.height();
        AnnotationStore.PageElement e=element("image",.2f,.2f,.5f,.4f);view.selectElement(e);
        float mx=pr.left+.35f*pw,my=pr.top+.3f*ph;event(0,mx,my,0);event(1,mx,my,40);assertTrue(calls.contains("onElementTapped"));
        event(0,pr.left+.9f*pw,pr.top+.9f*ph,100);event(1,pr.left+.9f*pw,pr.top+.9f*ph,140);assertNull(view.selectedElement());
    }
    @Test public void newAttachmentKindsSurviveSavingAndRejectUnsafeTargets()throws Exception{
        String video=UUID.randomUUID()+".mp4",thumb=UUID.randomUUID()+".png";
        for(String[] c:new String[][]{{"sticker","⭐",""},{"hyperlink","https://example.com/a?b=1",""},{"hyperlink","page:3",""},{"video",video,thumb}}){
            AnnotationStore.PageElement e=new AnnotationStore.PageElement();e.kind=c[0];e.text=c[1];e.asset=c[2];AnnotationStore.PageElement back=AnnotationStore.PageElement.fromJson(e.toJson());assertEquals(c[0],back.kind);assertEquals(c[1],back.text);
        }
        for(String[] bad:new String[][]{{"hyperlink","javascript:alert(1)",""},{"hyperlink","page:x",""},{"video","../x.mp4",thumb},{"sticker","",""}}){
            AnnotationStore.PageElement e=new AnnotationStore.PageElement();e.kind=bad[0];e.text=bad[1];e.asset=bad[2];try{AnnotationStore.PageElement.fromJson(e.toJson());fail(bad[0]+" "+bad[1]);}catch(org.json.JSONException expected){}
        }
    }
    @Test public void attachmentsAreIncludedWhenPagesAreDrawnForExport()throws Exception{
        File folder=new File(activity.getFilesDir(),"images");folder.mkdirs();String name=UUID.randomUUID()+".png";
        Bitmap red=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888);red.eraseColor(Color.RED);try(FileOutputStream out=new FileOutputStream(new File(folder,name))){red.compress(Bitmap.CompressFormat.PNG,100,out);}
        AnnotationStore.PageElement image=element("image",.1f,.1f,.4f,.4f);image.asset=name;
        AnnotationStore.PageElement link=element("hyperlink",.5f,.5f,.9f,.6f);link.text="https://example.com";
        Bitmap out=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888);out.eraseColor(Color.WHITE);AnnotationPainter.elements(activity,new Canvas(out),new RectF(0,0,400,400),store,0);
        assertEquals("이미지가 그려집니다",Color.RED,out.getPixel(100,100));
        int under=out.getPixel(280,238);assertTrue("링크 밑줄이 그려집니다",Color.blue(under)>Color.red(under)+40);
    }
}

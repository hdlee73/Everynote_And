package com.hdlee.pdfnote;

import android.app.*;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="mdpi",shadows=ReadingToolbarTest.RendererShadow.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReadingToolbarTest {
    @Implements(PdfRenderer.class)
    public static class RendererShadow {
        @Implementation protected void __constructor__(ParcelFileDescriptor descriptor){}
        @Implementation protected int getPageCount(){return 1;}
        @Implementation protected void close(){}
    }
    private MainActivity activity;
    private AnnotationStore store;
    @Before public void setup()throws Exception{
        activity=Robolectric.buildActivity(MainActivity.class).setup().get();
        store=new AnnotationStore(activity);store.open(Uri.parse("content://toolbar/"+UUID.randomUUID()));
        set("store",store);set("renderer",new PdfRenderer(null));
    }
    @Test public void mainToolbarIsCompactAndOutlineAndViewAreDirectlyAvailable()throws Exception{
        View root=field("root");layout(root,360,720);View bar=root.findViewWithTag("reading_toolbar");TextView page=root.findViewWithTag("page_indicator");
        assertEquals(54,bar.getLayoutParams().height-bar.getPaddingBottom());assertEquals(86,page.getLayoutParams().width);assertTrue(page.getLayoutParams().width<bar.getWidth()/3);
        description(bar,"문서 개요").performClick();assertEquals("문서 개요",org.robolectric.Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getTitle().toString());ShadowAlertDialog.getLatestAlertDialog().dismiss();
        description(bar,"보기 방법").performClick();assertTrue(hasText(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(),"두 쪽 보기 · 꺼짐"));ShadowAlertDialog.getLatestAlertDialog().dismiss();
        page.performClick();assertEquals("페이지로 이동",org.robolectric.Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getTitle().toString());ShadowAlertDialog.getLatestAlertDialog().dismiss();screenshot(root,"reading-toolbar.png");
    }
    @Test public void fullscreenCanAddOutlineWithoutExitingAndReturnsToNormalToolbar()throws Exception{
        invoke("toggleFullscreen");assertTrue((Boolean)field("fullscreen"));View dock=field("fullscreenDock");assertEquals(View.VISIBLE,dock.getVisibility());assertEquals(View.GONE,((View)field("bottomBar")).getVisibility());
        description(dock,"전체 화면 개요").performClick();AlertDialog list=ShadowAlertDialog.getLatestAlertDialog();list.getButton(AlertDialog.BUTTON_POSITIVE).performClick();org.robolectric.shadows.ShadowLooper.idleMainLooper();assertTrue("Outline placement starts after dialog action", (Boolean)field("outlineMode"));assertTrue((Boolean)field("fullscreen"));
        activity.onOutlinePointRequested(0,.25f,.5f);AlertDialog editor=ShadowAlertDialog.getLatestAlertDialog();EditText input=findEdit(editor.getWindow().getDecorView());input.setText("중요한 내용");editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();org.robolectric.shadows.ShadowLooper.idleMainLooper();assertEquals(1,store.outlines.size());assertEquals("중요한 내용",store.outlines.get(0).title);assertEquals(.25f,store.outlines.get(0).x,.001f);assertFalse((Boolean)field("outlineMode"));assertTrue((Boolean)field("fullscreen"));assertEquals(View.VISIBLE,dock.getVisibility());
        View root=field("root");layout(root,360,720);screenshot(root,"fullscreen-toolbar.png");description(dock,"전체 화면 종료").performClick();assertFalse((Boolean)field("fullscreen"));assertEquals(View.VISIBLE,((View)field("bottomBar")).getVisibility());assertEquals(View.GONE,dock.getVisibility());
    }
    @Test public void longTabNamesStayAtTheirBeginningWithEndEllipsis()throws Exception{
        Class<?> type=Class.forName("com.hdlee.pdfnote.MainActivity$DocumentSession");Constructor<?> ctor=type.getDeclaredConstructor();ctor.setAccessible(true);Object session=ctor.newInstance();Field title=type.getDeclaredField("title");title.setAccessible(true);String filename="Beginning_of_a_very_long_document_name_2026.pdf";title.set(session,filename);Field uri=type.getDeclaredField("uri");uri.setAccessible(true);uri.set(session,Uri.parse("content://tabs/document.pdf"));
        ((List<Object>)field("sessions")).add(session);set("activeSession",session);invoke("updateTabs");View root=field("root");layout(root,360,720);TextView name=root.findViewWithTag("document_tab_title");assertNotNull(name);assertEquals(filename,name.getText().toString());assertEquals("Title horizontal scroll",0,name.getScrollX());assertEquals("Title gravity",Gravity.START|Gravity.CENTER_VERTICAL,name.getGravity());assertEquals("Title ellipsis setting",android.text.TextUtils.TruncateAt.END,name.getEllipsize());assertEquals("Title direction",View.TEXT_DIRECTION_LTR,name.getTextDirection());assertTrue("Long title must be ellipsized: width="+name.getWidth()+", textWidth="+name.getPaint().measureText(filename)+", layoutWidth="+name.getLayout().getWidth(),name.getLayout().getEllipsisCount(0)>0);screenshot(root,"document-tabs.png");
    }
    @Test public void folderIconRendersAssignedColorAtSmallAndLargeSizes(){
        for(int size:new int[]{26,96}){Bitmap image=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);FolderIconDrawable icon=new FolderIconDrawable(0xFF54A485,size);icon.setBounds(0,0,size,size);icon.draw(new Canvas(image));int front=image.getPixel(size/2,size*40/64);assertEquals(255,Color.alpha(front));assertTrue(Color.green(front)>Color.red(front));assertEquals(0,Color.alpha(image.getPixel(0,0)));image.recycle();}
    }
    private <T>T field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return (T)f.get(activity);}
    private void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private void invoke(String name)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}
    private static void layout(View root,int w,int h){root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);}
    private static View description(View view,String description){if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=description(((ViewGroup)view).getChildAt(i),description);if(found!=null)return found;}return null;}
    private static boolean hasText(View view,String text){if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return true;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(hasText(((ViewGroup)view).getChildAt(i),text))return true;return false;}
    private static EditText findEdit(View view){if(view instanceof EditText)return (EditText)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){EditText e=findEdit(((ViewGroup)view).getChildAt(i));if(e!=null)return e;}return null;}
    private static void screenshot(View root,String name)throws Exception{File folder=new File("build/test-screenshots");folder.mkdirs();Bitmap image=Bitmap.createBitmap(root.getMeasuredWidth(),root.getMeasuredHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(image));try(OutputStream out=new FileOutputStream(new File(folder,name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
}

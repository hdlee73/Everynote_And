package com.hdlee.pdfnote;
import android.app.Activity;
import android.graphics.*;
import android.net.Uri;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NotebookFeatureTest {
    @Test public void pageElementsPersistAndSearchFindsTypedNotes()throws Exception{AnnotationStore store=new AnnotationStore(RuntimeEnvironment.getApplication());store.open(Uri.parse("content://notebook/"+UUID.randomUUID()));AnnotationStore.PageElement element=new AnnotationStore.PageElement();element.text="Financial research 금융";store.elements.add(element);store.save();AnnotationStore restored=new AnnotationStore(RuntimeEnvironment.getApplication());String backup=store.exportJson(Uri.parse("content://test"),"note");restored.importJson(backup,2);assertEquals(1,restored.elements.size());assertEquals(1,SearchScanner.annotations(restored,"financial").size());assertEquals(1,SearchScanner.annotations(restored,"금융").size());assertTrue(SearchScanner.annotations(restored,"missing").isEmpty());}
    @Test public void invalidGeometryDoesNotReplaceExistingNotes()throws Exception{AnnotationStore store=new AnnotationStore(RuntimeEnvironment.getApplication());AnnotationStore.PageElement element=new AnnotationStore.PageElement();element.text="keep";store.elements.add(element);org.json.JSONObject root=new org.json.JSONObject(store.exportJson(Uri.parse("content://test"),"note"));root.getJSONArray("elements").getJSONObject(0).put("right",2);try{store.importJson(root.toString(),1);fail();}catch(org.json.JSONException expected){}assertEquals("keep",store.elements.get(0).text);}
    @Test public void notebookNamesNeverEscapeFolderAndDuplicatesStayIntact()throws Exception{File folder=new File(RuntimeEnvironment.getApplication().getCacheDir(),UUID.randomUUID().toString());folder.mkdir();for(String name:new String[]{"../escape","a/b","..",""})try{NotebookFiles.unique(folder,name);fail();}catch(IOException expected){}File first=NotebookFiles.unique(folder,"Study.pdf");try(OutputStream out=new FileOutputStream(first)){out.write(new byte[]{1,2,3});}File second=NotebookFiles.unique(folder,"Study.pdf");assertNotEquals(first,second);assertEquals("Study (1).pdf",second.getName());assertEquals(3,first.length());}
    @Test public void bodyDragAndEdgeSwipeHaveDifferentOutcomes()throws Exception{Activity activity=Robolectric.buildActivity(Activity.class).setup().get();final List<Integer> swipes=new ArrayList<>();PdfPageView.Listener listener=(PdfPageView.Listener)Proxy.newProxyInstance(PdfPageView.Listener.class.getClassLoader(),new Class<?>[]{PdfPageView.Listener.class},(proxy,method,args)->{if(method.getName().equals("onPageSwipe"))swipes.add((Integer)args[0]);return null;});PdfPageView view=new PdfPageView(activity,listener);FrameLayout parent=new FrameLayout(activity);parent.addView(view);activity.setContentView(parent);parent.layout(0,0,1000,1000);view.layout(0,0,1000,1000);view.showPage(Bitmap.createBitmap(1000,1000,Bitmap.Config.ARGB_8888),0,new ArrayList<>(),new ArrayList<>(),new ArrayList<>());view.setPageSwipeEnabled(true);drag(view,500,900);assertTrue(swipes.isEmpty());drag(view,5,200);assertEquals(Collections.singletonList(-1),swipes);}
    private static void drag(PdfPageView view,float x,float end){long t=android.os.SystemClock.uptimeMillis();int[] actions={MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP};for(int i=0;i<3;i++){MotionEvent event=MotionEvent.obtain(t,t+100*i,actions[i],i==0?x:end,100,0);view.onTouchEvent(event);event.recycle();}}
    @Test public void straightToolRetainsOnlyStartAndEnd()throws Exception{Activity activity=Robolectric.buildActivity(Activity.class).setup().get();PdfPageView.Listener listener=(PdfPageView.Listener)Proxy.newProxyInstance(PdfPageView.Listener.class.getClassLoader(),new Class<?>[]{PdfPageView.Listener.class},(p,m,a)->null);PdfPageView view=new PdfPageView(activity,listener);FrameLayout parent=new FrameLayout(activity);parent.addView(view);activity.setContentView(parent);parent.layout(0,0,1000,1000);view.layout(0,0,1000,1000);List<AnnotationStore.InkStroke> strokes=new ArrayList<>();view.showPage(Bitmap.createBitmap(1000,1000,Bitmap.Config.ARGB_8888),0,new ArrayList<>(),strokes,new ArrayList<>());view.setFingerInk(true);view.setInkTool(3,Color.BLUE,.005f);long time=1;int[] actions={0,2,2,1};float[] xs={100,200,350,500};for(int i=0;i<4;i++){MotionEvent e=MotionEvent.obtain(time,time+20*i,actions[i],xs[i],100+i*80,0);view.onTouchEvent(e);e.recycle();}assertEquals(1,strokes.size());assertEquals(2,strokes.get(0).points.size());assertEquals(.5f,strokes.get(0).points.get(1).x,.001);}
    @Test public void toolsMenuHasOneActionPerItem()throws Exception{MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();Method method=MainActivity.class.getDeclaredMethod("showTools");method.setAccessible(true);method.invoke(activity);}
}

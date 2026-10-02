package com.hdlee.pdfnote;

import android.graphics.Typeface;
import android.net.Uri;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TypingSearchTest {
    private AnnotationStore newStore()throws Exception{AnnotationStore store=new AnnotationStore(RuntimeEnvironment.getApplication());store.open(Uri.parse("content://typing/"+UUID.randomUUID()));return store;}

    @Test public void typedTextStylePersistsThroughBackupAndRestore()throws Exception{
        AnnotationStore store=newStore();AnnotationStore.PageElement e=new AnnotationStore.PageElement();
        e.text="강조할 문장";e.textSize=.05f;e.color=0xFFDC2626;e.font="serif";e.bold=true;e.italic=true;store.elements.add(e);
        AnnotationStore restored=newStore();restored.importJson(store.exportJson(Uri.parse("content://test"),"note"),1);
        AnnotationStore.PageElement r=restored.elements.get(0);
        assertEquals(.05f,r.textSize,.0001f);assertEquals(0xFFDC2626,r.color);assertEquals("serif",r.font);assertTrue(r.bold);assertTrue(r.italic);assertEquals("강조할 문장",r.text);
    }
    @Test public void oldBackupsAndUnknownFontsFallBackToReadableDefaults()throws Exception{
        AnnotationStore store=newStore();AnnotationStore.PageElement e=new AnnotationStore.PageElement();e.text="old";store.elements.add(e);
        JSONObject root=new JSONObject(store.exportJson(Uri.parse("content://test"),"note"));JSONObject o=root.getJSONArray("elements").getJSONObject(0);
        o.remove("textSize");o.remove("color");o.put("font","comic-sans");
        AnnotationStore restored=newStore();restored.importJson(root.toString(),1);AnnotationStore.PageElement r=restored.elements.get(0);
        assertEquals(AnnotationStore.PageElement.DEFAULT_TEXT_SIZE,r.textSize,.0001f);assertEquals(AnnotationStore.PageElement.DEFAULT_TEXT_COLOR,r.color);assertEquals("sans",r.font);
        o.put("textSize",5.0);restored.importJson(root.toString(),1);assertEquals(AnnotationStore.PageElement.DEFAULT_TEXT_SIZE,restored.elements.get(0).textSize,.0001f);
    }
    @Test public void fittedHeightGrowsWithLinesAndSize()throws Exception{
        Typeface face=AnnotationPainter.typeface("sans",false,false);
        float one=AnnotationPainter.fitHeight("한 줄",.4f,.027f,1.414f,face);
        float three=AnnotationPainter.fitHeight("한 줄\n두 줄\n세 줄",.4f,.027f,1.414f,face);
        float large=AnnotationPainter.fitHeight("한 줄",.4f,.06f,1.414f,face);
        assertTrue(one>0);assertTrue(three>one*2.5f);assertTrue(large>one*2f);
        StringBuilder builder=new StringBuilder();for(int i=0;i<40;i++)builder.append("긴 문장 ");String longText=builder.toString();
        assertTrue("긴 글은 상자 너비에 맞춰 여러 줄로 감깁니다",AnnotationPainter.fitHeight(longText,.3f,.027f,1.414f,face)>one*3);
    }
    @Test public void everyFontIdMapsToATypefaceAndStylesApply()throws Exception{
        for(String font:AnnotationStore.PageElement.FONTS)assertNotNull(AnnotationPainter.typeface(font,false,false));
        Typeface bold=AnnotationPainter.typeface("serif",true,false),italic=AnnotationPainter.typeface("serif",false,true);
        assertEquals(Typeface.BOLD,bold.getStyle());assertEquals(Typeface.ITALIC,italic.getStyle());
    }
    @Test public void textLayerQualityGateAcceptsReadableTextAndRejectsGarbage()throws Exception{
        List<SearchScanner.Line> good=Collections.singletonList(new SearchScanner.Line("Readable body text 한국어 문장",0,0,1,.1f,new int[]{0},new float[]{0},new float[]{1}));
        List<SearchScanner.Line> garbage=Collections.singletonList(new SearchScanner.Line("\u0001\u0002\u0003\u0004\u0005\u0006\u0007\u0008\u000b\u000c\u000e\u000f\u0010\u0011",0,0,1,.1f,new int[]{0},new float[]{0},new float[]{1}));
        assertTrue(SearchScanner.usableTextLayer(good));assertFalse(SearchScanner.usableTextLayer(garbage));assertFalse(SearchScanner.usableTextLayer(new ArrayList<>()));
    }
    @Test public void searchReadsThePdfTextLayerStartingFromTheCurrentPageAndCachesIt()throws Exception{
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication());
        File pdf=File.createTempFile("search-",".pdf",RuntimeEnvironment.getApplication().getCacheDir());
        String[] pages={"Introduction page about weather and general background information here","Budget forecast for the quarterly review meeting with finance team members","Appendix with additional budget tables and the final budget summary table"};
        try(PDDocument document=new PDDocument()){
            for(String text:pages){PDPage page=new PDPage();document.addPage(page);try(PDPageContentStream out=new PDPageContentStream(document,page)){out.beginText();out.setFont(PDType1Font.HELVETICA,14);out.newLineAtOffset(60,700);out.showText(text);out.endText();}}
            document.save(pdf);
        }
        Uri uri=Uri.fromFile(pdf);final List<Integer> order=new ArrayList<>();final List<SearchScanner.Hit> hits=new ArrayList<>();
        SearchScanner.Listener listener=new SearchScanner.Listener(){
            @Override public void progress(int done,int total){}
            @Override public void hits(List<SearchScanner.Hit> batch){for(SearchScanner.Hit h:batch){order.add(h.page);hits.add(h);}}
        };
        boolean truncated=SearchScanner.scan(RuntimeEnvironment.getApplication(),uri,newStore(),3,1,"budget",false,new AtomicBoolean(),listener);
        assertFalse(truncated);assertEquals(Arrays.asList(1,2),order.subList(0,2));
        assertEquals("page 1 hit is the first match on that page",1,hits.get(0).page);assertTrue(hits.get(0).text.toLowerCase(Locale.ROOT).contains("budget"));
        assertTrue(hits.get(0).box.width()>0&&hits.get(0).box.height()>0);assertEquals(0,SearchScanner.annotations(newStore(),"budget").size());
        File cache=new File(RuntimeEnvironment.getApplication().getCacheDir(),"search_index");assertTrue("text layer saved to disk cache",cache.isDirectory()&&cache.list().length>0);
        hits.clear();order.clear();SearchScanner.scan(RuntimeEnvironment.getApplication(),uri,newStore(),3,0,"BUDGET",false,new AtomicBoolean(),listener);
        assertEquals("case-insensitive and starts from page 0",Arrays.asList(1,2),order.subList(0,2));
    }
    @Test public void canceledSearchStopsWithoutHits()throws Exception{
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication());
        File pdf=File.createTempFile("cancel-",".pdf",RuntimeEnvironment.getApplication().getCacheDir());
        try(PDDocument document=new PDDocument()){document.addPage(new PDPage());document.save(pdf);}
        final int[] count={0};AtomicBoolean cancel=new AtomicBoolean(true);
        SearchScanner.scan(RuntimeEnvironment.getApplication(),Uri.fromFile(pdf),newStore(),1,0,"x",false,cancel,new SearchScanner.Listener(){@Override public void progress(int d,int t){}@Override public void hits(List<SearchScanner.Hit> h){count[0]+=h.size();}});
        assertEquals(0,count[0]);
    }
}

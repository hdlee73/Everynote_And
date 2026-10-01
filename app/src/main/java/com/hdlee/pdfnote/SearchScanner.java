package com.hdlee.pdfnote;
import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.android.gms.tasks.Tasks;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
final class SearchScanner {
    static final class Hit {int page;float x,y;String text;Hit(int p,float x,float y,String text){page=p;this.x=x;this.y=y;this.text=text;}}
    interface Progress {void page(int page,int total);}
    static boolean contains(String text,String query){return text!=null&&text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));}
    static List<Hit> annotations(AnnotationStore store,String q){List<Hit> hits=new ArrayList<>();for(AnnotationStore.Mark m:store.marks)if(contains(m.note,q))hits.add(new Hit(m.page,m.left,m.top,"메모 · "+m.note));for(AnnotationStore.TranslationNote n:store.translations)if(contains(n.translated,q)||contains(n.source,q))hits.add(new Hit(n.page,n.left,n.top,"번역 · "+n.translated));for(AnnotationStore.StudyEntry e:store.studyEntries)if(contains(e.text,q)||contains(e.comment,q))hits.add(new Hit(e.page,e.x,e.y,"노트 · "+e.text+" "+e.comment));for(AnnotationStore.PageElement e:store.elements)if(contains(e.text,q))hits.add(new Hit(e.page,e.left,e.top,(e.kind.equals("link")?"링크 · ":"타이핑 · ")+e.text));return hits;}
    static List<Hit> scan(Context c,Uri uri,AnnotationStore store,String query,AtomicBoolean canceled,Progress progress)throws Exception{
        List<Hit> hits=annotations(store,query);ParcelFileDescriptor fd="file".equals(uri.getScheme())?ParcelFileDescriptor.open(new File(uri.getPath()),ParcelFileDescriptor.MODE_READ_ONLY):c.getContentResolver().openFileDescriptor(uri,"r");if(fd==null)throw new IOException("PDF를 읽을 수 없습니다");
        TextRecognizer latin=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),korean=TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
        try(PdfRenderer renderer=new PdfRenderer(fd)){
            for(int i=0;i<renderer.getPageCount()&&!canceled.get();i++){progress.page(i+1,renderer.getPageCount());try(PdfRenderer.Page page=renderer.openPage(i)){float scale=Math.min(2f,1600f/Math.max(page.getWidth(),page.getHeight()));Bitmap image=Bitmap.createBitmap(Math.max(1,Math.round(page.getWidth()*scale)),Math.max(1,Math.round(page.getHeight()*scale)),Bitmap.Config.ARGB_8888);try{image.eraseColor(Color.WHITE);Matrix matrix=new Matrix();matrix.postScale(scale,scale);page.render(image,null,matrix,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);AnnotationPainter.all(c,new Canvas(image),new RectF(0,0,image.getWidth(),image.getHeight()),store,i);InputImage input=InputImage.fromBitmap(image,0);Text a=Tasks.await(latin.process(input)),b=Tasks.await(korean.process(input));Text result=b.getText().length()>a.getText().length()?b:a;for(Text.TextBlock block:result.getTextBlocks())for(Text.Line line:block.getLines())if(contains(line.getText(),query)&&line.getBoundingBox()!=null){android.graphics.Rect box=line.getBoundingBox();hits.add(new Hit(i,(float)box.left/image.getWidth(),(float)box.top/image.getHeight(),"본문·필기 · "+line.getText()));}}finally{image.recycle();}}}
        }finally{latin.close();korean.close();}
        hits.sort(Comparator.comparingInt((Hit h)->h.page).thenComparingDouble(h->h.y));return hits;
    }
}

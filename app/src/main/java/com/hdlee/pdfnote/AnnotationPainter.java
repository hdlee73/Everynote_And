package com.hdlee.pdfnote;
import android.content.Context;
import android.graphics.*;
import java.io.File;

final class AnnotationPainter {
    private static final android.util.LruCache<String,Bitmap> images=new android.util.LruCache<String,Bitmap>(12*1024*1024){@Override protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}};
    private static Bitmap image(File file){Bitmap b=images.get(file.getPath());if(b==null){b=BitmapFactory.decodeFile(file.getPath());if(b!=null)images.put(file.getPath(),b);}return b;}

    /** Maps a stored font id (sans, serif, mono, hand) plus style flags to a platform typeface. */
    static Typeface typeface(String font,boolean bold,boolean italic){
        int style=(bold?Typeface.BOLD:0)|(italic?Typeface.ITALIC:0);
        Typeface base;
        if("serif".equals(font))base=Typeface.SERIF;
        else if("mono".equals(font))base=Typeface.MONOSPACE;
        else if("hand".equals(font))base=Typeface.create("cursive",Typeface.NORMAL);
        else base=Typeface.SANS_SERIF;
        return Typeface.create(base,style);
    }

    static void text(Canvas canvas,String text,RectF box,float size,int color){text(canvas,text,box,size,color,Typeface.DEFAULT);}
    static void text(Canvas canvas,String text,RectF box,float size,int color,Typeface face){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setTextSize(size);p.setTypeface(face);
        int save=canvas.save();canvas.clipRect(box);float y=box.top+size;
        for(String paragraph:text.split("\n",-1)){
            String remaining=paragraph;
            while(!remaining.isEmpty()){int n=Math.max(1,p.breakText(remaining,true,box.width(),null));canvas.drawText(remaining.substring(0,n),box.left,y,p);y+=size*1.35f;remaining=remaining.substring(n);}
            if(paragraph.isEmpty())y+=size*1.35f;
        }
        canvas.restoreToCount(save);
    }

    /**
     * Height (as a fraction of the page height) a typing box needs so that none of its text is clipped.
     * Uses the same wrapping rules as {@link #text}. {@code pageAspect} is page height divided by page width.
     */
    static float fitHeight(String text,float widthFraction,float sizeFraction,float pageAspect,Typeface face){
        final float pageWidth=1000f;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);float size=Math.max(1f,sizeFraction*pageWidth);p.setTextSize(size);p.setTypeface(face);
        float boxWidth=Math.max(size,widthFraction*pageWidth);int lines=0;
        for(String paragraph:text.split("\n",-1)){
            String remaining=paragraph;
            if(remaining.isEmpty()){lines++;continue;}
            while(!remaining.isEmpty()){int n=Math.max(1,p.breakText(remaining,true,boxWidth,null));lines++;remaining=remaining.substring(n);}
        }
        float heightPx=size*(1.35f*Math.max(1,lines)+.15f);
        return heightPx/(pageWidth*Math.max(.1f,pageAspect));
    }

    static RectF box(RectF dest,AnnotationStore.PageElement e){return new RectF(dest.left+e.left*dest.width(),dest.top+e.top*dest.height(),dest.left+e.right*dest.width(),dest.top+e.bottom*dest.height());}
    /** The text box being edited in place; it is drawn by the editor instead of the page. */
    static volatile AnnotationStore.PageElement skip;
    static void elements(Context context,Canvas c,RectF d,AnnotationStore store,int page){
        if(store==null)return;
        for(AnnotationStore.PageElement e:store.elements){
            if(e.page!=page||e==skip)continue;RectF b=box(d,e);
            if(e.kind.equals("image")){
                File file=new File(new File(context.getFilesDir(),"images"),e.asset);Bitmap image=image(file);
                if(image!=null){
                    float scale=Math.min(b.width()/image.getWidth(),b.height()/image.getHeight());
                    RectF fitted=new RectF(b.centerX()-image.getWidth()*scale/2,b.centerY()-image.getHeight()*scale/2,b.centerX()+image.getWidth()*scale/2,b.centerY()+image.getHeight()*scale/2);
                    c.drawBitmap(image,null,fitted,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
                }
            }else if(e.kind.equals("audio")){
                Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);float r=b.height()/2;fill.setColor(0xFFE6F0FF);c.drawRoundRect(b,r,r,fill);fill.setStyle(Paint.Style.STROKE);fill.setStrokeWidth(Math.max(1f,d.width()*.002f));fill.setColor(0xFF3E91FF);c.drawRoundRect(b,r,r,fill);
                float size=Math.max(8f,b.height()*.46f);text(c,"▶  녹음 "+e.text,new RectF(b.left+r*.9f,b.top+(b.height()-size*1.35f)/2f,b.right-r*.4f,b.bottom),size,0xFF1C74E9);
            }else if(e.kind.equals("link")){
                text(c,"↗ "+e.text,b,Math.max(9,d.width()*.027f),0xFF2563EB);
            }else{
                text(c,e.text,b,Math.max(9,d.width()*e.textSize),e.color,typeface(e.font,e.bold,e.italic));
            }
        }
    }

    /** Pressure-sensitive pen strokes of one page. Shared by the page view, export and handwriting search. */
    static void strokes(Canvas c,RectF d,AnnotationStore store,int page){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        for(AnnotationStore.InkStroke s:store.strokes)if(s.page==page){
            p.setColor(s.color);p.setStrokeCap(Paint.Cap.ROUND);
            for(int i=0;i<s.points.size();i++){
                AnnotationStore.InkPoint b=s.points.get(i),a=s.points.get(Math.max(0,i-1));
                float width=Math.max(1.5f,s.width*d.width()*(.45f+(a.pressure+b.pressure)/2*1.15f));p.setStrokeWidth(width);
                if(i==0)c.drawCircle(d.left+b.x*d.width(),d.top+b.y*d.height(),width/2,p);
                else c.drawLine(d.left+a.x*d.width(),d.top+a.y*d.height(),d.left+b.x*d.width(),d.top+b.y*d.height(),p);
            }
        }
    }

    static void all(Context context,Canvas c,RectF d,AnnotationStore store,int page){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        for(AnnotationStore.Mark m:store.marks)if(m.page==page){
            RectF b=new RectF(d.left+m.left*d.width(),d.top+m.top*d.height(),d.left+m.right*d.width(),d.top+m.bottom*d.height());
            if(!m.noteOnly){p.setColor(m.color);c.drawRect(b,p);}
            if(m.visible&&m.note!=null&&!m.note.isEmpty()){
                RectF note=new RectF(b.right,b.top,Math.min(d.right,b.right+d.width()*.35f),Math.min(d.bottom,b.top+d.height()*.12f));
                p.setColor(0xFFFFF7D6);c.drawRect(note,p);text(c,m.minimized?"메모":m.note,note,d.width()*.023f,0xFF1F1F1F);
            }
        }
        strokes(c,d,store,page);
        for(AnnotationStore.TranslationNote n:store.translations)if(n.page==page&&n.visible){
            RectF box=new RectF(d.left+n.right*d.width(),d.top+n.top*d.height(),Math.min(d.right,d.left+n.right*d.width()+d.width()*.35f),Math.min(d.bottom,d.top+n.top*d.height()+d.height()*.12f));
            p.setColor(0xFFF3E8FF);c.drawRect(box,p);text(c,n.minimized?"번역":n.translated,box,d.width()*.023f,0xFF1F1F1F);
        }
        elements(context,c,d,store,page);
    }
}

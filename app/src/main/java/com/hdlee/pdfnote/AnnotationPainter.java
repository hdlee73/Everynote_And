package com.hdlee.pdfnote;
import android.content.Context;
import android.graphics.*;
import java.io.File;

final class AnnotationPainter {
    private static final android.util.LruCache<String,Bitmap> images=new android.util.LruCache<String,Bitmap>(12*1024*1024){@Override protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}};
    private static Bitmap image(File file){Bitmap b=images.get(file.getPath());if(b==null){b=BitmapFactory.decodeFile(file.getPath());if(b!=null)images.put(file.getPath(),b);}return b;}

    /** Maps a stored font id (sans, serif, mono, hand) plus style flags to a platform typeface. */
    /** The picture file of an attached element; the bundled masking tapes are copied out of the assets on first use. */
    static File builtinAsset(Context context,String asset){
        File file=new File(new File(context.getFilesDir(),"images"),asset);
        if(!file.isFile()&&asset.matches("tape-\\d{2}\\.png")){file.getParentFile().mkdirs();try(java.io.InputStream in=context.getAssets().open("stickers/"+asset);java.io.OutputStream out=new java.io.FileOutputStream(file)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}catch(java.io.IOException ignored){file.delete();}}
        return file;
    }
    static Typeface typeface(String font,boolean bold,boolean italic){
        int style=(bold?Typeface.BOLD:0)|(italic?Typeface.ITALIC:0);
        Typeface base;
        if("serif".equals(font))base=Typeface.SERIF;
        else if("mono".equals(font))base=Typeface.MONOSPACE;
        else if("hand".equals(font))base=Typeface.create("cursive",Typeface.NORMAL);
        else if("casual".equals(font))base=Typeface.create("casual",Typeface.NORMAL);
        else if("typewriter".equals(font))base=Typeface.create("serif-monospace",Typeface.NORMAL);
        else if("medium".equals(font))base=Typeface.create("sans-serif-medium",Typeface.NORMAL);
        else if("light".equals(font))base=Typeface.create("sans-serif-light",Typeface.NORMAL);
        else if("black".equals(font))base=Typeface.create("sans-serif-black",Typeface.NORMAL);
        else if("condensed".equals(font))base=Typeface.create("sans-serif-condensed",Typeface.NORMAL);
        else base=Typeface.SANS_SERIF;
        return Typeface.create(base,style);
    }

    static void text(Canvas canvas,String text,RectF box,float size,int color){text(canvas,text,box,size,color,Typeface.DEFAULT);}
    static void text(Canvas canvas,String text,RectF box,float size,int color,Typeface face){text(canvas,text,box,size,color,face,0,false,false);}
    /** align: 0 left, 1 centre, 2 right. */
    static void text(Canvas canvas,String text,RectF box,float size,int color,Typeface face,int align,boolean underline,boolean strike){text(canvas,text,box,size,color,face,align,underline,strike,1.35f);}
    /** line: distance between baselines as a multiple of size. */
    static void text(Canvas canvas,String text,RectF box,float size,int color,Typeface face,int align,boolean underline,boolean strike,float step){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setTextSize(size);p.setTypeface(face);p.setUnderlineText(underline);p.setStrikeThruText(strike);
        int save=canvas.save();canvas.clipRect(box);float y=box.top+size;
        for(String paragraph:text.split("\n",-1)){
            String remaining=paragraph;
            while(!remaining.isEmpty()){int n=Math.max(1,p.breakText(remaining,true,box.width(),null));String line=remaining.substring(0,n);float x=box.left;if(align!=0){String shown=line.replaceAll("\\s+$","");float room=box.width()-p.measureText(shown);x=box.left+(align==1?room/2f:room);}canvas.drawText(line,x,y,p);y+=size*step;remaining=remaining.substring(n);}
            if(paragraph.isEmpty())y+=size*step;
        }
        canvas.restoreToCount(save);
    }

    /**
     * Height (as a fraction of the page height) a typing box needs so that none of its text is clipped.
     * Uses the same wrapping rules as {@link #text}. {@code pageAspect} is page height divided by page width.
     */
    static float fitHeight(String text,float widthFraction,float sizeFraction,float pageAspect,Typeface face){return fitHeight(text,widthFraction,sizeFraction,pageAspect,face,1.35f);}
    static float fitHeight(String text,float widthFraction,float sizeFraction,float pageAspect,Typeface face,float line){
        final float pageWidth=1000f;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);float size=Math.max(1f,sizeFraction*pageWidth);p.setTextSize(size);p.setTypeface(face);
        float boxWidth=Math.max(size,widthFraction*pageWidth);int lines=0;
        for(String paragraph:text.split("\n",-1)){
            String remaining=paragraph;
            if(remaining.isEmpty()){lines++;continue;}
            while(!remaining.isEmpty()){int n=Math.max(1,p.breakText(remaining,true,boxWidth,null));lines++;remaining=remaining.substring(n);}
        }
        float heightPx=size*(line*Math.max(1,lines)+.15f);
        return heightPx/(pageWidth*Math.max(.1f,pageAspect));
    }

    static RectF box(RectF dest,AnnotationStore.PageElement e){return new RectF(dest.left+e.left*dest.width(),dest.top+e.top*dest.height(),dest.left+e.right*dest.width(),dest.top+e.bottom*dest.height());}
    /** The text box being edited in place; it is drawn by the editor instead of the page. */
    static volatile AnnotationStore.PageElement skip;
    private static boolean lastOfGroup(AnnotationStore store,AnnotationStore.PageElement e){
        boolean after=false;
        for(AnnotationStore.PageElement o:store.elements){if(o==e){after=true;continue;}if(after&&o.kind.equals("hyperlink")&&o.page==e.page&&o.color==e.color&&o.text.equals(e.text))return false;}
        return true;
    }
    /** True while a page is drawn on a dark paper: dark ink and text are lightened so they stay readable. */
    static boolean dark;
    static int adj(int color){
        if(!dark)return color;
        int r=Color.red(color),g=Color.green(color),b=Color.blue(color);
        if(.299f*r+.587f*g+.114f*b>120)return color;
        return Color.argb(Color.alpha(color),r+(int)((255-r)*.88f),g+(int)((255-g)*.88f),b+(int)((255-b)*.88f));
    }
    static void elements(Context context,Canvas c,RectF d,AnnotationStore store,int page){
        if(store==null)return;
        for(AnnotationStore.PageElement e:store.elements){
            if(e.page!=page||e==skip)continue;RectF b=box(d,e);
            int rotSave=c.save();if(e.rot!=0f&&rotates(e))c.rotate(e.rot,b.centerX(),b.centerY());
            if(e.alpha<.999f&&rotates(e))c.saveLayerAlpha(new RectF(d.left-d.width(),d.top-d.height(),d.right+d.width(),d.bottom+d.height()),Math.round(e.alpha*255));
            if(e.kind.equals("image")){
                File file=builtinAsset(context,e.asset);Bitmap image=image(file);
                if(image!=null){
                    float scale=Math.min(b.width()/image.getWidth(),b.height()/image.getHeight());
                    RectF fitted=e.stretch?b:new RectF(b.centerX()-image.getWidth()*scale/2,b.centerY()-image.getHeight()*scale/2,b.centerX()+image.getWidth()*scale/2,b.centerY()+image.getHeight()*scale/2);
                    c.drawBitmap(image,null,fitted,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
                }
            }else if(e.kind.equals("sticker")){
                Paint glyph=new Paint(Paint.ANTI_ALIAS_FLAG);glyph.setTextAlign(Paint.Align.CENTER);float size=Math.min(b.width(),b.height())*.82f;glyph.setTextSize(size);Paint.FontMetrics fm=glyph.getFontMetrics();c.drawText(e.text,b.centerX(),b.centerY()-(fm.ascent+fm.descent)/2f,glyph);
            }else if(e.kind.equals("video")||e.kind.equals("youtube")){
                File file=new File(new File(context.getFilesDir(),"images"),e.asset);Bitmap frame=image(file);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                if(frame!=null){c.drawBitmap(frame,null,b,paint);}else{paint.setColor(0xFF2C2C2E);c.drawRoundRect(b,b.width()*.03f,b.width()*.03f,paint);}
                paint.setColor(0x55000000);c.drawRect(b,paint);float r=Math.min(b.width(),b.height())*.17f;boolean yt=e.kind.equals("youtube");if(yt){r*=1.15f;paint.setColor(0xFFFF0000);c.drawRoundRect(new RectF(b.centerX()-r*1.25f,b.centerY()-r*.88f,b.centerX()+r*1.25f,b.centerY()+r*.88f),r*.5f,r*.5f,paint);}else{paint.setColor(0xE6FFFFFF);c.drawCircle(b.centerX(),b.centerY(),r,paint);}
                android.graphics.Path tri=new android.graphics.Path();tri.moveTo(b.centerX()-r*.32f,b.centerY()-r*.5f);tri.lineTo(b.centerX()-r*.32f,b.centerY()+r*.5f);tri.lineTo(b.centerX()+r*.55f,b.centerY());tri.close();paint.setColor(yt?0xFFFFFFFF:0xFF1C1C1E);c.drawPath(tri,paint);
            }else if(e.kind.equals("shape")){Shapes.drawShape(c,b,e.text,d.width());
            }else if(e.kind.equals("table")){Shapes.drawTable(c,b,e.text,d.width());
            }else if(e.kind.equals("hyperlink")){
                Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(0x24007AFF);c.drawRoundRect(b,b.height()*.12f,b.height()*.12f,paint);paint.setColor(0xFF007AFF);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1.5f,d.width()*.0028f));c.drawLine(b.left,b.bottom-paint.getStrokeWidth(),b.right,b.bottom-paint.getStrokeWidth(),paint);
                if(lastOfGroup(store,e)){
                    // a small blue badge with an arrow marks the end of every link
                    float rad=Math.max(d.width()*.011f,Math.min(b.height()*.42f,d.width()*.02f));float cx=Math.min(d.right-rad,b.right+rad*.3f),cy=Math.max(d.top+rad,b.top-rad*.1f);
                    Paint bp=new Paint(Paint.ANTI_ALIAS_FLAG);bp.setColor(0xFF007AFF);c.drawCircle(cx,cy,rad,bp);
                    bp.setColor(0xFFFFFFFF);bp.setStyle(Paint.Style.STROKE);bp.setStrokeWidth(Math.max(1f,rad*.26f));bp.setStrokeCap(Paint.Cap.ROUND);bp.setStrokeJoin(Paint.Join.ROUND);float k=rad*.38f;
                    c.drawLine(cx-k,cy+k,cx+k,cy-k,bp);android.graphics.Path head=new android.graphics.Path();head.moveTo(cx-k*.1f,cy-k);head.lineTo(cx+k,cy-k);head.lineTo(cx+k,cy+k*.1f);c.drawPath(head,bp);
                }
            }else if(e.kind.equals("audio")){
                Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);float r=b.height()/2;fill.setColor(0xFFE5F0FF);c.drawRoundRect(b,r,r,fill);fill.setStyle(Paint.Style.STROKE);fill.setStrokeWidth(Math.max(1f,d.width()*.002f));fill.setColor(0xFF007AFF);c.drawRoundRect(b,r,r,fill);
                float size=Math.max(8f,b.height()*.46f);text(c,"▶  녹음 "+e.text,new RectF(b.left+r*.9f,b.top+(b.height()-size*1.35f)/2f,b.right-r*.4f,b.bottom),size,0xFF007AFF);
            }else if(e.kind.equals("link")){
                text(c,"↗ "+e.text,b,Math.max(9,d.width()*.027f),0xFF007AFF);
            }else{
                text(c,e.text,b,Math.max(9,d.width()*e.textSize),adj(e.color),typeface(e.font,e.bold,e.italic),e.align,e.underline,e.strike,e.line());
            }
            c.restoreToCount(rotSave);
        }
    }
    /** Elements that can be turned around their centre. */
    static boolean rotates(AnnotationStore.PageElement e){return e.kind.equals("image")||e.kind.equals("sticker")||e.kind.equals("shape")||e.kind.equals("table");}

    /** Pen strokes of one page. Shared by the page view, export and handwriting search. */
    static void strokes(Canvas c,RectF d,AnnotationStore store,int page){
        for(AnnotationStore.InkStroke s:store.strokes)if(s.page==page)stroke(c,d,s);
    }
    static final String[] PEN_NAMES={"볼펜","연필","만년필","붓","사인펜"};
    /** One stroke in the style of its pen: ballpoint, pencil, fountain pen (nib angle), brush (taper) or felt marker. Translucent colours do not darken where the stroke overlaps itself. */
    static void stroke(Canvas c,RectF d,AnnotationStore.InkStroke s){
        int n=s.points.size();if(n==0||d.width()<=0)return;
        int argb=adj(s.color);float penAlpha=s.pen==1?.78f:s.pen==3?.92f:s.pen==4?.82f:1f;int eff=Math.round(Color.alpha(argb)*penAlpha);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(argb|0xFF000000);p.setStrokeCap(s.pen==4?Paint.Cap.SQUARE:Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        int save=-1;if(eff<255)save=c.saveLayerAlpha(d.left,d.top,d.right,d.bottom,Math.max(8,eff));
        float base=s.width*d.width();
        for(int i=0;i<n;i++){
            AnnotationStore.InkPoint b=s.points.get(i),a=s.points.get(Math.max(0,i-1));
            float pr=(a.pressure+b.pressure)/2f,w;
            float ax=d.left+a.x*d.width(),ay=d.top+a.y*d.height(),bx=d.left+b.x*d.width(),by=d.top+b.y*d.height();
            switch(s.pen){
                case 1:w=base*.75f*(.5f+pr*.9f);break;
                case 2:{double ang=Math.atan2(by-ay,bx-ax);double cut=Math.abs(Math.sin(ang+Math.PI/4));w=base*(float)(.32+1.05*cut)*(.65f+pr*.7f);break;}
                case 3:{float t=n<=1?.5f:i/(float)(n-1);float taper=Math.min(1f,Math.min(t,1f-t)*7f);w=base*2.1f*(.35f+pr*.95f)*(.35f+.65f*taper);break;}
                case 4:w=base*1.5f;break;
                default:w=base*(.45f+pr*1.15f);
            }
            w=Math.max(1.5f,w);p.setStrokeWidth(w);
            if(i==0){p.setStyle(Paint.Style.FILL);c.drawCircle(bx,by,w/2,p);p.setStyle(Paint.Style.STROKE);}
            else c.drawLine(ax,ay,bx,by,p);
        }
        if(save>=0)c.restoreToCount(save);
    }

    static void all(Context context,Canvas c,RectF d,AnnotationStore store,int page){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        for(AnnotationStore.Mark m:store.marks)if(m.page==page){
            RectF b=new RectF(d.left+m.left*d.width(),d.top+m.top*d.height(),d.left+m.right*d.width(),d.top+m.bottom*d.height());
            if(!m.noteOnly&&m.path!=null&&m.thick>0){
                Paint hp=new Paint(Paint.ANTI_ALIAS_FLAG);hp.setColor(m.color|0xFF000000);hp.setStyle(Paint.Style.STROKE);hp.setStrokeCap(Paint.Cap.ROUND);hp.setStrokeJoin(Paint.Join.ROUND);hp.setStrokeWidth(Math.max(2f,m.thick*d.height()));
                android.graphics.Path line=new android.graphics.Path();for(int i=0;i+1<m.path.length;i+=2){float x=d.left+m.path[i]*d.width(),y=d.top+m.path[i+1]*d.height();if(i==0)line.moveTo(x,y);else line.lineTo(x,y);}
                int save=c.saveLayerAlpha(d.left,d.top,d.right,d.bottom,Math.max(8,Color.alpha(m.color)));c.drawPath(line,hp);c.restoreToCount(save);
            }else if(!m.noteOnly){p.setColor(m.color);c.drawRect(b,p);}
            if(m.visible&&m.note!=null&&!m.note.isEmpty()){
                RectF note=new RectF(b.right,b.top,Math.min(d.right,b.right+d.width()*.35f),Math.min(d.bottom,b.top+d.height()*.12f));
                p.setColor(0xFFFFF7D6);c.drawRect(note,p);text(c,m.minimized?"메모":m.note,note,d.width()*.023f,0xFF1C1C1E);
            }
        }
        strokes(c,d,store,page);
        for(AnnotationStore.TranslationNote n:store.translations)if(n.page==page&&n.visible){
            RectF box=new RectF(d.left+n.right*d.width(),d.top+n.top*d.height(),Math.min(d.right,d.left+n.right*d.width()+d.width()*.35f),Math.min(d.bottom,d.top+n.top*d.height()+d.height()*.12f));
            p.setColor(0xFFF3E8FF);c.drawRect(box,p);text(c,n.minimized?"번역":n.translated,box,d.width()*.023f,0xFF1C1C1E);
        }
        elements(context,c,d,store,page);
    }
}

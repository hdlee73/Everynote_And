package com.hdlee.pdfnote;

import android.content.Context;
import android.graphics.Color;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.*;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import java.io.*;
import java.nio.file.*;
import java.util.Locale;

final class NotebookFiles {
    static final String[] PAPER_NAMES={"백지","줄노트 (보통)","모눈종이","리걸노트","줄노트 (좁게)","줄노트 (넓게)","점 격자","코넬 노트","오선지","내 PDF·이미지 서식"};
    static final int CUSTOM=9;
    static final int[] COLORS={Color.WHITE,0xFFFFF9E8,0xFFFFF6B0,0xFFEFF6FF,0xFFF0F8EE,0xFFFFF0F4,0xFFEDEFF2,0xFFF3ECFF,0xFF1C1C1E};
    static final String[] COLOR_NAMES={"흰색","크림","리갈 옐로","하늘","연두","분홍","회색","연보라","검정"};
    static final class Paper {
        final int kind,color;
        /** For {@link #CUSTOM}: the PDF or image used as the page background (a copy kept inside the app). */
        final File template;
        Paper(int kind,int color){this(kind,color,null);}
        Paper(int kind,int color,File template){if(kind<0||kind>CUSTOM)throw new IllegalArgumentException("종이 형식");if(kind==CUSTOM&&(template==null||!template.isFile()))throw new IllegalArgumentException("서식 파일을 먼저 고르세요");this.kind=kind;this.color=color;this.template=kind==CUSTOM?template:null;}
        String spec(){return kind+":"+color+(template==null?"":":"+template.getAbsolutePath());}
        static Paper parse(String value){String[] p=value.split(":",3);return new Paper(Integer.parseInt(p[0]),Integer.parseInt(p[1]),p.length>2?new File(p[2]):null);}
    }
    /** One ruling mark of a paper type in PDF points (origin bottom-left): x1,y1,x2,y2,style. style 0 = faint line, 1 = red, 2 = dark, 3 = dot (x1==x2). */
    static java.util.List<float[]> layout(int kind,float w,float h){
        java.util.List<float[]> out=new java.util.ArrayList<>();
        switch(kind){
            case 1:case 4:case 5:{float step=kind==1?25:kind==4?18:32;for(float y=h-54;y>=42;y-=step)out.add(new float[]{36,y,w-36,y,0});break;}
            case 2:{for(float y=h-54;y>=42;y-=18)out.add(new float[]{36,y,w-36,y,0});for(float x=36;x<=w-36;x+=18)out.add(new float[]{x,42,x,h-54,0});break;}
            case 3:{out.add(new float[]{36,h-72,w-36,h-72,1});for(float y=h-96;y>=48;y-=22)out.add(new float[]{36,y,w-36,y,0});out.add(new float[]{74,h-36,74,36,1});out.add(new float[]{78,h-36,78,36,1});break;}
            case 6:{for(float y=h-54;y>=42;y-=18)for(float x=36;x<=w-36;x+=18)out.add(new float[]{x,y,x,y,3});break;}
            case 7:{out.add(new float[]{36,h-60,w-36,h-60,2});for(float y=h-84;y>=190;y-=24)out.add(new float[]{36,y,w-36,y,0});out.add(new float[]{160,h-60,160,190,2});out.add(new float[]{36,190,w-36,190,2});for(float y=166;y>=48;y-=24)out.add(new float[]{36,y,w-36,y,0});break;}
            case 8:{for(float top=h-60;top>=110;top-=72)for(int i=0;i<5;i++)out.add(new float[]{36,top-i*8,w-36,top-i*8,2});break;}
            default:break;
        }
        return out;
    }
    static int[] ruleColor(int style){switch(style){case 1:return new int[]{232,140,140};case 2:return new int[]{120,130,140};case 3:return new int[]{150,160,170};default:return new int[]{185,195,205};}}
    static File root(Context c){File root=new File(c.getFilesDir(),"documents");root.mkdirs();return root;}
    static String name(String text)throws IOException{String n=text.trim();if(n.isEmpty()||n.equals(".")||n.equals("..")||n.length()>100||java.util.regex.Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]").matcher(n).find())throw new IOException("파일 이름에 사용할 수 없는 문자가 있습니다");return n;}
    static String pdfName(String title)throws IOException{String valid=name(title);return valid.toLowerCase(Locale.ROOT).endsWith(".pdf")?valid:valid+".pdf";}
    static File unique(File folder,String name)throws IOException{String valid=name(name);File file=new File(folder,valid);int count=1;int dot=valid.lastIndexOf('.');while(file.exists()){String stem=dot>0?valid.substring(0,dot):valid,ext=dot>0?valid.substring(dot):"";file=new File(folder,stem+" ("+(count++)+")"+ext);}return file;}
    static File blank(File folder,String title,int pages)throws IOException{return create(folder,title,pages,new Paper(0,Color.WHITE));}
    static File create(File folder,String title,int pages,Paper paper)throws IOException{
        if(pages<1)throw new IOException("페이지 수가 올바르지 않습니다");File target=unique(folder,pdfName(title));File temp=File.createTempFile(".note-",".tmp",folder);
        try(PDDocument pdf=new PDDocument()){
            pdf.getDocumentInformation().setCustomMetadataValue("PDFNoteNotebook","true");
            pdf.getDocumentInformation().setCustomMetadataValue("PDFNotePaper",paper.spec());
            java.util.List<Closeable> open=new java.util.ArrayList<>();
            try{for(int i=0;i<pages;i++)addPaper(pdf,paper,-1,open);pdf.save(temp);}finally{for(Closeable c:open)try{c.close();}catch(IOException ignored){}}
            replace(temp,target);return target;
        }finally{temp.delete();}
    }
    static int append(Context context,File file,Paper paper)throws IOException{
        File temp=File.createTempFile(".page-",".tmp",file.getParentFile());int count;
        try{
            try(PDDocument pdf=PDDocument.load(file,MemoryUsageSetting.setupTempFileOnly().setTempDir(context.getCacheDir()))){
                if(!pdf.getCurrentAccessPermission().canModify())throw new IOException("페이지 추가가 허용되지 않는 PDF입니다");
                java.util.List<Closeable> open=new java.util.ArrayList<>();
                try{addPaper(pdf,paper,-1,open);count=pdf.getNumberOfPages();pdf.save(temp);}finally{for(Closeable c:open)try{c.close();}catch(IOException ignored){}}
            }
            replace(temp,file);return count;
        }finally{temp.delete();}
    }
    /** Inserts a blank paper page right after {@code afterIndex} (the last page when out of range). */
    static int insert(Context context,File file,Paper paper,int afterIndex)throws IOException{
        File temp=File.createTempFile(".page-",".tmp",file.getParentFile());int count;
        try{
            try(PDDocument pdf=PDDocument.load(file,MemoryUsageSetting.setupTempFileOnly().setTempDir(context.getCacheDir()))){
                if(!pdf.getCurrentAccessPermission().canModify())throw new IOException("페이지 추가가 허용되지 않는 PDF입니다");
                java.util.List<Closeable> open=new java.util.ArrayList<>();
                try{addPaper(pdf,paper,Math.min(afterIndex,pdf.getNumberOfPages()-1),open);count=pdf.getNumberOfPages();pdf.save(temp);}finally{for(Closeable c:open)try{c.close();}catch(IOException ignored){}}
            }
            replace(temp,file);return count;
        }finally{temp.delete();}
    }
    /** Deletes one page and returns the remaining page count. The last remaining page cannot be deleted. */
    static int delete(Context context,File file,int index)throws IOException{
        File temp=File.createTempFile(".page-",".tmp",file.getParentFile());int count;
        try{
            try(PDDocument pdf=PDDocument.load(file,MemoryUsageSetting.setupTempFileOnly().setTempDir(context.getCacheDir()))){
                if(!pdf.getCurrentAccessPermission().canModify())throw new IOException("페이지 삭제가 허용되지 않는 PDF입니다");
                if(index<0||index>=pdf.getNumberOfPages())throw new IOException("삭제할 페이지가 없습니다");
                if(pdf.getNumberOfPages()<=1)throw new IOException("마지막 한 페이지는 삭제할 수 없습니다");
                pdf.removePage(index);count=pdf.getNumberOfPages();pdf.save(temp);
            }
            replace(temp,file);return count;
        }finally{temp.delete();}
    }
    static void replace(File temp,File target)throws IOException{
        try{Files.move(temp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException error){Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}
    }
    private static void addPaper(PDDocument pdf,Paper paper,int afterIndex,java.util.List<Closeable> open)throws IOException{
        PDPage page;
        if(paper.kind==CUSTOM&&isPdf(paper.template)){
            PDDocument source=PDDocument.load(paper.template);open.add(source);
            if(source.getNumberOfPages()<1)throw new IOException("서식 PDF에 페이지가 없습니다");
            page=pdf.importPage(source.getPage(0));
            if(afterIndex>=0&&afterIndex<pdf.getNumberOfPages()-1){pdf.removePage(page);pdf.getPages().insertAfter(page,pdf.getPage(afterIndex));}
            return;
        }
        page=new PDPage(PDRectangle.A4);if(afterIndex<0||afterIndex>=pdf.getNumberOfPages())pdf.addPage(page);else pdf.getPages().insertAfter(page,pdf.getPage(afterIndex));float width=page.getMediaBox().getWidth(),height=page.getMediaBox().getHeight();
        try(PDPageContentStream canvas=new PDPageContentStream(pdf,page)){
            canvas.setNonStrokingColor(Color.red(paper.color),Color.green(paper.color),Color.blue(paper.color));canvas.addRect(0,0,width,height);canvas.fill();
            if(paper.kind==CUSTOM){
                android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(paper.template.getAbsolutePath());
                if(bitmap==null)throw new IOException("서식 이미지를 읽을 수 없습니다");
                try{
                    float scale=Math.min(width/bitmap.getWidth(),height/bitmap.getHeight()),w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;
                    canvas.drawImage(com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromImage(pdf,bitmap,.9f),(width-w)/2,(height-h)/2,w,h);
                }finally{bitmap.recycle();}
                return;
            }
            boolean dark=Color.red(paper.color)+Color.green(paper.color)+Color.blue(paper.color)<300;
            for(int style=0;style<=3;style++){
                boolean any=false;int[] c=ruleColor(style);if(dark)c=new int[]{Math.min(255,c[0]/2+90),Math.min(255,c[1]/2+90),Math.min(255,c[2]/2+90)};
                canvas.setStrokingColor(c[0],c[1],c[2]);canvas.setLineWidth(style==3?1.6f:style==2?.7f:.45f);canvas.setLineCapStyle(style==3?1:0);
                for(float[] seg:layout(paper.kind,width,height))if((int)seg[4]==style){canvas.moveTo(seg[0],seg[1]);canvas.lineTo(seg[2],seg[3]);any=true;}
                if(any)canvas.stroke();
            }
        }
    }
    static boolean isPdf(File file){return file!=null&&file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf");}
}

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
    static final String[] PAPER_NAMES={"백지","줄노트","모눈종이"};
    static final int[] COLORS={Color.WHITE,0xFFFFF9E8,0xFFEFF6FF,0xFFF0F8EE,0xFFFFF0F4,0xFFEDEFF2};
    static final String[] COLOR_NAMES={"흰색","크림","하늘","연두","분홍","회색"};
    static final class Paper {
        final int kind,color;
        Paper(int kind,int color){if(kind<0||kind>2)throw new IllegalArgumentException("종이 형식");this.kind=kind;this.color=color;}
    }
    static File root(Context c){File root=new File(c.getFilesDir(),"documents");root.mkdirs();return root;}
    static String name(String text)throws IOException{String n=text.trim();if(n.isEmpty()||n.equals(".")||n.equals("..")||n.length()>100||java.util.regex.Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]").matcher(n).find())throw new IOException("파일 이름에 사용할 수 없는 문자가 있습니다");return n;}
    static String pdfName(String title)throws IOException{String valid=name(title);return valid.toLowerCase(Locale.ROOT).endsWith(".pdf")?valid:valid+".pdf";}
    static File unique(File folder,String name)throws IOException{String valid=name(name);File file=new File(folder,valid);int count=1;int dot=valid.lastIndexOf('.');while(file.exists()){String stem=dot>0?valid.substring(0,dot):valid,ext=dot>0?valid.substring(dot):"";file=new File(folder,stem+" ("+(count++)+")"+ext);}return file;}
    static File blank(File folder,String title,int pages)throws IOException{return create(folder,title,pages,new Paper(0,Color.WHITE));}
    static File create(File folder,String title,int pages,Paper paper)throws IOException{
        if(pages<1)throw new IOException("페이지 수가 올바르지 않습니다");File target=unique(folder,pdfName(title));File temp=File.createTempFile(".note-",".tmp",folder);
        try(PDDocument pdf=new PDDocument()){
            pdf.getDocumentInformation().setCustomMetadataValue("PDFNoteNotebook","true");
            pdf.getDocumentInformation().setCustomMetadataValue("PDFNotePaper",paper.kind+":"+paper.color);
            for(int i=0;i<pages;i++)addPaper(pdf,paper);pdf.save(temp);replace(temp,target);return target;
        }finally{temp.delete();}
    }
    static int append(Context context,File file,Paper paper)throws IOException{
        File temp=File.createTempFile(".page-",".tmp",file.getParentFile());int count;
        try{
            try(PDDocument pdf=PDDocument.load(file,MemoryUsageSetting.setupTempFileOnly().setTempDir(context.getCacheDir()))){
                if(!pdf.getCurrentAccessPermission().canModify())throw new IOException("페이지 추가가 허용되지 않는 PDF입니다");
                addPaper(pdf,paper);count=pdf.getNumberOfPages();pdf.save(temp);
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
                addPaper(pdf,paper,Math.min(afterIndex,pdf.getNumberOfPages()-1));count=pdf.getNumberOfPages();pdf.save(temp);
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
    private static void addPaper(PDDocument pdf,Paper paper)throws IOException{addPaper(pdf,paper,-1);}
    private static void addPaper(PDDocument pdf,Paper paper,int afterIndex)throws IOException{
        PDPage page=new PDPage(PDRectangle.A4);if(afterIndex<0||afterIndex>=pdf.getNumberOfPages())pdf.addPage(page);else pdf.getPages().insertAfter(page,pdf.getPage(afterIndex));float width=page.getMediaBox().getWidth(),height=page.getMediaBox().getHeight();
        try(PDPageContentStream canvas=new PDPageContentStream(pdf,page)){
            canvas.setNonStrokingColor(Color.red(paper.color),Color.green(paper.color),Color.blue(paper.color));canvas.addRect(0,0,width,height);canvas.fill();
            if(paper.kind==0)return;
            canvas.setStrokingColor(185,195,205);canvas.setLineWidth(.45f);float step=paper.kind==1?25:18;
            for(float y=height-54;y>=42;y-=step){canvas.moveTo(36,y);canvas.lineTo(width-36,y);}
            if(paper.kind==2)for(float x=36;x<=width-36;x+=step){canvas.moveTo(x,42);canvas.lineTo(x,height-54);}
            canvas.stroke();
        }
    }
}

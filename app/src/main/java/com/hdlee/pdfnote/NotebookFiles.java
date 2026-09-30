package com.hdlee.pdfnote;
import android.content.Context;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import java.io.*;
final class NotebookFiles {
    static File root(Context c){File root=new File(c.getFilesDir(),"documents");root.mkdirs();return root;}
    static String name(String text)throws IOException{String n=text.trim();if(n.isEmpty()||n.equals(".")||n.equals("..")||n.length()>100||n.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*"))throw new IOException("파일 이름에 사용할 수 없는 문자가 있습니다");return n;}
    static File unique(File folder,String name)throws IOException{String valid=name(name);File file=new File(folder,valid);int count=1;int dot=valid.lastIndexOf('.');while(file.exists()){String stem=dot>0?valid.substring(0,dot):valid,ext=dot>0?valid.substring(dot):"";file=new File(folder,stem+" ("+(count++)+")"+ext);}return file;}
    static File blank(File folder,String title,int pages)throws IOException{if(pages<1||pages>200)throw new IOException("페이지는 1~200 사이로 입력하세요");File file=unique(folder,title.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")?title:title+".pdf");PdfDocument pdf=new PdfDocument();try{for(int i=0;i<pages;i++){PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,i+1).create());page.getCanvas().drawColor(Color.WHITE);pdf.finishPage(page);}try(OutputStream out=new FileOutputStream(file)){pdf.writeTo(out);}}catch(IOException|RuntimeException error){file.delete();throw error;}finally{pdf.close();}return file;}
}

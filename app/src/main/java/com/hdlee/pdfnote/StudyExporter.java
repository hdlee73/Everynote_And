package com.hdlee.pdfnote;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Portable exports: text and small OOXML workbooks, without a heavyweight Excel runtime. */
final class StudyExporter {
    static byte[] export(List<AnnotationStore.StudyEntry> entries, String title, int format) throws IOException {
        if(format==2)return workbook(entries,title);
        if(format==3)return pdf(entries,title);
        if(format==4)return docx(entries,title);
        StringBuilder out=new StringBuilder();
        if(format==0)out.append("# ").append(title).append("\n\n");
        if(format==1)out.append("\uFEFFdocument,page,text,comment\r\n");
        for(AnnotationStore.StudyEntry e:entries){
            if(format==0)out.append("## [p.").append(e.page+1).append("]\n\n").append(e.text).append("\n\n").append(e.comment).append("\n\n");
            else if(format==1)out.append(csv(title)).append(',').append(e.page+1).append(',').append(csv(e.text)).append(',').append(csv(e.comment)).append("\r\n");
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
    /** A4 PDF: title, then each entry as "[p.N]" heading, quoted text and comment. */
    private static byte[] pdf(List<AnnotationStore.StudyEntry> entries,String title)throws IOException{
        final int w=595,h=842,m=48,tw=w-2*m;PdfDocument doc=new PdfDocument();
        TextPaint head=new TextPaint(Paint.ANTI_ALIAS_FLAG),body=new TextPaint(Paint.ANTI_ALIAS_FLAG),note=new TextPaint(Paint.ANTI_ALIAS_FLAG),big=new TextPaint(Paint.ANTI_ALIAS_FLAG);
        big.setTextSize(20);big.setTypeface(Typeface.DEFAULT_BOLD);head.setTextSize(11);head.setColor(0xFF0A84FF);head.setTypeface(Typeface.DEFAULT_BOLD);body.setTextSize(12);note.setTextSize(11);note.setColor(0xFF555555);
        List<Object[]> blocks=new ArrayList<>();blocks.add(new Object[]{title,big,20});
        for(AnnotationStore.StudyEntry e:entries){blocks.add(new Object[]{"[p."+(e.page+1)+"]",head,3});blocks.add(new Object[]{e.text,body,4});if(e.comment!=null&&!e.comment.isEmpty())blocks.add(new Object[]{e.comment,note,4});blocks.get(blocks.size()-1)[2]=16;}
        int pageNo=1;PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(w,h,pageNo).create());android.graphics.Canvas canvas=page.getCanvas();int y=m;
        for(Object[] b:blocks){
            StaticLayout layout=StaticLayout.Builder.obtain((String)b[0],0,((String)b[0]).length(),(TextPaint)b[1],tw).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0,1.25f).build();
            if(y+layout.getHeight()>h-m&&y>m){doc.finishPage(page);page=doc.startPage(new PdfDocument.PageInfo.Builder(w,h,++pageNo).create());canvas=page.getCanvas();y=m;}
            canvas.save();canvas.translate(m,y);layout.draw(canvas);canvas.restore();y+=layout.getHeight()+(Integer)b[2];
        }
        doc.finishPage(page);ByteArrayOutputStream out=new ByteArrayOutputStream();try{doc.writeTo(out);}finally{doc.close();}return out.toByteArray();
    }
    /** Minimal Word (.docx) package that opens in Word, LibreOffice and Google Docs. */
    private static byte[] docx(List<AnnotationStore.StudyEntry> entries,String title)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(bytes)){
            part(z,"[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
            part(z,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
            StringBuilder b=new StringBuilder("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
            para(b,title,true,"32");
            for(AnnotationStore.StudyEntry e:entries){para(b,"[p."+(e.page+1)+"]",true,"22");para(b,e.text,false,"24");if(e.comment!=null&&!e.comment.isEmpty())para(b,e.comment,false,"22");}
            part(z,"word/document.xml",b.append("</w:body></w:document>").toString());
        }return bytes.toByteArray();
    }
    private static void para(StringBuilder b,String text,boolean bold,String size){
        b.append("<w:p><w:r><w:rPr>").append(bold?"<w:b/>":"").append("<w:sz w:val=\"").append(size).append("\"/></w:rPr>");
        String[] lines=text.replace("\r","").split("\n",-1);for(int i=0;i<lines.length;i++){if(i>0)b.append("<w:br/>");b.append("<w:t xml:space=\"preserve\">").append(xml(lines[i])).append("</w:t>");}
        b.append("</w:r></w:p>");
    }
    private static String csv(String s){if(s.matches("^[\\s]*[=+@-].*"))s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
    private static String xml(String s){return s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]","").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    private static byte[] workbook(List<AnnotationStore.StudyEntry> entries,String title)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(bytes)){
            part(z,"[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            part(z,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            part(z,"xl/workbook.xml","<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Notes\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            part(z,"xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            StringBuilder sheet=new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            row(sheet,1,new String[]{"document","page","text","comment"});int index=2;
            for(AnnotationStore.StudyEntry e:entries)row(sheet,index++,new String[]{title,String.valueOf(e.page+1),e.text,e.comment});
            part(z,"xl/worksheets/sheet1.xml",sheet.append("</sheetData></worksheet>").toString());
        }return bytes.toByteArray();
    }
    private static void row(StringBuilder b,int row,String[] values){b.append("<row r=\"").append(row).append("\">");for(int i=0;i<values.length;i++)b.append("<c r=\"").append((char)('A'+i)).append(row).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(values[i])).append("</t></is></c>");b.append("</row>");}
    private static void part(ZipOutputStream z,String name,String text)throws IOException{z.putNextEntry(new ZipEntry(name));z.write(text.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
}

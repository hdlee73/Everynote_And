package com.hdlee.pdfnote;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Portable exports: text and small OOXML workbooks, without a heavyweight Excel runtime. */
final class StudyExporter {
    static byte[] export(List<AnnotationStore.StudyEntry> entries, String title, int format) throws IOException {
        if(format==2)return workbook(entries,title);
        StringBuilder out=new StringBuilder();
        if(format==0)out.append("# ").append(title).append("\n\n");
        if(format==1)out.append("\uFEFFdocument,page,text,comment\r\n");
        for(AnnotationStore.StudyEntry e:entries){
            if(format==0)out.append("## [p.").append(e.page+1).append("]\n\n").append(e.text).append("\n\n").append(e.comment).append("\n\n");
            else if(format==1)out.append(csv(title)).append(',').append(e.page+1).append(',').append(csv(e.text)).append(',').append(csv(e.comment)).append("\r\n");
            else out.append(html(e.text)).append('\t').append(html(e.comment)+"<br>"+html(title)+" · p."+(e.page+1)).append('\n');
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static String csv(String s){if(s.matches("^[\\s]*[=+@-].*"))s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
    private static String html(String s){return xml(s).replace("\r","").replace("\n","<br>").replace("\t","&#9;");}
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

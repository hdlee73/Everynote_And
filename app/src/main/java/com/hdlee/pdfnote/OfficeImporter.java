package com.hdlee.pdfnote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import org.apache.poi.hwpf.extractor.WordExtractor;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/** Text-focused, offline preview for legacy office formats. The source document is never modified. */
final class OfficeImporter {
    private OfficeImporter() {}

    static boolean isOffice(String name) {
        String lower=name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".hwp")||lower.endsWith(".doc");
    }

    static File createPreview(Context context, Uri uri, String name) throws Exception {
        File input=File.createTempFile("office-input-",".bin",context.getCacheDir());
        File output=File.createTempFile("office-preview-",".pdf",context.getCacheDir());
        boolean success=false;
        try {
            try(InputStream source=context.getContentResolver().openInputStream(uri);
                OutputStream target=new FileOutputStream(input)) {
                if(source==null)throw new IOException("파일을 읽을 수 없습니다");
                byte[] buffer=new byte[16384];int count;long total=0;
                while((count=source.read(buffer))!=-1){total+=count;if(total>24L*1024*1024)throw new IOException("24MB 이하 문서를 선택하세요");target.write(buffer,0,count);}
            }
            String text;
            if(name.toLowerCase(Locale.ROOT).endsWith(".hwp")) {
                text=TextExtractor.extract(HWPReader.fromFile(input.getAbsolutePath()),TextExtractMethod.InsertControlTextBetweenParagraphText);
            }else{
                try(InputStream stream=new FileInputStream(input);WordExtractor extractor=new WordExtractor(stream)){
                    text=extractor.getText();
                }
            }
            if(text==null||text.trim().isEmpty())throw new IOException("추출할 본문 글자가 없습니다");
            writeTextPdf(text,output);
            success=true;return output;
        }finally{
            input.delete();if(!success)output.delete();
        }
    }

    private static void writeTextPdf(String content,File file) throws IOException {
        PdfDocument document=new PdfDocument();
        Paint titlePaint=new Paint(Paint.ANTI_ALIAS_FLAG),bodyPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(0xFF173B63);titlePaint.setTextSize(15);titlePaint.setTypeface(Typeface.DEFAULT_BOLD);
        bodyPaint.setColor(Color.BLACK);bodyPaint.setTextSize(12.5f);bodyPaint.setTypeface(Typeface.DEFAULT);
        PdfDocument.Page page=null;Canvas canvas=null;int pageNumber=0;float y=0;
        try {
            String cleaned=content.replace("\r\n","\n").replace('\r','\n').replace('\u0000',' ');
            for(String paragraph:cleaned.split("\n",-1)){
                if(page==null){page=document.startPage(new PdfDocument.PageInfo.Builder(595,842,++pageNumber).create());canvas=page.getCanvas();canvas.drawColor(Color.WHITE);canvas.drawText("문서 본문 미리보기",46,45,titlePaint);y=80;}
                String line=paragraph.trim();
                if(line.isEmpty()) {y+=12;continue;}
                while(!line.isEmpty()){
                    if(y>784){document.finishPage(page);page=null;canvas=null;page=document.startPage(new PdfDocument.PageInfo.Builder(595,842,++pageNumber).create());canvas=page.getCanvas();canvas.drawColor(Color.WHITE);y=58;}
                    int fit=bodyPaint.breakText(line,true,503,null);
                    if(fit<=0)fit=1;
                    if(fit<line.length()){
                        int space=line.lastIndexOf(' ',fit-1);
                        if(space>fit/2)fit=space;
                    }
                    canvas.drawText(line.substring(0,fit),46,y,bodyPaint);
                    line=line.substring(fit).trim();y+=20;
                }
                y+=5;
            }
            if(page!=null)document.finishPage(page);
            try(OutputStream target=new FileOutputStream(file)){document.writeTo(target);}
        }finally{document.close();}
    }
}

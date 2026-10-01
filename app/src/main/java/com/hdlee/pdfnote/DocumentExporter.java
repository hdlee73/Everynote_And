package com.hdlee.pdfnote;
import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
final class DocumentExporter {
    static void export(Context c,Uri source,AnnotationStore annotations,OutputStream output)throws IOException{
        ParcelFileDescriptor descriptor="file".equals(source.getScheme())?ParcelFileDescriptor.open(new File(source.getPath()),ParcelFileDescriptor.MODE_READ_ONLY):c.getContentResolver().openFileDescriptor(source,"r");
        if(descriptor==null)throw new IOException("PDF를 읽을 수 없습니다");
        PdfDocument pdf=new PdfDocument();try(PdfRenderer renderer=new PdfRenderer(descriptor)){
            for(int i=0;i<renderer.getPageCount();i++)try(PdfRenderer.Page original=renderer.openPage(i)){
                PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(original.getWidth(),original.getHeight(),i+1).create());
                float ratio=Math.min(2f,2048f/Math.max(original.getWidth(),original.getHeight()));Bitmap image=Bitmap.createBitmap(Math.max(1,Math.round(original.getWidth()*ratio)),Math.max(1,Math.round(original.getHeight()*ratio)),Bitmap.Config.ARGB_8888);
                try{image.eraseColor(Color.WHITE);Matrix matrix=new Matrix();matrix.postScale(ratio,ratio);original.render(image,null,matrix,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);RectF rect=new RectF(0,0,original.getWidth(),original.getHeight());page.getCanvas().drawBitmap(image,null,rect,new Paint(Paint.FILTER_BITMAP_FLAG));AnnotationPainter.all(c,page.getCanvas(),rect,annotations,i);pdf.finishPage(page);}finally{image.recycle();}
            }
            pdf.writeTo(output);
        }finally{pdf.close();}
    }
}

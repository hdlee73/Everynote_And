package com.hdlee.pdfnote;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import java.io.File;

/** Keeps the native engine out of the PDF viewer process. */
public final class OfficeConversionService extends Service {
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        ResultReceiver receiver=intent.getParcelableExtra("receiver");
        String source=intent.getStringExtra("source"),output=intent.getStringExtra("output");
        new Thread(()->{
            Bundle result=new Bundle();int code=0;
            try{
                if(source==null||output==null)throw new IllegalArgumentException("변환 경로가 없습니다");
                OfficeEngine.convert(this,new File(source),new File(output));
                result.putString("output",output);
            }catch(Throwable error){code=1;result.putString("error",error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());}
            if(receiver!=null)receiver.send(code,result);
            stopSelf(startId);
        },"office-conversion").start();
        return START_NOT_STICKY;
    }
}

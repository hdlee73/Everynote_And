package com.hdlee.pdfnote;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import java.io.File;

/** Keeps the native engine out of the PDF viewer process. */
public final class OfficeConversionService extends Service {
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        ResultReceiver receiver=intent.getParcelableExtra("receiver");
        String source=intent.getStringExtra("source"),output=intent.getStringExtra("output");
        worker.execute(()->{
            Bundle result=new Bundle();int code=0;
            try{
                if(source==null||output==null)throw new IllegalArgumentException("변환 경로가 없습니다");
                OfficeEngine.install(this,stage->{if(receiver!=null){Bundle update=new Bundle();update.putString("stage",stage);receiver.send(2,update);}});
                if(receiver!=null){Bundle update=new Bundle();update.putString("stage","원본 서식을 PDF로 변환하는 중");receiver.send(2,update);}
                OfficeEngine.convert(this,new File(source),new File(output));
                result.putString("output",output);
            }catch(Throwable error){code=1;result.putString("error",error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());}
            if(receiver!=null)receiver.send(code,result);
            stopSelf(startId);
        });
        return START_NOT_STICKY;
    }
    @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->android.os.Process.killProcess(android.os.Process.myPid()),250);}
}

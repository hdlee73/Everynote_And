package com.hdlee.pdfnote;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import java.util.UUID;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class CaptureProviderTest {
    @Test public void onlyCaptureFilesAreReadable()throws Exception{
        Context context=RuntimeEnvironment.getApplication();File directory=new File(context.getCacheDir(),"captures");directory.mkdirs();File file=new File(directory,UUID.randomUUID()+".png");try(FileOutputStream out=new FileOutputStream(file)){out.write(new byte[]{1,2,3});}
        CaptureProvider provider=new CaptureProvider();android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo();info.authority=context.getPackageName()+".captures";provider.attachInfo(context,info);
        Uri uri=CaptureProvider.uri(context,file);assertEquals("image/png",provider.getType(uri));try(ParcelFileDescriptor fd=provider.openFile(uri,"r")){assertEquals(3,fd.getStatSize());}
        try{provider.openFile(uri,"rw");fail();}catch(FileNotFoundException expected){}
        try{provider.openFile(Uri.parse("content://"+context.getPackageName()+".captures/%2E%2E%2Fsecret.png"),"r");fail();}catch(FileNotFoundException expected){}
        try{provider.openFile(Uri.parse("content://"+context.getPackageName()+".captures/other.txt"),"r");fail();}catch(FileNotFoundException expected){}
    }
}

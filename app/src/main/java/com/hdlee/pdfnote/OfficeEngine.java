package com.hdlee.pdfnote;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.system.Os;
import android.system.OsConstants;

import org.libreoffice.kit.Document;
import org.libreoffice.kit.LibreOfficeKit;
import org.libreoffice.kit.Office;
import org.tukaani.xz.XZInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;

/** Optional LibreOfficeKit Android runtime. Documents remain on the device. */
final class OfficeEngine {
    private OfficeEngine(){}
    private static final String VERSION="lo-lite-2.0";
    private static final String FROM="/data/data/vasuki.istanpdf/files";
    private static final int ALIAS_FD=1023;
    private static Office office;
    interface Progress { void report(String stage); }
    private static final class Bundle {
        final String abi,url,sha;final long bytes;
        Bundle(String a,String u,long b,String s){abi=a;url=u;bytes=b;sha=s;}
    }
    private static Bundle bundle(){
        String abi=(Process.is64Bit()?Build.SUPPORTED_64_BIT_ABIS:Build.SUPPORTED_32_BIT_ABIS)[0];
        if("arm64-v8a".equals(abi))return new Bundle(abi,"https://github.com/vasuki-re/LibreOffice-Lite/releases/download/v2.0/LibreOffice-arm64.tar.xz",47521152L,"6665ad47db41d116248c40e6fc608a3425b9eb4a304f5ad5e5d5c80dbea61e30");
        if("armeabi-v7a".equals(abi))return new Bundle(abi,"https://github.com/vasuki-re/LibreOffice-Lite/releases/download/v2.0/LibreOffice-arm.tar.xz",46666612L,"7546e244f66c8c1b5a5a4ede7cb26c8f7e767879bbf3b39d405a5d8fa148b045");
        return null;
    }
    static boolean supported(){return bundle()!=null;}
    static File root(Context c){return new File(c.getNoBackupFilesDir(),"office-engine");}
    static File installed(Context c){File dir=new File(root(c),VERSION+"-"+bundle().abi);
        return new File(dir,".ready").isFile()&&new File(dir,"lib/liblo-native-code.so").isFile()&&new File(dir,"program/fundamentalrc").isFile()?dir:null;}
    static void install(Context context,Progress progress) throws Exception {
        Bundle b=bundle();if(b==null)throw new IOException("이 기기에서 지원되지 않는 CPU 형식입니다");
        if(installed(context)!=null)return;
        File root=root(context);if(!root.exists()&&!root.mkdirs())throw new IOException("엔진 저장 공간을 만들 수 없습니다");
        File archive=new File(root,b.sha+".tar.xz");
        if(!archive.isFile()||archive.length()!=b.bytes||!b.sha.equals(sha256(archive))){
            archive.delete();progress.report("변환 엔진 다운로드 중 · 약 48 MB");
            HttpURLConnection conn=(HttpURLConnection)new URL(b.url).openConnection();
            conn.setConnectTimeout(20000);conn.setReadTimeout(60000);conn.setInstanceFollowRedirects(true);
            try{if(conn.getResponseCode()!=200)throw new IOException("다운로드 HTTP "+conn.getResponseCode());
                try(InputStream in=conn.getInputStream();OutputStream out=new FileOutputStream(archive)){copy(in,out,-1);}
            }finally{conn.disconnect();}
        }
        if(archive.length()!=b.bytes||!b.sha.equals(sha256(archive))){archive.delete();throw new IOException("변환 엔진 검증에 실패했습니다");}
        progress.report("변환 엔진 설치 중");
        File stage=new File(root,"staging");deleteTree(stage);if(!stage.mkdirs())throw new IOException("설치 공간을 만들 수 없습니다");
        try(InputStream in=new XZInputStream(new BufferedInputStream(new java.io.FileInputStream(archive),65536),300*1024)){
            extract(in,stage,b.abi);
            File lib=new File(stage,"lib");
            if(!new File(lib,"liblo-native-code.so").isFile()||!new File(lib,"libc++_shared.so").isFile()||!new File(stage,"program/fundamentalrc").isFile())throw new IOException("엔진 파일이 누락되었습니다");
            for(File f:lib.listFiles()){if(f.getName().endsWith(".so"))patch(f);}
            new File(stage,".ready").createNewFile();
            File dest=new File(root,VERSION+"-"+b.abi);deleteTree(dest);
            if(!stage.renameTo(dest))throw new IOException("엔진 설치를 완료할 수 없습니다");
            archive.delete();
        }catch(Exception e){deleteTree(stage);throw e;}
    }
    private static String sha256(File f)throws Exception{
        MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] buf=new byte[65536];
        try(InputStream in=new java.io.FileInputStream(f)){int n;while((n=in.read(buf))!=-1)d.update(buf,0,n);}
        StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();
    }
    private static void copy(InputStream in,OutputStream out,long count)throws IOException{
        byte[] b=new byte[65536];long left=count;int n;
        while(left!=0&&(n=in.read(b,0,(int)(left<0?b.length:Math.min(b.length,left))))!=-1){out.write(b,0,n);if(left>0)left-=n;}
        if(left>0)throw new IOException("압축 파일이 손상되었습니다");
    }
    private static boolean read(InputStream in,byte[] buf)throws IOException{int pos=0,n;while(pos<buf.length){n=in.read(buf,pos,buf.length-pos);if(n<0)return false;pos+=n;}return true;}
    private static String field(byte[] b,int off,int size){int end=off;while(end<off+size&&b[end]!=0)end++;return new String(b,off,end-off,java.nio.charset.StandardCharsets.UTF_8);}
    private static void extract(InputStream in,File root,String abi)throws IOException{
        byte[] h=new byte[512];String longName=null;int files=0;
        while(read(in,h)){
            boolean empty=true;for(byte v:h)if(v!=0){empty=false;break;}if(empty)break;
            String name=longName==null?field(h,0,100):longName;longName=null;
            String sizeText=field(h,124,12).trim();long size=Long.parseLong(sizeText.isEmpty()?"0":sizeText,8);
            long padded=(size+511)/512*512;char type=(char)h[156];
            if(type=='L'){if(size>8192)throw new IOException("압축 파일 이름 오류");byte[] nameBytes=new byte[(int)size];if(!read(in,nameBytes))throw new IOException("압축 파일 손상");longName=new String(nameBytes,java.nio.charset.StandardCharsets.UTF_8).replace("\u0000","");skip(in,padded-size);continue;}
            String rel=name.replace('\\','/');while(rel.startsWith("./")||rel.startsWith("/"))rel=rel.startsWith("./")?rel.substring(2):rel.substring(1);
            String mapped=rel.startsWith(abi+"/")?"lib/"+rel.substring(abi.length()+1):rel.startsWith("unpack/")?rel.substring(7):(rel.startsWith("program/")||rel.startsWith("share/"))?rel:null;
            File target=mapped==null?null:new File(root,mapped);
            if((type!='0'&&type!=0)||target==null||mapped.endsWith("/")){skip(in,padded);continue;}
            if(!target.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator))throw new IOException("압축 경로 오류");
            if(!target.getParentFile().exists()&&!target.getParentFile().mkdirs())throw new IOException("파일 생성 실패");
            try(OutputStream out=new FileOutputStream(target)){copy(in,out,size);}skip(in,padded-size);files++;
        }
        if(files==0)throw new IOException("변환 엔진 내용이 없습니다");
    }
    private static void skip(InputStream in,long bytes)throws IOException{byte[] buf=new byte[8192];while(bytes>0){int n=in.read(buf,0,(int)Math.min(buf.length,bytes));if(n<0)throw new IOException("압축 파일 손상");bytes-=n;}}
    private static void deleteTree(File f){if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File child:children)deleteTree(child);}f.delete();}
    private static void patch(File file)throws IOException{
        byte[] from=FROM.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String base="/proc/self/fd/"+ALIAS_FD;int pad=from.length-base.length();
        if(pad<0||pad%2!=0)throw new IOException("엔진 경로 오류");
        byte[] to=(base+new String(new char[pad/2]).replace("\u0000","/.")).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        try(RandomAccessFile raf=new RandomAccessFile(file,"rw")){
            byte[] b=new byte[1048576+from.length];long pos=0;int carry=0,n;
            while((n=raf.read(b,carry,1048576))>0){int valid=carry+n;
                for(int i=0;i<=valid-from.length;i++)if(b[i]==from[0]&&match(b,i,from)){
                    long next=raf.getFilePointer();raf.seek(pos-carry+i);raf.write(to);raf.seek(next);i+=from.length-1;
                }
                carry=Math.min(from.length-1,valid);System.arraycopy(b,valid-carry,b,0,carry);pos+=n;
            }
        }
    }
    private static boolean match(byte[] b,int p,byte[] f){for(int i=0;i<f.length;i++)if(b[p+i]!=f[i])return false;return true;}
    private static void pin(File dir)throws Exception{
        String path="/proc/self/fd/"+ALIAS_FD;
        try{String existing=Os.readlink(path);if(!new File(existing).getCanonicalPath().equals(dir.getCanonicalPath()))throw new IOException("변환 엔진 파일 충돌");return;}
        catch(android.system.ErrnoException ignored){}
        java.io.FileDescriptor fd=Os.open(dir.getAbsolutePath(),OsConstants.O_RDONLY,0);Os.dup2(fd,ALIAS_FD);
    }
    private static Office start(Context c)throws Exception{
        if(office!=null)return office;File dir=installed(c);if(dir==null)throw new IOException("변환 엔진이 설치되지 않았습니다");
        pin(dir);System.load(new File(dir,"lib/libc++_shared.so").getAbsolutePath());
        System.load(new File(dir,"lib/liblo-native-code.so").getAbsolutePath());
        File temp=new File(c.getCacheDir(),"office-tmp");temp.mkdirs();
        File fontDir=new File(dir,"etc/fonts");fontDir.mkdirs();
        File fontFile=new File(fontDir,"fonts.conf");
        String xml="<?xml version=\"1.0\"?><fontconfig><dir>/system/fonts</dir><dir>"+new File(dir,"user/fonts").getAbsolutePath()+"</dir><dir>"+new File(dir,"share/fonts/truetype").getAbsolutePath()+"</dir><cachedir>"+temp.getAbsolutePath()+"</cachedir>"
                +"<alias><family>Calibri</family><prefer><family>Carlito</family></prefer></alias>"
                +"<alias><family>Cambria</family><prefer><family>Caladea</family></prefer></alias>"
                +"<alias><family>Arial</family><prefer><family>Liberation Sans</family></prefer></alias>"
                +"<alias><family>Times New Roman</family><prefer><family>Liberation Serif</family></prefer></alias></fontconfig>";
        try(OutputStream out=new FileOutputStream(fontFile)){out.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        for(String env:new String[]{"FONTCONFIG_FILE="+fontFile.getAbsolutePath(),"FONTCONFIG_PATH="+fontDir.getAbsolutePath(),"TMPDIR="+temp.getAbsolutePath(),"HOME="+c.getFilesDir().getAbsolutePath()})LibreOfficeKit.putenv(env);
        LibreOfficeKit.putenv("SAL_LOG=-WARN-INFO");LibreOfficeKit.redirectStdio(true);
        if(!LibreOfficeKit.initializeNative(dir.getAbsolutePath(),temp.getAbsolutePath(),c.getPackageResourcePath(),c.getAssets()))throw new IOException("변환 엔진 초기화 실패");
        ByteBuffer handle=LibreOfficeKit.getLibreOfficeKitHandle();if(handle==null)throw new IOException("변환 엔진 초기화 실패");
        office=new Office(handle);return office;
    }
    static void convert(Context c,File input,File output)throws Exception{
        Office o=start(c);String url=Uri.fromFile(input).toString();
        o.setOptionalFeatures(Office.FEATURE_DOCUMENT_PASSWORD);
        o.callback=(type,payload)->{if(type==Office.LOK_CALLBACK_DOCUMENT_PASSWORD)o.setDocumentPassword(url,null);};
        try{
            Document document=o.documentLoad(url);if(document==null)throw new IOException("문서를 열 수 없습니다: "+o.getError());
            try{output.delete();document.saveAs(Uri.fromFile(output).toString(),"pdf",null);}finally{document.destroy();}
        }finally{o.callback=null;}
        if(!output.isFile()||output.length()==0)throw new IOException("PDF 변환 결과가 비어 있습니다");
    }
}

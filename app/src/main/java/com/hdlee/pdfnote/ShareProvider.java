package com.hdlee.pdfnote;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.List;

/** Read-only access to library PDFs (and inserted videos, for an external player) so they can be handed to other apps. */
public final class ShareProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    static Uri uri(Context context,File file)throws IOException{
        File root=NotebookFiles.root(context).getCanonicalFile(),target=file.getCanonicalFile();String rootPath=root.getPath()+File.separator;
        if(!target.getPath().startsWith(rootPath))throw new IOException("문서함 밖의 파일입니다");
        Uri.Builder builder=new Uri.Builder().scheme("content").authority(context.getPackageName()+".shared");for(String part:target.getPath().substring(rootPath.length()).split(File.separator))builder.appendPath(part);return builder.build();
    }
    /** An inserted video (files/videos/<uuid>.mp4). The ".video" segment can never be a library folder (hidden names are refused below). */
    static Uri videoUri(Context context,File file){return new Uri.Builder().scheme("content").authority(context.getPackageName()+".shared").appendPath(VIDEO).appendPath(file.getName()).build();}
    private static final String VIDEO=".video";
    /** The real container from the first bytes: videos are stored as .mp4 whatever they are, so players get the right type. */
    static String videoMime(File file){
        byte[] h=new byte[16];int n=0;try(InputStream in=new FileInputStream(file)){n=in.read(h);}catch(IOException ignored){}
        if(n>=12&&h[0]=='R'&&h[1]=='I'&&h[2]=='F'&&h[3]=='F'&&h[8]=='A'&&h[9]=='V'&&h[10]=='I')return "video/x-msvideo";
        if(n>=4&&(h[0]&0xFF)==0x30&&(h[1]&0xFF)==0x26&&(h[2]&0xFF)==0xB2&&(h[3]&0xFF)==0x75)return "video/x-ms-wmv";
        if(n>=4&&(h[0]&0xFF)==0x1A&&(h[1]&0xFF)==0x45&&(h[2]&0xFF)==0xDF&&(h[3]&0xFF)==0xA3)return "video/x-matroska";
        if(n>=3&&h[0]=='F'&&h[1]=='L'&&h[2]=='V')return "video/x-flv";
        if(n>=10&&h[4]=='f'&&h[5]=='t'&&h[6]=='y'&&h[7]=='p')return h[8]=='q'&&h[9]=='t'?"video/quicktime":"video/mp4";
        if(n>=4&&h[0]==0&&h[1]==0&&h[2]==1&&((h[3]&0xFF)==0xBA||(h[3]&0xFF)==0xB3))return "video/mpeg";
        if(n>=1&&(h[0]&0xFF)==0x47)return "video/mp2t";
        return "video/mp4";
    }
    private static String videoExtension(String mime){return mime.contains("msvideo")?".avi":mime.contains("wmv")?".wmv":mime.contains("matroska")?".mkv":mime.contains("flv")?".flv":mime.contains("quicktime")?".mov":mime.equals("video/mpeg")?".mpg":mime.contains("mp2t")?".ts":".mp4";}
    private File video(Context context,Uri uri)throws FileNotFoundException{
        List<String> parts=uri.getPathSegments();if(parts.size()!=2||!VIDEO.equals(parts.get(0))||!parts.get(1).matches("[a-f0-9-]{36}\\.mp4"))throw new FileNotFoundException("Invalid video");
        File dir=new File(context.getFilesDir(),"videos"),file=new File(dir,parts.get(1));if(!file.isFile())throw new FileNotFoundException("Video not found");return file;
    }
    private boolean isVideo(Uri uri){return uri.getPathSegments().size()>0&&VIDEO.equals(uri.getPathSegments().get(0));}
    private File resolve(Uri uri)throws FileNotFoundException{
        Context context=getContext();if(context==null||!"content".equals(uri.getScheme())||!(context.getPackageName()+".shared").equals(uri.getAuthority())||uri.getPathSegments().isEmpty())throw new FileNotFoundException("Invalid share URI");
        if(isVideo(uri))return video(context,uri);
        try{File root=NotebookFiles.root(context).getCanonicalFile();File file=root;for(String part:uri.getPathSegments()){if(part.isEmpty()||part.equals("..")||part.equals("."))throw new FileNotFoundException("Invalid path");file=new File(file,part);}
            file=file.getCanonicalFile();String relative=file.getPath();if(!relative.startsWith(root.getPath()+File.separator)||relative.substring(root.getPath().length()+1).startsWith(".")||!file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")||!file.isFile())throw new FileNotFoundException("Document not found");return file;
        }catch(IOException error){throw new FileNotFoundException(error.getMessage());}
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("Read-only");return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public String getType(Uri uri){if(isVideo(uri))try{return videoMime(resolve(uri));}catch(FileNotFoundException error){return "video/*";}return "application/pdf";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        try{File file=resolve(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns);Object[] row=new Object[columns.length];
            for(int i=0;i<columns.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]=isVideo(uri)?file.getName().replaceAll("\\.mp4$","")+videoExtension(videoMime(file)):file.getName();else if(OpenableColumns.SIZE.equals(columns[i]))row[i]=file.length();}cursor.addRow(row);return cursor;
        }catch(FileNotFoundException error){return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read-only");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("Read-only");}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("Read-only");}
}

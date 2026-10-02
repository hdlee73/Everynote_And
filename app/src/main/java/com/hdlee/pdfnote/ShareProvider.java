package com.hdlee.pdfnote;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Read-only access to library PDFs so they can be handed to other apps' share sheets. */
public final class ShareProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    static Uri uri(Context context,File file)throws IOException{
        File root=NotebookFiles.root(context).getCanonicalFile(),target=file.getCanonicalFile();String rootPath=root.getPath()+File.separator;
        if(!target.getPath().startsWith(rootPath))throw new IOException("문서함 밖의 파일입니다");
        Uri.Builder builder=new Uri.Builder().scheme("content").authority(context.getPackageName()+".shared");for(String part:target.getPath().substring(rootPath.length()).split(File.separator))builder.appendPath(part);return builder.build();
    }
    private File resolve(Uri uri)throws FileNotFoundException{
        Context context=getContext();if(context==null||!"content".equals(uri.getScheme())||!(context.getPackageName()+".shared").equals(uri.getAuthority())||uri.getPathSegments().isEmpty())throw new FileNotFoundException("Invalid share URI");
        try{File root=NotebookFiles.root(context).getCanonicalFile();File file=root;for(String part:uri.getPathSegments()){if(part.isEmpty()||part.equals("..")||part.equals("."))throw new FileNotFoundException("Invalid path");file=new File(file,part);}
            file=file.getCanonicalFile();String relative=file.getPath();if(!relative.startsWith(root.getPath()+File.separator)||relative.substring(root.getPath().length()+1).startsWith(".")||!file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")||!file.isFile())throw new FileNotFoundException("Document not found");return file;
        }catch(IOException error){throw new FileNotFoundException(error.getMessage());}
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("Read-only");return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public String getType(Uri uri){return "application/pdf";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        try{File file=resolve(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns);Object[] row=new Object[columns.length];
            for(int i=0;i<columns.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]=file.getName();else if(OpenableColumns.SIZE.equals(columns[i]))row[i]=file.length();}cursor.addRow(row);return cursor;
        }catch(FileNotFoundException error){return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read-only");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("Read-only");}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("Read-only");}
}

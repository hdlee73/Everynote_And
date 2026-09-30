package com.hdlee.pdfnote;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Only temporary capture PNGs can be opened through a granted read URI. */
public final class CaptureProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    static Uri uri(Context context,File file){return new Uri.Builder().scheme("content").authority(context.getPackageName()+".captures").appendPath(file.getName()).build();}
    private File resolve(Uri uri)throws FileNotFoundException{
        if(!"content".equals(uri.getScheme())||!(getContext().getPackageName()+".captures").equals(uri.getAuthority())||uri.getPathSegments().size()!=1)throw new FileNotFoundException("Invalid capture URI");
        String name=uri.getLastPathSegment();if(name==null||!name.matches("[a-f0-9-]{36}\\.png"))throw new FileNotFoundException("Invalid capture name");
        File file=new File(new File(getContext().getCacheDir(),"captures"),name);
        try{if(!file.getCanonicalFile().getParentFile().equals(new File(getContext().getCacheDir(),"captures").getCanonicalFile())||!file.isFile())throw new FileNotFoundException("Capture not found");}catch(IOException error){throw new FileNotFoundException(error.getMessage());}
        return file;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("Captures are read-only");return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public String getType(Uri uri){return "image/png";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        try{File file=resolve(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]=file.getName();else if(OpenableColumns.SIZE.equals(columns[i]))row[i]=file.length();}cursor.addRow(row);return cursor;}catch(FileNotFoundException error){return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read-only");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("Read-only");}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("Read-only");}
}

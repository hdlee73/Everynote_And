package com.hdlee.pdfnote;

import android.content.*;
import android.net.Uri;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/** Owns the app's permanent PDF copies. All file mutations are serialized. */
final class LibraryRepository {
    private final Context context;
    private final SharedPreferences preferences;
    final File root;
    LibraryRepository(Context context){this.context=context.getApplicationContext();root=NotebookFiles.root(context);preferences=context.getSharedPreferences("pdf_note_library",Context.MODE_PRIVATE);}
    boolean managed(Uri uri){return "file".equals(uri.getScheme())&&uri.getPath()!=null&&managed(new File(uri.getPath()));}
    boolean managed(File file){try{return file.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator);}catch(IOException error){return false;}}
    private void folder(File folder)throws IOException{if(!(folder.equals(root)||managed(folder))||!folder.isDirectory())throw new IOException("문서함 폴더를 선택하세요");}
    List<File> list(File directory){File[] files=directory.listFiles(f->!f.getName().startsWith(".")&&(f.isDirectory()||f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")));List<File> result=files==null?new ArrayList<>():new ArrayList<>(Arrays.asList(files));result.sort(Comparator.comparing((File f)->!f.isDirectory()).thenComparing(File::getName,String.CASE_INSENSITIVE_ORDER));return result;}
    static final String[] SORT_NAMES={"이름 오름차순","이름 내림차순","최근 수정","최근 열기"};
    static final String[] VIEW_NAMES={"큰 표지","작은 표지","목록"};
    static final int[] FOLDER_COLORS={0xFF5C86BE,0xFFDA9A43,0xFF54A485,0xFF9272C3,0xFFD36D86,0xFF718096};
    static final String[] FOLDER_COLOR_NAMES={"블루","오렌지","그린","퍼플","로즈","그레이"};
    int sortMode(){return Math.max(0,Math.min(3,preferences.getInt("sort",0)));}
    void sortMode(int mode){preferences.edit().putInt("sort",mode).apply();}
    int viewMode(){return Math.max(0,Math.min(2,preferences.getInt("view",0)));}
    void viewMode(int mode){preferences.edit().putInt("view",mode).apply();}
    int folderColor(File folder){return preferences.getInt("color:"+folder.getAbsolutePath(),FOLDER_COLORS[0]);}
    void folderColor(File folder,int color){preferences.edit().putInt("color:"+folder.getAbsolutePath(),color).apply();}
    void opened(Uri uri){if(managed(uri))preferences.edit().putLong("opened:"+uri.getPath(),System.currentTimeMillis()).apply();}
    List<File> sorted(File directory,String query){
        List<File> items=list(directory);String q=query.trim().toLowerCase(Locale.ROOT);items.removeIf(f->!f.getName().toLowerCase(Locale.ROOT).contains(q));
        Comparator<File> byName=Comparator.comparing(File::getName,String.CASE_INSENSITIVE_ORDER);Comparator<File> order=byName;
        switch(sortMode()){case 1:order=byName.reversed();break;case 2:order=Comparator.comparingLong(this::modified).reversed().thenComparing(byName);break;case 3:order=Comparator.comparingLong((File f)->preferences.getLong("opened:"+f.getAbsolutePath(),0)).reversed().thenComparing(byName);break;}
        items.sort(Comparator.comparing((File f)->!f.isDirectory()).thenComparing(order));return items;
    }
    private long modified(File file){return Math.max(file.lastModified(),AnnotationStore.modified(context,Uri.fromFile(file)));}
    private File trashFolder(){File folder=new File(root,".trash");folder.mkdirs();return folder;}
    private boolean inTrash(File file){try{return file.getCanonicalPath().startsWith(new File(root,".trash").getCanonicalPath()+File.separator);}catch(IOException error){return true;}}
    static final class TrashItem {final File file;final String name,parent;final long deleted;TrashItem(File file,String name,String parent,long deleted){this.file=file;this.name=name;this.parent=parent;this.deleted=deleted;}}
    synchronized File trash(File source)throws IOException,JSONException{
        if(inTrash(source))throw new IOException("이미 휴지통에 있습니다");String name=source.getName(),parent=source.getParent();File saved=transfer(source,trashFolder(),UUID.randomUUID()+".pdf",true);
        String info=new JSONObject().put("name",name).put("parent",parent).put("deleted",System.currentTimeMillis()).toString();preferences.edit().putString("trash:"+saved.getAbsolutePath(),info).commit();return saved;
    }
    List<TrashItem> trashItems(){List<TrashItem> result=new ArrayList<>();for(File file:list(trashFolder()))if(file.isFile()){try{JSONObject info=new JSONObject(preferences.getString("trash:"+file.getAbsolutePath(),"{}"));result.add(new TrashItem(file,info.optString("name",file.getName()),info.optString("parent",root.getAbsolutePath()),info.optLong("deleted",file.lastModified())));}catch(JSONException ignored){result.add(new TrashItem(file,file.getName(),root.getAbsolutePath(),file.lastModified()));}}result.sort(Comparator.comparingLong((TrashItem t)->t.deleted).reversed());return result;}
    synchronized File restore(TrashItem item)throws IOException,JSONException{
        if(!inTrash(item.file))throw new IOException("휴지통 문서가 아닙니다");File destination=new File(item.parent);if(!destination.isDirectory()||!(destination.equals(root)||managed(destination)))destination=root;File target=NotebookFiles.unique(destination,NotebookFiles.pdfName(item.name));File restored=transfer(item.file,destination,target.getName(),true);preferences.edit().remove("trash:"+item.file.getAbsolutePath()).commit();return restored;
    }
    synchronized File createFolder(File parent,String title)throws IOException{folder(parent);File result=new File(parent,NotebookFiles.name(title));if(result.exists()||!result.mkdir())throw new IOException("같은 이름의 폴더가 있습니다");return result;}
    synchronized File createNote(File parent,String title,NotebookFiles.Paper paper)throws IOException{folder(parent);File file=NotebookFiles.create(parent,title,1,paper);putPaper(file,paper);return file;}
    NotebookFiles.Paper paper(File file){String value=preferences.getString("paper:"+file.getAbsolutePath(),null);if(value==null)return null;try{String[] parts=value.split(":");return new NotebookFiles.Paper(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]));}catch(RuntimeException error){return null;}}
    private void putPaper(File file,NotebookFiles.Paper paper){preferences.edit().putString("paper:"+file.getAbsolutePath(),paper.kind+":"+paper.color).commit();}
    File imported(Uri uri){String path=preferences.getString("source:"+uri.toString(),null);File file=path==null?null:new File(path);return file!=null&&managed(file)&&!inTrash(file)&&file.isFile()?file:null;}
    synchronized File importPdf(Uri source,String title,File destination)throws IOException,JSONException{
        folder(destination);File existing=imported(source);if(existing!=null)return existing;
        String safe=title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_");if(safe.trim().isEmpty())safe="문서.pdf";if(safe.length()>90)safe=safe.substring(0,86)+".pdf";
        File target=NotebookFiles.unique(destination,NotebookFiles.pdfName(safe));File temp=File.createTempFile(".import-",".tmp",destination);int count;NotebookFiles.Paper paper=null;
        try{
            try(InputStream in="file".equals(source.getScheme())?new FileInputStream(source.getPath()):context.getContentResolver().openInputStream(source);OutputStream out=new FileOutputStream(temp)){
                if(in==null)throw new IOException("PDF를 읽을 수 없습니다");copy(in,out);
            }
            try(PDDocument pdf=PDDocument.load(temp,MemoryUsageSetting.setupTempFileOnly().setTempDir(context.getCacheDir()))){
                count=pdf.getNumberOfPages();if(count==0)throw new IOException("빈 PDF입니다");
                if("true".equals(pdf.getDocumentInformation().getCustomMetadataValue("PDFNoteNotebook"))){try{String[] spec=pdf.getDocumentInformation().getCustomMetadataValue("PDFNotePaper").split(":");paper=new NotebookFiles.Paper(Integer.parseInt(spec[0]),Integer.parseInt(spec[1]));}catch(RuntimeException ignored){}}
            }
            cloneAnnotations(source,Uri.fromFile(target),count);NotebookFiles.replace(temp,target);if(paper!=null)putPaper(target,paper);
            preferences.edit().putString("source:"+source,target.getAbsolutePath()).commit();return target;
        }finally{temp.delete();}
    }
    synchronized File transfer(File source,File destination,String title,boolean move)throws IOException,JSONException{
        folder(destination);if(!managed(source)||!source.isFile())throw new IOException("저장된 PDF를 선택하세요");
        String name=NotebookFiles.pdfName(title);File exact=new File(destination,name);
        if(move&&source.equals(exact))return source;
        File target=move?exact:NotebookFiles.unique(destination,name);if(move&&target.exists())throw new IOException("같은 이름의 PDF가 있습니다");
        File temp=File.createTempFile(".copy-",".tmp",destination);
        try{
            try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(temp)){copy(in,out);}
            cloneAnnotations(Uri.fromFile(source),Uri.fromFile(target),Integer.MAX_VALUE);NotebookFiles.replace(temp,target);
            NotebookFiles.Paper paper=paper(source);if(paper!=null)putPaper(target,paper);
            if(move){if(!source.delete()){target.delete();throw new IOException("원본을 이동할 수 없습니다");}
                SharedPreferences.Editor edit=preferences.edit().remove("paper:"+source.getAbsolutePath()).remove("opened:"+source.getAbsolutePath()).putLong("opened:"+target.getAbsolutePath(),preferences.getLong("opened:"+source.getAbsolutePath(),0));
                for(Map.Entry<String,?> entry:preferences.getAll().entrySet())if(entry.getKey().startsWith("source:")&&source.getAbsolutePath().equals(entry.getValue()))edit.putString(entry.getKey(),target.getAbsolutePath());edit.commit();
            }
            return target;
        }finally{temp.delete();}
    }
    private void cloneAnnotations(Uri source,Uri target,int count)throws JSONException{
        AnnotationStore original=new AnnotationStore(context);original.open(source);AnnotationStore clone=new AnnotationStore(context);clone.open(target);clone.importJson(original.exportJson(source,"PDF"),count);
    }
    synchronized int append(File file,NotebookFiles.Paper requested)throws IOException{
        if(!managed(file)||!file.isFile())throw new IOException("저장된 PDF가 아닙니다");return NotebookFiles.append(context,file,requested);
    }
    private static void copy(InputStream in,OutputStream out)throws IOException{byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
}

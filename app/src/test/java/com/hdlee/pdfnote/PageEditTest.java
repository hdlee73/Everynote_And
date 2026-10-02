package com.hdlee.pdfnote;

import android.net.Uri;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import java.io.*;
import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PageEditTest {
    private LibraryRepository library;
    @Before public void setup(){PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication());library=new LibraryRepository(RuntimeEnvironment.getApplication());}
    private AnnotationStore newStore()throws Exception{AnnotationStore store=new AnnotationStore(RuntimeEnvironment.getApplication());store.open(Uri.parse("content://pages/"+UUID.randomUUID()));return store;}
    private static String contents(PDPage page)throws Exception{try(InputStream input=page.getContents();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[1024];int n;while((n=input.read(b))!=-1)out.write(b,0,n);return out.toString("ISO-8859-1");}}
    private static int pages(File file)throws Exception{try(PDDocument pdf=PDDocument.load(file)){return pdf.getNumberOfPages();}}

    private AnnotationStore filled()throws Exception{
        AnnotationStore store=newStore();
        for(int page=0;page<3;page++){
            AnnotationStore.Mark mark=new AnnotationStore.Mark();mark.page=page;store.marks.add(mark);
            AnnotationStore.InkStroke stroke=new AnnotationStore.InkStroke();stroke.page=page;store.strokes.add(stroke);
            AnnotationStore.PageElement element=new AnnotationStore.PageElement();element.page=page;element.text="p"+page;store.elements.add(element);
            AnnotationStore.OutlineItem outline=new AnnotationStore.OutlineItem();outline.page=page;store.outlines.add(outline);
            store.bookmarks.add(page);
        }
        return store;
    }
    private static List<Integer> marked(AnnotationStore store){List<Integer> pages=new ArrayList<>();for(AnnotationStore.Mark m:store.marks)pages.add(m.page);Collections.sort(pages);return pages;}

    @Test public void deletingAPageDropsItsAnnotationsAndRenumbersTheRest()throws Exception{
        AnnotationStore store=filled();store.removePage(1);
        assertEquals(Arrays.asList(0,1),marked(store));assertEquals(2,store.strokes.size());assertEquals(2,store.outlines.size());
        assertEquals(new HashSet<>(Arrays.asList(0,1)),store.bookmarks);
        assertEquals("p0",store.elements.get(0).text);assertEquals(0,store.elements.get(0).page);assertEquals("p2",store.elements.get(1).text);assertEquals(1,store.elements.get(1).page);
        store.removePage(0);assertEquals(Collections.singletonList(0),marked(store));assertEquals(Collections.singleton(0),store.bookmarks);assertEquals("p2",store.elements.get(0).text);
    }
    @Test public void insertingAPageMovesLaterAnnotationsDown()throws Exception{
        AnnotationStore store=filled();store.insertPageAfter(0);
        assertEquals(Arrays.asList(0,2,3),marked(store));assertEquals(new HashSet<>(Arrays.asList(0,2,3)),store.bookmarks);
        store.insertPageAfter(3);assertEquals(Arrays.asList(0,2,3),marked(store));
    }
    @Test public void pageEditsSurviveBackupAndRestore()throws Exception{
        AnnotationStore store=filled();store.removePage(2);AnnotationStore restored=newStore();restored.importJson(store.exportJson(Uri.parse("content://x"),"n"),2);
        assertEquals(2,restored.marks.size());assertEquals(new HashSet<>(Arrays.asList(0,1)),restored.bookmarks);
    }
    @Test public void blankPagesAreInsertedWhereRequestedAndPagesCanBeDeleted()throws Exception{
        File folder=library.createFolder(library.root,"페이지편집");File note=library.createNote(folder,"원본",new NotebookFiles.Paper(0,NotebookFiles.COLORS[0]));
        assertEquals(1,pages(note));
        assertEquals(2,library.insertPage(note,new NotebookFiles.Paper(1,NotebookFiles.COLORS[1]),0));
        assertEquals(3,library.insertPage(note,new NotebookFiles.Paper(2,NotebookFiles.COLORS[0]),0));
        try(PDDocument pdf=PDDocument.load(note)){
            assertEquals(3,pdf.getNumberOfPages());
            assertTrue("새 모눈 페이지는 첫 장 바로 뒤",contents(pdf.getPage(1)).length()>contents(pdf.getPage(0)).length());
            assertTrue("처음 추가한 줄 노트는 뒤로 밀림",contents(pdf.getPage(2)).length()>contents(pdf.getPage(0)).length());
            assertNotEquals(contents(pdf.getPage(1)),contents(pdf.getPage(2)));
        }
        assertEquals(2,library.deletePage(note,1));assertEquals(2,pages(note));
        assertEquals(1,library.deletePage(note,0));
        try{library.deletePage(note,0);fail("마지막 페이지는 삭제할 수 없습니다");}catch(IOException expected){}
        try{library.deletePage(note,5);fail();}catch(IOException expected){}
        assertEquals("범위를 넘으면 맨 뒤에 추가",2,library.insertPage(note,new NotebookFiles.Paper(0,NotebookFiles.COLORS[0]),99));
    }
    @Test public void allDocumentsListsEveryFolderExceptTheTrash()throws Exception{
        File a=library.createFolder(library.root,"가"),b=library.createFolder(a,"나");
        File first=library.createNote(library.root,"루트 노트",new NotebookFiles.Paper(0,NotebookFiles.COLORS[0])),second=library.createNote(a,"가 노트",new NotebookFiles.Paper(1,NotebookFiles.COLORS[0])),third=library.createNote(b,"나 노트",new NotebookFiles.Paper(2,NotebookFiles.COLORS[0]));
        List<File> all=library.allDocuments("");assertEquals(3,all.size());assertTrue(all.containsAll(Arrays.asList(first,second,third)));
        for(File f:all)assertTrue(f.isFile());
        assertEquals(Collections.singletonList(third),library.allDocuments("나 노트"));assertTrue(library.allDocuments("없는 문서").isEmpty());
        library.trash(second);List<File> after=library.allDocuments("");assertEquals(2,after.size());assertFalse(after.contains(second));
    }
    @Test public void legacyFolderColorsMapToThePastelPalette()throws Exception{
        File folder=library.createFolder(library.root,"색상");
        RuntimeEnvironment.getApplication().getSharedPreferences("pdf_note_library",android.content.Context.MODE_PRIVATE).edit().putInt("color:"+folder.getAbsolutePath(),0xFF54A485).commit();
        assertEquals(LibraryRepository.FOLDER_COLORS[2],library.folderColor(folder));
        library.folderColor(folder,LibraryRepository.FOLDER_COLORS[4]);assertEquals(LibraryRepository.FOLDER_COLORS[4],library.folderColor(folder));
    }
}

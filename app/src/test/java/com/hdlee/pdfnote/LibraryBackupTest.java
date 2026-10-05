package com.hdlee.pdfnote;

import android.net.Uri;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import java.nio.file.Files;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LibraryBackupTest {
    private LibraryRepository library;
    private File[] assets;
    @Before public void setup()throws Exception{
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication());library=new LibraryRepository(RuntimeEnvironment.getApplication());
        File base=Files.createTempDirectory("backup-assets").toFile();assets=new File[]{new File(base,"images"),new File(base,"recordings"),new File(base,"videos")};for(File d:assets)d.mkdirs();
    }
    @Test public void wholeLibraryBackupRestoresDocumentsNotesAssetsAndFlags()throws Exception{
        File folder=library.createFolder(library.root,"과목");library.folderColor(folder,LibraryRepository.FOLDER_COLORS[2]);
        File note=library.createNote(folder,"필기",new NotebookFiles.Paper(10,NotebookFiles.COLORS[0]));library.favorite(note,true);
        AnnotationStore store=new AnnotationStore(RuntimeEnvironment.getApplication());store.open(Uri.fromFile(note));
        AnnotationStore.PageElement text=new AnnotationStore.PageElement();text.text="백업 확인";text.lineSpacing=2.1f;store.elements.add(text);store.save();
        String image="0123456789abcdef0123456789abcdef0123.png";Files.write(new File(assets[0],image).toPath(),new byte[]{1,2,3});
        ByteArrayOutputStream out=new ByteArrayOutputStream();assertEquals(1,LibraryBackup.write(RuntimeEnvironment.getApplication(),library,assets,out,null));
        // everything lost
        assertTrue(note.delete());assertTrue(new File(assets[0],image).delete());library.favorite(note,false);
        LibraryBackup.Result r=LibraryBackup.restore(RuntimeEnvironment.getApplication(),library,assets,new ByteArrayInputStream(out.toByteArray()),true,f->false,null);
        assertEquals(1,r.documents);assertEquals(1,r.notes);assertEquals(1,r.assets);assertTrue(note.isFile());assertTrue(library.favorite(note));assertNotNull(library.paper(note));assertEquals(10,library.paper(note).kind);
        AnnotationStore back=new AnnotationStore(RuntimeEnvironment.getApplication());back.open(Uri.fromFile(note));assertEquals("백업 확인",back.elements.get(0).text);assertEquals(2.1f,back.elements.get(0).lineSpacing,.001f);
        assertTrue(new File(assets[0],image).isFile());
        // restoring again without overwrite adds a copy and keeps the original
        LibraryBackup.Result again=LibraryBackup.restore(RuntimeEnvironment.getApplication(),library,assets,new ByteArrayInputStream(out.toByteArray()),false,f->false,null);
        assertEquals(1,again.documents);assertTrue(new File(folder,"필기 (1).pdf").isFile());
        // an open document is skipped when overwriting
        LibraryBackup.Result open=LibraryBackup.restore(RuntimeEnvironment.getApplication(),library,assets,new ByteArrayInputStream(out.toByteArray()),true,f->true,null);
        assertEquals(1,open.skipped);assertEquals(0,open.documents);
    }
    @Test public void foreignZipIsRejected()throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(java.util.zip.ZipOutputStream z=new java.util.zip.ZipOutputStream(out)){z.putNextEntry(new java.util.zip.ZipEntry("x.txt"));z.write(1);z.closeEntry();}
        try{LibraryBackup.restore(RuntimeEnvironment.getApplication(),library,assets,new ByteArrayInputStream(out.toByteArray()),true,f->false,null);fail();}catch(IOException expected){}
    }
    @Test public void versionComparisonAndLineSpacingKey()throws Exception{
        assertTrue(UpdateChecker.compare("v1.36.0","1.35.0")>0);assertTrue(UpdateChecker.compare("1.35.0","1.35.0")==0);assertTrue(UpdateChecker.compare("1.9.0","1.10.0")<0);
        AnnotationStore.PageElement e=new AnnotationStore.PageElement();e.text="x";assertFalse(e.toJson().has("lineSpacing"));assertEquals(1.35f,e.line(),.001f);
        e.lineSpacing=1.8f;assertEquals(1.8f,AnnotationStore.PageElement.fromJson(e.toJson()).lineSpacing,.001f);
    }
}

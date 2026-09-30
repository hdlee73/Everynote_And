package com.hdlee.pdfnote;

import android.content.Context;
import android.net.Uri;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class StudyStoreTest {
    private AnnotationStore store;
    private Uri uri=Uri.parse("content://test/document.pdf");
    @Before public void setup(){Context context=RuntimeEnvironment.getApplication();context.getSharedPreferences("pdf_note_data",0).edit().clear().commit();java.io.File dir=new java.io.File(context.getFilesDir(),"annotations");java.io.File[] files=dir.listFiles();if(files!=null)for(java.io.File f:files)f.delete();store=new AnnotationStore(context);store.open(uri);}
    private AnnotationStore.StudyEntry entry(){AnnotationStore.StudyEntry e=new AnnotationStore.StudyEntry();e.page=1;e.x=0.3f;e.y=0.7f;e.text="한글, \"English\"\nNext <line>";e.comment="설명\t뒤쪽";e.excerpt=true;return e;}
    @Test public void notesPersistAndDocumentsStaySeparate(){store.studyEntries.add(entry());store.save();AnnotationStore again=new AnnotationStore(RuntimeEnvironment.getApplication());again.open(uri);assertEquals(1,again.studyEntries.size());assertEquals(0.7f,again.studyEntries.get(0).y,0.001);again.open(Uri.parse("content://test/other.pdf"));assertTrue(again.studyEntries.isEmpty());}
    @Test public void backupRestoresNotesAndExistingAnnotations()throws Exception{store.studyEntries.add(entry());store.bookmarks.add(1);String json=store.exportJson(uri,"document.pdf");store.studyEntries.clear();store.importJson(json,2);assertEquals(1,store.studyEntries.size());assertTrue(store.bookmarks.contains(1));}
    @Test public void failedImportDoesNotEraseExistingNotes()throws Exception{store.studyEntries.add(entry());String json=store.exportJson(uri,"document.pdf");try{store.importJson(json,1);fail();}catch(JSONException expected){}assertEquals(1,store.studyEntries.size());JSONObject root=new JSONObject(json);root.getJSONArray("studyEntries").getJSONObject(0).put("x",2);try{store.importJson(root.toString(),2);fail();}catch(JSONException expected){}assertEquals(1,store.studyEntries.size());}
    @Test public void legacyPreferencesMigrateToSidecar()throws Exception{Context context=RuntimeEnvironment.getApplication();store.studyEntries.add(entry());String json=store.exportJson(uri,"document.pdf");java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");StringBuilder key=new StringBuilder("doc_");for(byte b:digest.digest(uri.toString().getBytes(StandardCharsets.UTF_8)))key.append(String.format("%02x",b));context.getSharedPreferences("pdf_note_data",0).edit().putString(key.toString(),json).commit();store.open(uri);assertEquals(1,store.studyEntries.size());store.save();assertTrue(new File(context.getFilesDir(),"annotations/"+key+".json").exists());assertFalse(context.getSharedPreferences("pdf_note_data",0).contains(key.toString()));}
    @Test public void oldBackupsLoadWithoutStudyEntries()throws Exception{JSONObject root=new JSONObject(store.exportJson(uri,"document.pdf"));root.put("format","PDF Note annotations v1");root.remove("studyEntries");store.importJson(root.toString(),2);assertTrue(store.studyEntries.isEmpty());}
    @Test public void exportsPreserveTextAndProtectSpreadsheetCells()throws Exception{AnnotationStore.StudyEntry e=entry();List<AnnotationStore.StudyEntry> entries=Collections.singletonList(e);String csv=new String(StudyExporter.export(entries,"=formula",1),StandardCharsets.UTF_8);assertTrue(csv.contains("\"'=formula\""));assertTrue(csv.contains("\"\"English\"\""));String tsv=new String(StudyExporter.export(entries,"PDF",3),StandardCharsets.UTF_8);assertEquals(2,tsv.split("\t").length);assertTrue(tsv.contains("&lt;line&gt;"));assertTrue(tsv.contains("p.2"));String md=new String(StudyExporter.export(entries,"PDF",0),StandardCharsets.UTF_8);assertTrue(md.contains("[p.2]"));try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(StudyExporter.export(entries,"PDF",2)))){ZipEntry part;String sheet=null;int count=0;while((part=zip.getNextEntry())!=null){count++;if(part.getName().equals("xl/worksheets/sheet1.xml")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=zip.read(b))!=-1)out.write(b,0,n);sheet=out.toString("UTF-8");}}assertEquals(5,count);assertNotNull(sheet);assertTrue(sheet.contains("&lt;line&gt;"));assertTrue(sheet.contains("t=\"inlineStr\""));}}
}

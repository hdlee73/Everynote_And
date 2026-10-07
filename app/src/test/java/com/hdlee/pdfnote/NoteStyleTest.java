package com.hdlee.pdfnote;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.*;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NoteStyleTest {
    @Test public void landscapeNoteHasAWiderThanTallPage()throws Exception{
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication());LibraryRepository library=new LibraryRepository(RuntimeEnvironment.getApplication());
        File folder=library.createFolder(library.root,"Style-"+UUID.randomUUID());
        File portrait=library.createNote(folder,"세로",new NotebookFiles.Paper(1,NotebookFiles.COLORS[0]));
        File landscape=library.createNote(folder,"가로",new NotebookFiles.Paper(2,NotebookFiles.COLORS[1]),true);
        try(PDDocument pdf=PDDocument.load(portrait)){assertTrue(pdf.getPage(0).getMediaBox().getHeight()>pdf.getPage(0).getMediaBox().getWidth());}
        try(PDDocument pdf=PDDocument.load(landscape)){assertTrue(pdf.getPage(0).getMediaBox().getWidth()>pdf.getPage(0).getMediaBox().getHeight());}
        assertEquals(2,library.paper(landscape).kind);
    }
    @Test public void defaultNoteStyleSurvivesRestartAndFallsBackToTheFirstForm()throws Exception{
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();SharedPreferences prefs=activity.getSharedPreferences("style-"+UUID.randomUUID(),0);
        PaperChoiceView.Style fresh=PaperChoiceView.defaultStyle(prefs);assertEquals(10,fresh.kind);assertEquals(Color.WHITE,fresh.color);assertFalse(fresh.landscape);
        PaperChoiceView view=new PaperChoiceView(activity,3,NotebookFiles.COLORS[1],true,true);
        assertEquals(3,view.kind());assertEquals(NotebookFiles.COLORS[1],view.color());assertTrue(view.landscape());
        PaperChoiceView.saveDefaultStyle(prefs,view);
        PaperChoiceView.Style saved=PaperChoiceView.defaultStyle(prefs);assertEquals(3,saved.kind);assertEquals(NotebookFiles.COLORS[1],saved.color);assertTrue(saved.landscape);
        assertTrue(PaperChoiceView.describe(saved).contains("가로"));assertTrue(PaperChoiceView.describe(saved).contains("크림"));
        prefs.edit().putInt("default_paper_kind",NotebookFiles.CUSTOM).putInt("default_paper_color",12345).apply();
        PaperChoiceView.Style odd=PaperChoiceView.defaultStyle(prefs);assertEquals("a custom form can't be a default",10,odd.kind);assertEquals(Color.WHITE,odd.color);
    }
    @Test public void aFormPaperIgnoresTheLandscapeChoice()throws Exception{
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        PaperChoiceView view=new PaperChoiceView(activity,10,Color.WHITE,true,true);
        assertEquals(10,view.kind());assertEquals(NotebookFiles.CUSTOM,view.paper().kind);
        PaperChoiceView plain=new PaperChoiceView(activity,6,Color.WHITE,true,false);
        assertFalse("no layout choice offered -> always portrait",plain.landscape());
    }
}

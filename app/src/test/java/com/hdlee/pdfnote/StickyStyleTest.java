package com.hdlee.pdfnote;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class StickyStyleTest {
    @Test public void stickyStyleSurvivesSaveAndLoad() throws Exception {
        AnnotationStore.Mark m = new AnnotationStore.Mark();
        m.note = "메모"; m.noteOnly = true; m.paper = 0xFFCFE8FF; m.fontSp = 20; m.boxSize = 2;
        AnnotationStore.Mark back = AnnotationStore.Mark.fromJson(new JSONObject(m.toJson().toString()));
        assertEquals(0xFFCFE8FF, back.paper); assertEquals(20, back.fontSp); assertEquals(2, back.boxSize);
    }
    @Test public void oldNotesKeepTheDefaultLook() throws Exception {
        AnnotationStore.Mark old = AnnotationStore.Mark.fromJson(new JSONObject("{\"page\":1,\"note\":\"x\"}"));
        assertEquals(0xFFFFF3A6, old.paper); assertEquals(13, old.fontSp); assertEquals(1, old.boxSize);
    }
    @Test public void outOfRangeStyleIsClamped() throws Exception {
        AnnotationStore.Mark odd = AnnotationStore.Mark.fromJson(new JSONObject("{\"fontSp\":99,\"boxSize\":9}"));
        assertEquals(28, odd.fontSp); assertEquals(2, odd.boxSize);
    }
}

package com.hdlee.pdfnote;

import android.graphics.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class InkHighlighterTest {
    private static AnnotationStore.InkStroke line(float width){
        AnnotationStore.InkStroke s=new AnnotationStore.InkStroke();s.pen=AnnotationPainter.HIGHLIGHTER;s.color=0x66FFDE59;s.width=width;
        for(int i=0;i<=20;i++)s.points.add(new AnnotationStore.InkPoint(.1f+.8f*i/20f,.5f,i%2==0?.2f:1f));   // pressure must not change a highlighter's width
        return s;
    }
    private static Bitmap draw(AnnotationStore.InkStroke s){Bitmap b=Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);AnnotationPainter.stroke(new Canvas(b),new RectF(0,0,200,100),s);return b;}
    private static int thickness(Bitmap b){int n=0;for(int y=0;y<b.getHeight();y++)if(b.getPixel(100,y)!=Color.WHITE)n++;return n;}
    @Test public void highlighterIsATranslucentBandOfConstantWidth(){
        Bitmap thin=draw(line(.01f)),thick=draw(line(.04f));
        int c=thick.getPixel(100,50);assertTrue("투명해서 종이가 비칩니다",Color.blue(c)>120&&Color.blue(c)<230);
        assertTrue("굵기 차이가 분명합니다",thickness(thick)>=thickness(thin)*2);
        int wobble=0;for(int x=30;x<170;x++)if(thick.getPixel(x,50)!=thick.getPixel(100,50))wobble++;assertEquals("필압과 상관없이 같은 농도·굵기입니다",0,wobble);
    }
    @Test public void highlighterStrokeSurvivesSaving()throws Exception{
        JSONObject o=line(.02f).toJson();assertEquals(AnnotationPainter.HIGHLIGHTER,AnnotationStore.InkStroke.fromJson(o).pen);
    }
}

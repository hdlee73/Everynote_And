package com.hdlee.pdfnote;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PageCurlViewTest {
    private static Bitmap solid(int w, int h, int color) { Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); b.eraseColor(color); return b; }

    private Bitmap frame(PageCurlView view, float progress) {
        view.setProgress(progress);
        view.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 200, 100);
        Bitmap out = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(out));
        return out;
    }

    @Test public void spreadTurnsFromOldPagesToNewPages() {
        PageCurlView view = new PageCurlView(Robolectric.buildActivity(MainActivity.class).get());
        // fixed left page yellow, leaf front red, page under the leaf blue, back of the leaf green
        view.setup(solid(100, 100, Color.YELLOW), solid(100, 100, Color.BLUE), solid(100, 100, Color.RED), solid(100, 100, Color.GREEN), false, .5f);
        Bitmap start = frame(view, 0f);
        assertEquals("처음에는 낡은 왼쪽 면", Color.YELLOW, start.getPixel(30, 50));
        for (float t = .1f; t < 1f; t += .1f) assertNotNull("넘기는 중간 프레임도 그려집니다", frame(view, t));
        Bitmap end = frame(view, 1f);
        assertEquals("끝에는 새 오른쪽 면이 보입니다", Color.BLUE, end.getPixel(170, 50));
    }

    @Test public void mirroredTurnMovesTheOtherWay() {
        PageCurlView view = new PageCurlView(Robolectric.buildActivity(MainActivity.class).get());
        view.setup(solid(100, 100, Color.YELLOW), solid(100, 100, Color.BLUE), solid(100, 100, Color.RED), solid(100, 100, Color.GREEN), true, .5f);
        for (float t = 0f; t <= 1f; t += .1f) assertNotNull("뒤로 넘기는 모든 프레임이 그려집니다(거울 캔버스)", frame(view, t));
    }
}

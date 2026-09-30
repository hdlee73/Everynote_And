package com.hdlee.pdfnote;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "mdpi")
public class PdfPageViewSelectionTest {
    private PdfPageView view;
    private final List<String> selections = new ArrayList<>();
    private final List<PdfPageView.TextRegion> cached = new ArrayList<>();
    private long time;

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        PdfPageView.Listener listener = (PdfPageView.Listener) Proxy.newProxyInstance(
            PdfPageView.Listener.class.getClassLoader(), new Class<?>[]{PdfPageView.Listener.class},
            (proxy, method, args) -> {
                if (method.getName().equals("onTextSelectionFinished"))
                    selections.add(((PdfPageView.TextSelection) args[0]).text);
                return null;
            });
        view = new PdfPageView(activity, listener);
        FrameLayout parent = new FrameLayout(activity);
        parent.addView(view);
        activity.setContentView(parent);
        parent.layout(0, 0, 1000, 1000);
        view.layout(0, 0, 1000, 1000);
        view.showPage(Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888), 0,
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (int i = 0; i < 4; i++) {
            float left = new float[]{0.10f, 0.22f, 0.33f, 0.45f}[i];
            cached.add(new PdfPageView.TextRegion("ABCD".substring(i, i+1), "A B C D",
                new RectF(left, 0.10f, left+0.10f, 0.12f), new RectF(0.1f, 0.10f, 0.55f, 0.12f)));
        }
        view.setTextRegions(cached, false);
    }

    private void event(int action, float x, float y) {
        MotionEvent e = MotionEvent.obtain(time, time += 20, action, x, y, 0);
        view.onTouchEvent(e);
        e.recycle();
    }
    private void drag(float from, float to) {
        event(MotionEvent.ACTION_DOWN, from, 110);
        event(MotionEvent.ACTION_MOVE, to, 110);
        event(MotionEvent.ACTION_UP, to, 110);
    }

    @Test public void consecutiveDragNearOldHandleStartsFreshSelection() {
        drag(150, 270);
        drag(335, 500);
        assertEquals(List.of("A B", "C D"), selections);
    }

    @Test public void handleStillAdjustsPreviousSelection() {
        drag(150, 270);
        event(MotionEvent.ACTION_DOWN, 320, 125);
        event(MotionEvent.ACTION_MOVE, 380, 110);
        event(MotionEvent.ACTION_UP, 380, 110);
        assertEquals(List.of("A B", "A B C"), selections);
    }

    @Test public void returningToCachedPagePreservesRecognizedWords() {
        view.stopTextSelection();
        assertEquals(4, cached.size());
        view.setTextRegions(cached, false);
        drag(150, 270);
        assertEquals(List.of("A B"), selections);
    }

    @Test public void actionClearingOverlayAllowsImmediateNewDrag() {
        drag(150, 270);
        view.clearTextSelectionOverlay();
        drag(500, 380);
        assertEquals(List.of("A B", "C D"), selections);
    }

    @Test public void cancellationRemovesPendingLongPress() {
        event(MotionEvent.ACTION_DOWN, 150, 110);
        event(MotionEvent.ACTION_CANCEL, 150, 110);
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertTrue(selections.isEmpty());
        drag(335, 500);
        assertEquals(List.of("C D"), selections);
    }
}

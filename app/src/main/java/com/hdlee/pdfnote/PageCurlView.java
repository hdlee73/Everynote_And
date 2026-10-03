package com.hdlee.pdfnote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.view.View;

/**
 * Draws a paper page turning like a real book leaf: the page curls around a moving fold line, its back side shows on the
 * far side of the fold, and soft shadows fall on the page underneath. The leaf is a bitmap bent with drawBitmapMesh.
 * Right-to-left turns are drawn directly; left-to-right turns reuse the same code on a mirrored canvas with mirrored bitmaps.
 */
final class PageCurlView extends View {
    private static final int COLUMNS = 72, ROWS = 96;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private Bitmap fixedHalf, under, front, back;
    private boolean mirrored;
    private float spine;
    private float progress;
    private final float[] verts = new float[(COLUMNS + 1) * (ROWS + 1) * 2];
    private final int[] colors = new int[(COLUMNS + 1) * (ROWS + 1)];
    private Bitmap dark;
    /** Tint laid over the mirrored page to make the back of the leaf (white on light pages, black on dark ones). */
    static int backTint = 0xCCFFFFFF;

    PageCurlView(Context context) { super(context); }

    /** @param fixedHalf page left of the spine that stays put (null for single-page turns); the other bitmaps cover the leaf area. */
    void setup(Bitmap fixedHalf, Bitmap under, Bitmap front, Bitmap back, boolean mirrored, float spineFraction) {
        this.fixedHalf = fixedHalf; this.under = under; this.front = front; this.back = back; this.mirrored = mirrored; this.spine = spineFraction;
    }
    void setProgress(float value) { progress = Math.max(0f, Math.min(1f, value)); invalidate(); }
    float progress() { return progress; }
    void release() { for (Bitmap b : new Bitmap[]{fixedHalf, under, front, back}) if (b != null && !b.isRecycled()) b.recycle(); fixedHalf = under = front = back = null; if (dark != null) { dark.recycle(); dark = null; } }

    static Bitmap mirror(Bitmap source) {
        Matrix m = new Matrix(); m.setScale(-1f, 1f, source.getWidth() / 2f, 0f);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), m, false);
    }
    static Bitmap paperBack(Bitmap front) {
        Bitmap copy = front.copy(Bitmap.Config.ARGB_8888, true); new Canvas(copy).drawColor(backTint); return copy;
    }

    private float touch = .88f;
    /** Finger height as a fraction of the page: the curl starts there and the far rows follow later, like a peeled corner. */
    void setTouch(float fraction) { float v = Math.max(0f, Math.min(1f, fraction)); if (v != touch) { touch = v; invalidate(); } }

    /** Fold position of one row (f = 0 top .. 1 bottom): exactly at the finger's row, lagging smoothly further away from it, and straight at both ends. */
    private float foldAt(float f, float w, float lw, float t) {
        float dist = (float) Math.sqrt((f - touch) * (f - touch) + .02f) - .1414f;
        return w - t * lw + lw * .62f * (float) Math.pow(1f - t, .85f) * dist;
    }

    @Override protected void onDraw(Canvas canvas) {
        if (front == null || under == null || back == null) return;
        final float w = getWidth(), h = getHeight(), s = spine * w, lw = w - s;
        canvas.save();
        if (mirrored) canvas.scale(-1f, 1f, w / 2f, 0f);
        if (fixedHalf != null) canvas.drawBitmap(fixedHalf, 0, 0, null);
        canvas.drawBitmap(under, s, 0, null);
        final float t = progress, r = lw * 0.06f * (float) Math.sqrt(Math.sin(Math.PI * t)), flat = (float) Math.PI * r;
        if (t > 0.01f && t < 0.995f) drawShadow(canvas, s, w, h, lw, t);
        fill(false, s, w, lw, r, flat, h, t);
        canvas.drawBitmapMesh(front, COLUMNS, ROWS, verts, 0, colors, 0, paint);
        if (t > 0.01f) {
            fill(true, s, w, lw, r, flat, h, t);
            canvas.drawBitmapMesh(back, COLUMNS, ROWS, verts, 0, colors, 0, paint);
        }
        canvas.restore();
    }

    /** Soft shadow on the page being revealed, as a mesh whose vertex colors fade out smoothly away from the fold. */
    private void drawShadow(Canvas canvas, float s, float w, float h, float lw, float t) {
        if (dark == null) { dark = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888); dark.eraseColor(0xFF000000); }
        final float reach = lw * 0.09f;
        for (int j = 0; j <= ROWS; j++) {
            float f = (float) j / ROWS, fold = foldAt(f, w, lw, t);
            for (int i = 0; i <= COLUMNS; i++) {
                float u = (float) i / COLUMNS, x = Math.min(w, fold + reach * u);
                int k = j * (COLUMNS + 1) + i;
                verts[k * 2] = Math.max(s, x); verts[k * 2 + 1] = h * f;
                float a = fold >= w ? 0f : 0x30 * (1f - u) * (1f - u);
                colors[k] = ((int) a << 24);
            }
        }
        canvas.drawBitmapMesh(dark, COLUMNS, ROWS, verts, 0, colors, 0, null);
    }

    /** Fills the mesh for the front (backSide=false) or back (true) of the leaf; vertices of the other side collapse onto the fold. */
    private void fill(boolean backSide, float s, float w, float lw, float r, float flat, float h, float t) {
        for (int j = 0; j <= ROWS; j++) {
            float f = (float) j / ROWS, fold = foldAt(f, w, lw, t);
            for (int i = 0; i <= COLUMNS; i++) {
                float x = s + lw * i / COLUMNS, d = x - fold, px, inset = 0f;
                int shade = 0;
                if (d <= 0f) { px = backSide ? fold : x; }
                else if (r > .5f && d <= flat) {
                    px = backSide ? fold : fold + r * (float) Math.sin(d / r); inset = h * 0.012f * (1f - (float) Math.cos(d / r));
                    shade = backSide ? 0 : (int) (46f * Math.sin(Math.PI * d / flat));
                } else {
                    px = backSide ? fold - (d - flat) : fold;
                    if (backSide) { float near = Math.max(0f, 1f - (d - flat) / (lw * 0.22f)); shade = (int) (30f * near * near); }
                }
                int k = j * (COLUMNS + 1) + i;
                verts[k * 2] = px; verts[k * 2 + 1] = inset + (h - 2 * inset) * f;
                int v = 255 - shade; colors[k] = 0xFF000000 | (v << 16) | (v << 8) | v;
            }
        }
    }
}

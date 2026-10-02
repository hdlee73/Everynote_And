package com.hdlee.pdfnote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/**
 * Draws a paper page turning like a real book leaf: the page curls around a moving fold line, its back side shows on the
 * far side of the fold, and soft shadows fall on the page underneath. The leaf is a bitmap bent with drawBitmapMesh.
 * Right-to-left turns are drawn directly; left-to-right turns reuse the same code on a mirrored canvas with mirrored bitmaps.
 */
final class PageCurlView extends View {
    private static final int COLUMNS = 48;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint();
    private Bitmap fixedHalf, under, front, back;
    private boolean mirrored;
    private float spine;
    private float progress;
    private final float[] verts = new float[(COLUMNS + 1) * 2 * 2];

    PageCurlView(Context context) { super(context); }

    /** @param fixedHalf page left of the spine that stays put (null for single-page turns); the other bitmaps cover the leaf area. */
    void setup(Bitmap fixedHalf, Bitmap under, Bitmap front, Bitmap back, boolean mirrored, float spineFraction) {
        this.fixedHalf = fixedHalf; this.under = under; this.front = front; this.back = back; this.mirrored = mirrored; this.spine = spineFraction;
    }
    void setProgress(float value) { progress = Math.max(0f, Math.min(1f, value)); invalidate(); }
    void release() { for (Bitmap b : new Bitmap[]{fixedHalf, under, front, back}) if (b != null && !b.isRecycled()) b.recycle(); fixedHalf = under = front = back = null; }

    static Bitmap mirror(Bitmap source) {
        Matrix m = new Matrix(); m.setScale(-1f, 1f, source.getWidth() / 2f, 0f);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), m, false);
    }
    static Bitmap paperBack(Bitmap front) {
        Bitmap copy = front.copy(Bitmap.Config.ARGB_8888, true); new Canvas(copy).drawColor(0xCCFFFFFF); return copy;
    }

    @Override protected void onDraw(Canvas canvas) {
        if (front == null || under == null || back == null) return;
        final float w = getWidth(), h = getHeight(), s = spine * w, lw = w - s;
        canvas.save();
        if (mirrored) canvas.scale(-1f, 1f, w / 2f, 0f);
        if (fixedHalf != null) canvas.drawBitmap(fixedHalf, 0, 0, null);
        canvas.drawBitmap(under, s, 0, null);
        final float t = progress, fold = w - t * lw, r = lw * 0.11f * (float) Math.sin(Math.PI * t), flat = (float) Math.PI * r;
        // shadow of the lifting leaf on the page below
        if (t > 0.01f && t < 0.995f) {
            float reach = lw * 0.2f;
            shade.setShader(new LinearGradient(fold, 0, Math.min(w, fold + reach), 0, 0x55000000, 0x00000000, Shader.TileMode.CLAMP));
            canvas.save(); canvas.clipRect(Math.max(s, fold), 0, w, h); canvas.drawRect(Math.max(s, fold), 0, w, h, shade); canvas.restore();
        }
        // front side: flat part plus the half cylinder around the fold
        fill(false, s, lw, fold, r, flat, h);
        canvas.drawBitmapMesh(front, COLUMNS, 1, verts, 0, null, 0, paint);
        if (r > 1f) {
            shade.setShader(new LinearGradient(fold, 0, fold + 2 * r, 0, new int[]{0x00000000, 0x40000000, 0x00000000}, new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP));
            canvas.save(); canvas.clipRect(fold, 0, Math.min(w, fold + 2 * r), h); canvas.drawRect(fold, 0, fold + 2 * r, h, shade); canvas.restore();
        }
        // back side lying on the far side of the fold
        if (t > 0.01f) {
            fill(true, s, lw, fold, r, flat, h);
            canvas.drawBitmapMesh(back, COLUMNS, 1, verts, 0, null, 0, paint);
            float tip = fold - Math.max(0f, lw - flat);
            shade.setShader(new LinearGradient(fold, 0, fold - lw * 0.16f, 0, 0x26000000, 0x00000000, Shader.TileMode.CLAMP));
            canvas.drawRect(Math.max(0, fold - lw * 0.16f), 0, fold, h, shade);
            shade.setShader(new LinearGradient(tip, 0, tip - lw * 0.1f, 0, 0x40000000, 0x00000000, Shader.TileMode.CLAMP));
            canvas.drawRect(tip - lw * 0.1f, 0, tip, h, shade);
        }
        shade.setShader(null);
        canvas.restore();
    }

    /** Fills the mesh for the front (backSide=false) or back (true) of the leaf; vertices of the other side collapse onto the fold. */
    private void fill(boolean backSide, float s, float lw, float fold, float r, float flat, float h) {
        for (int i = 0; i <= COLUMNS; i++) {
            float x = s + lw * i / COLUMNS, d = x - fold, px, inset = 0f;
            if (d <= 0f) px = backSide ? fold : x;
            else if (r > .5f && d <= flat) { px = backSide ? fold : fold + r * (float) Math.sin(d / r); inset = h * 0.018f * (1f - (float) Math.cos(d / r)); }
            else px = backSide ? fold - (d - flat) : fold;
            verts[i * 2] = px; verts[i * 2 + 1] = inset;
            verts[(COLUMNS + 1 + i) * 2] = px; verts[(COLUMNS + 1 + i) * 2 + 1] = h - inset;
        }
    }
}

package com.hdlee.pdfnote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/**
 * Draws a paper page turning like a real book leaf: the page curls around a moving fold line, its back side shows on the
 * far side of the fold, and a soft shadow falls on the page underneath. The fold is a straight line, the perpendicular bisector of
 * the grabbed page corner and the finger, so a turn started low lifts the lower corner first and the rest follows diagonally,
 * and the flap's outline is the mirrored page edge (a clean diagonal corner, never a straight cut).
 * Right-to-left turns are drawn directly; left-to-right turns reuse the same code on a mirrored canvas with mirrored bitmaps.
 */
final class PageCurlView extends View {
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private Bitmap fixedHalf, under, front, back;
    private boolean mirrored;
    private float spine;
    private float progress;
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Tint laid over the mirrored page to make the back of the leaf (white on light pages, black on dark ones). */
    static int backTint = 0xE6FFFFFF;

    PageCurlView(Context context) { super(context); }

    /** @param fixedHalf page left of the spine that stays put (null for single-page turns); the other bitmaps cover the leaf area. */
    void setup(Bitmap fixedHalf, Bitmap under, Bitmap front, Bitmap back, boolean mirrored, float spineFraction) {
        this.fixedHalf = fixedHalf; this.under = under; this.front = front; this.back = back; this.mirrored = mirrored; this.spine = spineFraction;
    }
    void setProgress(float value) { progress = Math.max(0f, Math.min(1f, value)); invalidate(); }
    float progress() { return progress; }
    void release() { for (Bitmap b : new Bitmap[]{fixedHalf, under, front, back}) if (b != null && !b.isRecycled()) b.recycle(); fixedHalf = under = front = back = null; }

    static Bitmap mirror(Bitmap source) {
        Matrix m = new Matrix(); m.setScale(-1f, 1f, source.getWidth() / 2f, 0f);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), m, false);
    }
    static Bitmap paperBack(Bitmap front) {
        Bitmap copy = front.copy(Bitmap.Config.ARGB_8888, true); new Canvas(copy).drawColor(backTint); return copy;
    }

    private float touch = .88f;
    /** Finger height as a fraction of the page: the curl starts at the nearer corner and peels diagonally from there. */
    void setTouch(float fraction) { float v = Math.max(0f, Math.min(1f, fraction)); if (v != touch) { touch = v; invalidate(); } }

    private final Path clip = new Path(), flap = new Path();
    private final Matrix reflect = new Matrix();
    private final float[] m9 = new float[9];

    @Override protected void onDraw(Canvas canvas) {
        if (front == null || under == null || back == null) return;
        final float w = getWidth(), h = getHeight(), s = spine * w, lw = w - s, t = progress;
        canvas.save();
        if (mirrored) canvas.scale(-1f, 1f, w / 2f, 0f);
        if (fixedHalf != null) canvas.drawBitmap(fixedHalf, 0, 0, null);
        canvas.drawBitmap(under, s, 0, null);
        if (t <= 0.004f) canvas.drawBitmap(front, s, 0, paint);
        else if (t < 0.995f) drawFold(canvas, s, w, h, lw, t);
        canvas.restore();
    }

    /** The turning leaf: the grabbed corner C goes to the finger point G, the fold is their perpendicular bisector. */
    private void drawFold(Canvas canvas, float s, float w, float h, float lw, float t) {
        final float cy = touch > .5f ? h : 0f, dir = cy > 0f ? -1f : 1f;
        // the lifted corner starts small and grows with the drag; the fold starts steeply diagonal and straightens as the page goes over
        final float dx = 2.04f * lw * t, dy = dx * .8f * (float) Math.pow(1f - t, 1.2f) * (h / Math.max(1f, lw));
        float gx = w - dx, gy = cy + dir * dy;
        float nx = gx - w, ny = gy - cy, len = (float) Math.hypot(nx, ny);
        nx /= len; ny /= len;                                  // normal pointing from the corner toward the finger
        final float mx = (w + gx) / 2f, my = (cy + gy) / 2f, tx = -ny, ty2 = nx, big = 4f * (w + h);
        // reflection across the fold line
        final float k = 2f * (nx * mx + ny * my);
        m9[0] = 1f - 2f * nx * nx; m9[1] = -2f * nx * ny; m9[2] = k * nx; m9[3] = -2f * nx * ny; m9[4] = 1f - 2f * ny * ny; m9[5] = k * ny; m9[6] = 0f; m9[7] = 0f; m9[8] = 1f;
        reflect.setValues(m9);
        // 1) the part of the leaf that has not turned yet (finger side of the fold)
        canvas.save(); canvas.clipRect(s, 0, w, h); halfPlane(clip, mx, my, nx, ny, tx, ty2, big, 1f); canvas.clipPath(clip);
        canvas.drawBitmap(front, s, 0, paint); canvas.restore();
        // 2) soft shadow cast on the page that is being uncovered
        final float reach = lw * .10f;
        canvas.save(); canvas.clipRect(s, 0, w, h); halfPlane(clip, mx, my, nx, ny, tx, ty2, big, -1f); canvas.clipPath(clip);
        band(canvas, mx, my, nx, ny, tx, ty2, big, -reach, 0f, 0x00000000, 0x4A000000); canvas.restore();
        // 3) the back of the turned part, mirrored across the fold, with a rounded shading along the crease
        canvas.save(); canvas.concat(reflect); canvas.clipRect(s, 0, w, h); halfPlane(clip, mx, my, nx, ny, tx, ty2, big, -1f); canvas.clipPath(clip);
        canvas.drawBitmap(back, s, 0, paint); canvas.restore();
        flap.rewind(); boolean first = true;
        float[][] poly = clipRectByHalfPlane(s, w, h, mx, my, nx, ny);
        for (float[] p : poly) { float[] q = {p[0], p[1]}; reflect.mapPoints(q); if (first) { flap.moveTo(q[0], q[1]); first = false; } else flap.lineTo(q[0], q[1]); }
        if (!first) {
            flap.close();
            canvas.save(); canvas.clipPath(flap);
            band(canvas, mx, my, nx, ny, tx, ty2, big, 0f, lw * .07f, 0x3C000000, 0x00000000); canvas.restore();
            shade.setStyle(Paint.Style.STROKE); shade.setStrokeWidth(1f); shade.setShader(null); shade.setColor(0x22000000);
            canvas.drawPath(flap, shade);
        }
    }

    /** Half-plane {sign * n·(P - M) >= 0} as a big quad. */
    private static void halfPlane(Path out, float mx, float my, float nx, float ny, float tx, float ty, float big, float sign) {
        out.rewind(); float ax = nx * sign * big, ay = ny * sign * big;
        out.moveTo(mx + tx * big, my + ty * big); out.lineTo(mx - tx * big, my - ty * big);
        out.lineTo(mx - tx * big + ax, my - ty * big + ay); out.lineTo(mx + tx * big + ax, my + ty * big + ay); out.close();
    }

    /** Gradient band between offsets a..b along the normal (positive = finger side). */
    private void band(Canvas canvas, float mx, float my, float nx, float ny, float tx, float ty, float big, float a, float b, int ca, int cb) {
        Path p = new Path();
        p.moveTo(mx + tx * big + nx * a, my + ty * big + ny * a); p.lineTo(mx - tx * big + nx * a, my - ty * big + ny * a);
        p.lineTo(mx - tx * big + nx * b, my - ty * big + ny * b); p.lineTo(mx + tx * big + nx * b, my + ty * big + ny * b); p.close();
        shade.setStyle(Paint.Style.FILL); shade.setShader(new LinearGradient(mx + nx * a, my + ny * a, mx + nx * b, my + ny * b, ca, cb, Shader.TileMode.CLAMP));
        canvas.drawPath(p, shade); shade.setShader(null);
    }

    /** The leaf rectangle cut by the fold, keeping the corner side (n·(P - M) <= 0); Sutherland-Hodgman. */
    private static float[][] clipRectByHalfPlane(float s, float w, float h, float mx, float my, float nx, float ny) {
        float[][] in = {{s, 0}, {w, 0}, {w, h}, {s, h}};
        java.util.ArrayList<float[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < in.length; i++) {
            float[] a = in[i], b = in[(i + 1) % in.length];
            float da = nx * (a[0] - mx) + ny * (a[1] - my), db = nx * (b[0] - mx) + ny * (b[1] - my);
            if (da <= 0) out.add(a);
            if ((da < 0 && db > 0) || (da > 0 && db < 0)) { float u = da / (da - db); out.add(new float[]{a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u}); }
        }
        return out.toArray(new float[0][]);
    }
}

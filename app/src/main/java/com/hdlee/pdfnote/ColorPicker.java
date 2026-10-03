package com.hdlee.pdfnote;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.function.IntConsumer;

/** A free colour chooser (hue, saturation, brightness, optional opacity) plus the wide preset palette used across the app. */
final class ColorPicker {
    /** 4 rows of 8: neutrals, vivid, deep, pastel. */
    static final int[] PALETTE = {
        0xFF000000, 0xFF3A3A3C, 0xFF636366, 0xFF8E8E93, 0xFFAEAEB2, 0xFFD1D1D6, 0xFFE5E5EA, 0xFFFFFFFF,
        0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759, 0xFF00C7BE, 0xFF007AFF, 0xFF5856D6, 0xFFAF52DE,
        0xFF8E1B14, 0xFFB35900, 0xFF8A6D00, 0xFF1B7F37, 0xFF00746E, 0xFF0040A8, 0xFF2E2C8A, 0xFF7B2FA3,
        0xFFFFB3AE, 0xFFFFD3A0, 0xFFFFF0A0, 0xFFB8EBC4, 0xFFA0EEE9, 0xFFA6CBFF, 0xFFC4C2FF, 0xFFE3C4F5};

    private ColorPicker() {}

    static void show(Context context, String title, int initial, boolean alpha, IntConsumer onPick) {
        final float[] hsv = new float[3];
        Color.colorToHSV(initial, hsv);
        final int[] opacity = {alpha ? Color.alpha(initial) : 255};
        final float density = context.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Math.round(18 * density), Math.round(6 * density), Math.round(18 * density), Math.round(6 * density));
        final View preview = new View(context);
        box.addView(preview, new LinearLayout.LayoutParams(-1, Math.round(48 * density)));
        final Slider hue = new Slider(context), sat = new Slider(context), val = new Slider(context), alp = new Slider(context);
        final TextView hex = new TextView(context);
        hex.setTextSize(12);
        hex.setTextColor(0xFF8E8E93);
        hex.setGravity(Gravity.CENTER);
        Runnable refresh = () -> {
            int rgb = Color.HSVToColor(hsv);
            int full = (opacity[0] << 24) | (rgb & 0xFFFFFF);
            GradientDrawable_set(preview, full, density);
            int[] rainbow = new int[7];
            for (int i = 0; i < 7; i++) rainbow[i] = Color.HSVToColor(new float[]{i * 60f, 1f, 1f});
            hue.setColors(rainbow);
            sat.setColors(new int[]{Color.HSVToColor(new float[]{hsv[0], 0f, hsv[2]}), Color.HSVToColor(new float[]{hsv[0], 1f, hsv[2]})});
            val.setColors(new int[]{Color.BLACK, Color.HSVToColor(new float[]{hsv[0], hsv[1], 1f})});
            alp.setColors(new int[]{rgb & 0xFFFFFF, rgb | 0xFF000000});
            hex.setText(String.format("#%06X" + (alpha ? " · 투명도 %d%%" : ""), alpha ? new Object[]{rgb & 0xFFFFFF, Math.round(opacity[0] * 100f / 255f)} : new Object[]{rgb & 0xFFFFFF}));
        };
        hue.setValue(hsv[0] / 360f);
        sat.setValue(hsv[1]);
        val.setValue(hsv[2]);
        alp.setValue(opacity[0] / 255f);
        hue.setOnChange(v -> { hsv[0] = v * 359.9f; refresh.run(); });
        sat.setOnChange(v -> { hsv[1] = v; refresh.run(); });
        val.setOnChange(v -> { hsv[2] = v; refresh.run(); });
        alp.setOnChange(v -> { opacity[0] = Math.round(v * 255f); refresh.run(); });
        addRow(box, context, "색상", hue, density);
        addRow(box, context, "채도", sat, density);
        addRow(box, context, "밝기", val, density);
        if (alpha) addRow(box, context, "투명도", alp, density);
        box.addView(hex, new LinearLayout.LayoutParams(-1, Math.round(28 * density)));
        refresh.run();
        new AlertDialog.Builder(context).setTitle(title).setView(box)
            .setPositiveButton("적용", (d, w) -> onPick.accept((opacity[0] << 24) | (Color.HSVToColor(hsv) & 0xFFFFFF)))
            .setNegativeButton("취소", null).show();
    }

    private static void GradientDrawable_set(View preview, int color, float density) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(12 * density);
        g.setStroke(Math.round(density), 0xFFC7C7CC);
        preview.setBackground(g);
    }

    private static void addRow(LinearLayout box, Context context, String label, Slider slider, float density) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(context);
        name.setText(label);
        name.setTextSize(12);
        name.setTextColor(0xFF8E8E93);
        row.addView(name, new LinearLayout.LayoutParams(Math.round(44 * density), -2));
        row.addView(slider, new LinearLayout.LayoutParams(0, Math.round(36 * density), 1));
        box.addView(row, new LinearLayout.LayoutParams(-1, Math.round(40 * density)));
    }

    /** A horizontal gradient track with a round thumb. */
    private static final class Slider extends View {
        interface Change { void on(float value); }
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int[] colors = {Color.BLACK, Color.WHITE};
        private float value;
        private Change change;

        Slider(Context context) { super(context); }
        void setColors(int[] c) { colors = c; invalidate(); }
        void setValue(float v) { value = Math.max(0f, Math.min(1f, v)); invalidate(); }
        void setOnChange(Change c) { change = c; }

        @Override protected void onDraw(Canvas canvas) {
            float d = getResources().getDisplayMetrics().density, pad = 14 * d, h = 16 * d, top = (getHeight() - h) / 2f;
            RectF bar = new RectF(pad, top, getWidth() - pad, top + h);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(0xFFDDDDDD);
            canvas.drawRoundRect(bar, h / 2, h / 2, paint);
            paint.setShader(new LinearGradient(bar.left, 0, bar.right, 0, colors, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(bar, h / 2, h / 2, paint);
            paint.setShader(null);
            float x = bar.left + value * bar.width(), cy = getHeight() / 2f;
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x, cy, 12 * d, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2 * d);
            paint.setColor(0xFF8E8E93);
            canvas.drawCircle(x, cy, 12 * d, paint);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
                getParent().requestDisallowInterceptTouchEvent(true);
                float pad = 14 * getResources().getDisplayMetrics().density;
                setValue((e.getX() - pad) / Math.max(1f, getWidth() - 2 * pad));
                if (change != null) change.on(value);
                return true;
            }
            return a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL || super.onTouchEvent(e);
        }
    }
}

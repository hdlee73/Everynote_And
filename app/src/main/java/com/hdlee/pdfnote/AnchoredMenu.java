package com.hdlee.pdfnote;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * Compact floating menu card in the style of a modern note app: a short list of text rows (optionally with a small icon, check
 * or chevron), an optional row of icon-only shortcuts, anchored to the button that opened it, either below it (header menus)
 * or directly above it (bottom-toolbar menus).
 */
final class AnchoredMenu {
    static final int INK = 0xFF1C1C1E, ACCENT = 0xFF007AFF, GRAY = 0xFF8E8E93, LINE = 0xFFE5E5EA, RED = 0xFFFF3B30;

    static final class Row {
        final String label; final int icon; final Runnable action; boolean selected, danger, submenu, divider; View custom;
        Row(String label, int icon, Runnable action) { this.label = label; this.icon = icon; this.action = action; }
        static Row divider() { Row r = new Row("", 0, null); r.divider = true; return r; }
        static Row custom(View view) { Row r = new Row("", 0, null); r.custom = view; return r; }
        Row selected(boolean value) { selected = value; return this; }
        Row danger() { danger = true; return this; }
        Row submenu() { submenu = true; return this; }
    }
    static final class Shortcut {
        final String description; final int icon; final boolean active; final Runnable action;
        Shortcut(String description, int icon, boolean active, Runnable action) { this.description = description; this.icon = icon; this.active = active; this.action = action; }
    }

    private AnchoredMenu() {}

    /** @param above true to place the card right above the anchor (toolbar menus), false to drop it below the anchor. */
    static PopupWindow show(Context context, View anchor, boolean above, List<Row> rows, List<Shortcut> shortcuts) {
        final float density = context.getResources().getDisplayMetrics().density;
        final int screenW = context.getResources().getDisplayMetrics().widthPixels, screenH = context.getResources().getDisplayMetrics().heightPixels;
        final PopupWindow[] holder = new PopupWindow[1];
        LinearLayout card = new LinearLayout(context); card.setOrientation(LinearLayout.VERTICAL); card.setTag("anchored_menu");
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(18 * density); bg.setStroke(Math.max(1, Math.round(density * .5f)), 0x14000000); card.setBackground(bg);
        card.setPadding(Math.round(6 * density), Math.round(6 * density), Math.round(6 * density), Math.round(6 * density)); card.setElevation(12 * density);
        LinearLayout list = new LinearLayout(context); list.setOrientation(LinearLayout.VERTICAL);
        boolean wide = false;
        for (final Row row : rows) {
            if (row.custom != null) { list.addView(row.custom, new LinearLayout.LayoutParams(-1, -2)); wide = true; continue; }
            if (row.divider) { View line = new View(context); line.setBackgroundColor(LINE); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, Math.round(density * .5f))); lp.setMargins(Math.round(12 * density), Math.round(4 * density), Math.round(12 * density), Math.round(4 * density)); list.addView(line, lp); continue; }
            LinearLayout line = new LinearLayout(context); line.setGravity(Gravity.CENTER_VERTICAL); line.setPadding(Math.round(12 * density), 0, Math.round(12 * density), 0); line.setContentDescription(row.label);
            if (row.icon != 0) { ImageView glyph = new ImageView(context); glyph.setImageResource(row.icon); glyph.setColorFilter(row.danger ? RED : row.selected ? ACCENT : INK); LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Math.round(20 * density), Math.round(20 * density)); gp.rightMargin = Math.round(12 * density); line.addView(glyph, gp); }
            TextView text = new TextView(context); text.setText(row.label); text.setTextSize(15); text.setSingleLine(); text.setEllipsize(android.text.TextUtils.TruncateAt.END); text.setTextColor(row.danger ? RED : row.selected ? ACCENT : INK); if (row.selected) text.setTypeface(Typeface.DEFAULT_BOLD);
            line.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            if (row.selected || row.submenu) { TextView mark = new TextView(context); mark.setText(row.submenu ? "›" : "✓"); mark.setTextSize(row.submenu ? 18 : 14); mark.setTextColor(row.submenu ? GRAY : ACCENT); mark.setPadding(Math.round(10 * density), 0, 0, 0); line.addView(mark, new LinearLayout.LayoutParams(-2, -2)); }
            line.setOnClickListener(v -> { if (holder[0] != null) holder[0].dismiss(); if (row.action != null) row.action.run(); });
            list.addView(line, new LinearLayout.LayoutParams(-1, Math.round(44 * density)));
        }
        ScrollView scroll = new ScrollView(context) { @Override protected void onMeasure(int w, int h) { super.onMeasure(w, MeasureSpec.makeMeasureSpec(Math.round(screenH * .6f), MeasureSpec.AT_MOST)); } };
        scroll.setVerticalScrollBarEnabled(false); scroll.addView(list); card.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        if (shortcuts != null && !shortcuts.isEmpty()) {
            View line = new View(context); line.setBackgroundColor(LINE); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, Math.round(density * .5f))); lp.setMargins(Math.round(8 * density), Math.round(4 * density), Math.round(8 * density), Math.round(2 * density)); card.addView(line, lp);
            LinearLayout icons = new LinearLayout(context); icons.setGravity(Gravity.CENTER);
            for (final Shortcut s : shortcuts) {
                ImageButton b = new ImageButton(context); b.setImageResource(s.icon); b.setColorFilter(s.active ? ACCENT : INK); b.setBackgroundColor(Color.TRANSPARENT); b.setContentDescription(s.description); b.setPadding(Math.round(10 * density), Math.round(10 * density), Math.round(10 * density), Math.round(10 * density)); b.setScaleType(ImageView.ScaleType.FIT_CENTER);
                b.setOnClickListener(v -> { if (holder[0] != null) holder[0].dismiss(); s.action.run(); });
                icons.addView(b, new LinearLayout.LayoutParams(0, Math.round(44 * density), 1));
            }
            card.addView(icons, new LinearLayout.LayoutParams(-1, Math.round(44 * density)));
        }
        card.measure(View.MeasureSpec.makeMeasureSpec(Math.round(Math.min(screenW - 24 * density, (wide ? 268 : 240) * density)), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST));
        int w = card.getMeasuredWidth(), h = card.getMeasuredHeight();
        PopupWindow window = new PopupWindow(card, w, h, true); holder[0] = window;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); window.setElevation(12 * density); window.setOutsideTouchable(true);
        int[] at = new int[2]; anchor.getLocationOnScreen(at);
        int margin = Math.round(8 * density), gap = Math.round(6 * density);
        int x = above ? at[0] + anchor.getWidth() / 2 - w / 2 : at[0] + anchor.getWidth() - w;
        x = Math.max(margin, Math.min(screenW - w - margin, x));
        int y = above ? at[1] - h - gap : at[1] + anchor.getHeight() + gap;
        y = Math.max(margin, Math.min(screenH - h - margin, y));
        window.showAtLocation(anchor.getRootView(), Gravity.TOP | Gravity.START, x, y);
        return window;
    }
    static List<Row> rows(Row... rows) { List<Row> list = new ArrayList<>(); for (Row r : rows) list.add(r); return list; }
}

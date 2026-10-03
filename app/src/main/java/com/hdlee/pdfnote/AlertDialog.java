package com.hdlee.pdfnote;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * App-wide dialog with an iOS-like look and the same builder API as android.app.AlertDialog:
 * a centered rounded card for title/message/custom view with hairline-separated buttons, and a bottom action sheet
 * (centered blue rows plus a separate Cancel card) when the dialog lists items.
 */
final class AlertDialog extends Dialog {
    static final int BUTTON_POSITIVE = DialogInterface.BUTTON_POSITIVE, BUTTON_NEGATIVE = DialogInterface.BUTTON_NEGATIVE, BUTTON_NEUTRAL = DialogInterface.BUTTON_NEUTRAL;
    private static final int ACCENT = 0xFF007AFF, INK = 0xFF1C1C1E, GRAY = 0xFF8E8E93, LINE = 0xFFE5E5EA;
    private final TextView[] buttons = new TextView[3];
    private final boolean sheet;
    private TextView titleView, messageView;

    private AlertDialog(Context context, boolean sheet) { super(context, R.style.SheetDialog); this.sheet = sheet; }

    TextView getButton(int which) { return buttons[-which - 1]; }
    CharSequence getTitleText() { return titleView == null ? null : titleView.getText(); }

    @Override public void show() {
        super.show();
        Window window = getWindow();
        if (window == null) return;
        float density = getContext().getResources().getDisplayMetrics().density;
        int screen = getContext().getResources().getDisplayMetrics().widthPixels;
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (sheet) { window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL); window.setLayout(Math.min(screen, Math.round(480 * density)), ViewGroup.LayoutParams.WRAP_CONTENT); }
        else { window.setGravity(Gravity.CENTER); window.setWindowAnimations(android.R.style.Animation_Dialog); window.setLayout(Math.min(screen - Math.round(48 * density), Math.round(330 * density)), ViewGroup.LayoutParams.WRAP_CONTENT); }
    }

    private static final class LimitedScroll extends ScrollView {
        private final int max;
        LimitedScroll(Context c, int max) { super(c); this.max = max; setVerticalScrollBarEnabled(false); }
        @Override protected void onMeasure(int w, int h) { super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST)); }
    }

    static final class Builder {
        private final Context context; private final float density;
        private CharSequence title, message; private View view; private CharSequence[] items; private int checked = -1; private boolean choice;
        private DialogInterface.OnClickListener itemListener;
        private final CharSequence[] labels = new CharSequence[3]; private final DialogInterface.OnClickListener[] listeners = new DialogInterface.OnClickListener[3];
        private boolean cancelable = true; private DialogInterface.OnDismissListener dismiss; private DialogInterface.OnCancelListener cancel;

        Builder(Context context) { this.context = context; density = context.getResources().getDisplayMetrics().density; }
        Builder setTitle(CharSequence t) { title = t; return this; }
        Builder setMessage(CharSequence m) { message = m; return this; }
        Builder setView(View v) { view = v; return this; }
        Builder setItems(CharSequence[] list, DialogInterface.OnClickListener l) { items = list; itemListener = l; choice = false; return this; }
        Builder setSingleChoiceItems(CharSequence[] list, int selected, DialogInterface.OnClickListener l) { items = list; checked = selected; itemListener = l; choice = true; return this; }
        Builder setPositiveButton(CharSequence t, DialogInterface.OnClickListener l) { labels[0] = t; listeners[0] = l; return this; }
        Builder setNegativeButton(CharSequence t, DialogInterface.OnClickListener l) { labels[1] = t; listeners[1] = l; return this; }
        Builder setNeutralButton(CharSequence t, DialogInterface.OnClickListener l) { labels[2] = t; listeners[2] = l; return this; }
        Builder setCancelable(boolean c) { cancelable = c; return this; }
        Builder setOnDismissListener(DialogInterface.OnDismissListener l) { dismiss = l; return this; }
        Builder setOnCancelListener(DialogInterface.OnCancelListener l) { cancel = l; return this; }
        AlertDialog show() { AlertDialog d = create(); d.show(); return d; }

        private int dp(float v) { return Math.round(v * density); }
        private GradientDrawable round(int color, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g; }
        private View hairline(boolean vertical) { View v = new View(context); v.setBackgroundColor(LINE); v.setLayoutParams(vertical ? new LinearLayout.LayoutParams(Math.max(1, dp(.5f)), -1) : new LinearLayout.LayoutParams(-1, Math.max(1, dp(.5f)))); return v; }

        AlertDialog create() {
            final AlertDialog dialog = new AlertDialog(context, items != null);
            dialog.setCancelable(cancelable); dialog.setCanceledOnTouchOutside(cancelable);
            if (dismiss != null) dialog.setOnDismissListener(dismiss);
            if (cancel != null) dialog.setOnCancelListener(cancel);
            final int[] which = {BUTTON_POSITIVE, BUTTON_NEGATIVE, BUTTON_NEUTRAL};
            for (int i = 0; i < 3; i++) {
                final int slot = i;
                TextView b = new TextView(context); b.setTextSize(17); b.setGravity(Gravity.CENTER); b.setSingleLine(); b.setText(labels[i]); b.setContentDescription(labels[i]); b.setPadding(dp(12), 0, dp(12), 0);
                b.setTextColor(ACCENT); b.setTypeface(i == 0 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                if (i == 1 && labels[i] != null && labels[i].toString().contains("삭제")) b.setTextColor(0xFFFF3B30);
                b.setOnClickListener(v -> { if (listeners[slot] != null) listeners[slot].onClick(dialog, which[slot]); dialog.dismiss(); });
                dialog.buttons[i] = b;
            }
            FrameLayout frame = new FrameLayout(context);
            if (items == null) frame.addView(card(dialog), new FrameLayout.LayoutParams(-1, -2)); else frame.addView(sheet(dialog), new FrameLayout.LayoutParams(-1, -2));
            dialog.setContentView(frame);
            Window w = dialog.getWindow(); if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            return dialog;
        }

        private View card(AlertDialog dialog) {
            LinearLayout card = new LinearLayout(context); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(round(Color.WHITE, 18)); card.setTag("alert_card");
            LinearLayout body = new LinearLayout(context); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(20), dp(20), dp(20), dp(16));
            if (title != null) { TextView t = new TextView(context); t.setText(title); t.setTextSize(17); t.setTextColor(INK); t.setTypeface(Typeface.DEFAULT_BOLD); t.setGravity(Gravity.CENTER); t.setTag("dialog_title"); body.addView(t, new LinearLayout.LayoutParams(-1, -2)); dialog.titleView = t; }
            if (message != null) { TextView m = new TextView(context); m.setText(message); m.setTextSize(14); m.setTextColor(0xFF48484A); m.setGravity(Gravity.CENTER); m.setLineSpacing(0, 1.15f); m.setTag("dialog_message"); LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2); mp.topMargin = dp(title == null ? 0 : 8); LimitedScroll s = new LimitedScroll(context, Math.round(context.getResources().getDisplayMetrics().heightPixels * .5f)); s.addView(m); body.addView(s, mp); dialog.messageView = m; }
            if (view != null) { if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view); LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, -2); vp.topMargin = dp(title == null && message == null ? 0 : 12); body.addView(view, vp); }
            card.addView(body, new LinearLayout.LayoutParams(-1, -2));
            int count = 0; for (CharSequence l : labels) if (l != null) count++;
            if (count == 0) return card;
            card.addView(hairline(false));
            boolean row = count <= 2 && labels[2] == null;
            LinearLayout bar = new LinearLayout(context); bar.setOrientation(row ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
            int[] order = row ? new int[]{1, 0} : new int[]{0, 2, 1}; boolean first = true;
            for (int slot : order) {
                if (labels[slot] == null) continue;
                if (!first) bar.addView(hairline(row)); first = false;
                bar.addView(dialog.buttons[slot], row ? new LinearLayout.LayoutParams(0, dp(48), 1) : new LinearLayout.LayoutParams(-1, dp(48)));
            }
            card.addView(bar, new LinearLayout.LayoutParams(-1, -2));
            return card;
        }

        private View sheet(AlertDialog dialog) {
            LinearLayout root = new LinearLayout(context); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(10), 0, dp(10), dp(12)); root.setTag("action_sheet");
            LinearLayout list = new LinearLayout(context); list.setOrientation(LinearLayout.VERTICAL); list.setBackground(round(Color.WHITE, 14));
            if (title != null) { TextView t = new TextView(context); t.setText(title); t.setTextSize(13); t.setTextColor(GRAY); t.setGravity(Gravity.CENTER); t.setPadding(dp(16), dp(14), dp(16), dp(12)); t.setTag("dialog_title"); list.addView(t, new LinearLayout.LayoutParams(-1, -2)); dialog.titleView = t; }
            if (message != null) { TextView m = new TextView(context); m.setText(message); m.setTextSize(13); m.setTextColor(GRAY); m.setGravity(Gravity.CENTER); m.setPadding(dp(16), 0, dp(16), dp(12)); list.addView(m, new LinearLayout.LayoutParams(-1, -2)); }
            LinearLayout rows = new LinearLayout(context); rows.setOrientation(LinearLayout.VERTICAL);
            final TextView[] cells = new TextView[items.length]; final int[] current = {checked};
            Runnable refresh = () -> { for (int i = 0; i < cells.length; i++) { boolean on = choice && i == current[0]; cells[i].setText(on ? Glyph.check(cells[i].getContext(), 0xFF007AFF, items[i]) : items[i]); cells[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT); } };
            for (int i = 0; i < items.length; i++) {
                final int index = i;
                if (i > 0 || title != null || message != null) rows.addView(hairline(false));
                TextView cell = new TextView(context); cell.setTextSize(18); cell.setTextColor(ACCENT); cell.setGravity(Gravity.CENTER); cell.setContentDescription(items[i]); cell.setSingleLine(); cell.setEllipsize(android.text.TextUtils.TruncateAt.END); cell.setPadding(dp(16), 0, dp(16), 0);
                cell.setOnClickListener(v -> { if (choice) { current[0] = index; refresh.run(); } if (itemListener != null) itemListener.onClick(dialog, index); if (!choice) dialog.dismiss(); });
                cells[i] = cell; rows.addView(cell, new LinearLayout.LayoutParams(-1, dp(54)));
            }
            refresh.run();
            LimitedScroll scroll = new LimitedScroll(context, Math.round(context.getResources().getDisplayMetrics().heightPixels * .55f)); scroll.addView(rows); list.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
            for (int slot : new int[]{0, 2}) if (labels[slot] != null) { list.addView(hairline(false)); list.addView(dialog.buttons[slot], new LinearLayout.LayoutParams(-1, dp(54))); }
            if (view != null) { if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view); list.addView(view, new LinearLayout.LayoutParams(-1, -2)); }
            root.addView(list, new LinearLayout.LayoutParams(-1, -2));
            TextView cancelButton = dialog.buttons[1]; if (labels[1] == null) { cancelButton.setText("취소"); cancelButton.setContentDescription("취소"); cancelButton.setOnClickListener(v -> dialog.dismiss()); }
            cancelButton.setTextSize(18); cancelButton.setTypeface(Typeface.DEFAULT_BOLD); cancelButton.setBackground(round(Color.WHITE, 14));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(54)); cp.topMargin = dp(8); root.addView(cancelButton, cp);
            return root;
        }
    }
}

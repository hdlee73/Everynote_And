package com.hdlee.pdfnote;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;

/** Vector icons that sit inside text, replacing font glyphs such as ✓ ▸ ‹ whose shapes differ from device to device. */
final class Glyph {
    private Glyph() {}

    static ImageSpan span(Context context, int resource, int color, int sizeDp) {
        float density = context.getResources().getDisplayMetrics().density;
        Drawable drawable = context.getDrawable(resource).mutate();
        drawable.setTint(color);
        int size = Math.round(sizeDp * density);
        drawable.setBounds(0, 0, size, size);
        return new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM);
    }

    /** An icon followed by a label; pass an empty label for an icon alone. */
    static CharSequence leading(Context context, int resource, int color, int sizeDp, CharSequence label) {
        SpannableStringBuilder text = new SpannableStringBuilder("￼");
        text.setSpan(span(context, resource, color, sizeDp), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (label != null && label.length() > 0) text.append("  ").append(label);
        return text;
    }

    /** A round accent badge with a clean check mark. */
    static CharSequence check(Context context, int color, CharSequence label) {
        return leading(context, R.drawable.ic_check_bold, color, 18, label);
    }
}

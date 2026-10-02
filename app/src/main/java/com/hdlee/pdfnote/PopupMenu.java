package com.hdlee.pdfnote;

import android.content.Context;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Drop-in replacement for android.widget.PopupMenu that shows the app's compact anchored menu card. */
final class PopupMenu {
    static final class Item {
        private final int id; private final String title;
        Item(int id, String title) { this.id = id; this.title = title; }
        int getItemId() { return id; }
        String getTitle() { return title; }
    }
    static final class Menu {
        private final List<Item> items = new ArrayList<>();
        Item add(CharSequence title) { return add(0, items.size() + 1, items.size(), title); }
        Item add(int group, int id, int order, CharSequence title) { Item item = new Item(id, String.valueOf(title)); items.add(item); return item; }
    }
    interface OnMenuItemClickListener { boolean onMenuItemClick(Item item); }

    private final Context context; private final View anchor; private final Menu menu = new Menu(); private OnMenuItemClickListener listener;
    PopupMenu(Context context, View anchor) { this.context = context; this.anchor = anchor; }
    Menu getMenu() { return menu; }
    void setOnMenuItemClickListener(OnMenuItemClickListener l) { listener = l; }
    void show() {
        List<AnchoredMenu.Row> rows = new ArrayList<>();
        for (Item item : menu.items) {
            boolean checked = item.title.startsWith("✓");
            String label = checked ? item.title.substring(1).trim() : item.title;
            rows.add(new AnchoredMenu.Row(label, 0, () -> { if (listener != null) listener.onMenuItemClick(item); }).selected(checked));
        }
        AnchoredMenu.show(context, anchor, false, rows, null);
    }
}

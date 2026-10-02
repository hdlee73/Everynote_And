package com.hdlee.pdfnote;

import android.content.Context;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Drop-in replacement for android.widget.PopupMenu that shows the app's bottom action sheet instead of a floating list. */
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

    private final Context context; private final Menu menu = new Menu(); private OnMenuItemClickListener listener;
    PopupMenu(Context context, View anchor) { this.context = context; }
    Menu getMenu() { return menu; }
    void setOnMenuItemClickListener(OnMenuItemClickListener l) { listener = l; }
    void show() {
        CharSequence[] titles = new CharSequence[menu.items.size()];
        for (int i = 0; i < titles.length; i++) titles[i] = menu.items.get(i).title;
        new AlertDialog.Builder(context).setItems(titles, (d, index) -> { if (listener != null) listener.onMenuItemClick(menu.items.get(index)); }).show();
    }
}

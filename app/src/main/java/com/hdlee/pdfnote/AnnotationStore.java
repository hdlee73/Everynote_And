package com.hdlee.pdfnote;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class AnnotationStore {
    static final class Mark {
        int page;
        float left, top, right, bottom;
        int color;
        String note;
        boolean noteOnly;

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("page", page).put("left", left).put("top", top)
                    .put("right", right).put("bottom", bottom)
                    .put("color", color).put("note", note == null ? "" : note)
                    .put("noteOnly", noteOnly);
            return o;
        }

        static Mark fromJson(JSONObject o) {
            Mark m = new Mark();
            m.page = o.optInt("page");
            m.left = (float) o.optDouble("left");
            m.top = (float) o.optDouble("top");
            m.right = (float) o.optDouble("right");
            m.bottom = (float) o.optDouble("bottom");
            m.color = o.optInt("color", 0x66FFEB3B);
            m.note = o.optString("note", "");
            m.noteOnly = o.optBoolean("noteOnly", false);
            return m;
        }
    }

    static final class OutlineItem {
        int page;
        float x, y;
        String title;

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("page", page).put("x", x).put("y", y).put("title", title);
        }

        static OutlineItem fromJson(JSONObject o) {
            OutlineItem item = new OutlineItem();
            item.page = o.optInt("page");
            item.x = (float) o.optDouble("x", 0.5);
            item.y = (float) o.optDouble("y", 0.5);
            item.title = o.optString("title", "개요");
            return item;
        }
    }

    private final SharedPreferences prefs;
    private String key;
    final List<Mark> marks = new ArrayList<>();
    final Set<Integer> bookmarks = new HashSet<>();
    final List<OutlineItem> outlines = new ArrayList<>();

    AnnotationStore(Context context) {
        prefs = context.getSharedPreferences("pdf_note_data", Context.MODE_PRIVATE);
    }

    void open(Uri uri) {
        key = "doc_" + sha256(uri.toString());
        marks.clear();
        bookmarks.clear();
        outlines.clear();
        try {
            JSONObject root = new JSONObject(prefs.getString(key, "{}"));
            JSONArray a = root.optJSONArray("marks");
            if (a != null) for (int i = 0; i < a.length(); i++) marks.add(Mark.fromJson(a.getJSONObject(i)));
            JSONArray b = root.optJSONArray("bookmarks");
            if (b != null) for (int i = 0; i < b.length(); i++) bookmarks.add(b.getInt(i));
            JSONArray o = root.optJSONArray("outlines");
            if (o != null) for (int i = 0; i < o.length(); i++) outlines.add(OutlineItem.fromJson(o.getJSONObject(i)));
        } catch (JSONException ignored) { }
    }

    void save() {
        if (key == null) return;
        try {
            JSONObject root = new JSONObject();
            JSONArray a = new JSONArray();
            for (Mark m : marks) a.put(m.toJson());
            JSONArray b = new JSONArray();
            for (int page : bookmarks) b.put(page);
            JSONArray o = new JSONArray();
            for (OutlineItem item : outlines) o.put(item.toJson());
            root.put("marks", a).put("bookmarks", b).put("outlines", o);
            prefs.edit().putString(key, root.toString()).apply();
        } catch (JSONException ignored) { }
    }

    String exportJson(Uri uri, String title) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("format", "PDF Note annotations v1");
        root.put("document", title);
        root.put("uri", uri.toString());
        JSONArray a = new JSONArray();
        for (Mark m : marks) a.put(m.toJson());
        JSONArray b = new JSONArray();
        for (int page : bookmarks) b.put(page);
        JSONArray o = new JSONArray();
        for (OutlineItem item : outlines) o.put(item.toJson());
        root.put("marks", a).put("bookmarks", b).put("outlines", o);
        return root.toString(2);
    }

    private static String sha256(String input) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format("%02x", b));
            return result.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}

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

    static final class InkPoint {
        float x, y, pressure;
        InkPoint(float x, float y, float pressure) { this.x=x; this.y=y; this.pressure=pressure; }
        JSONObject toJson() throws JSONException { return new JSONObject().put("x",x).put("y",y).put("p",pressure); }
        static InkPoint fromJson(JSONObject o) { return new InkPoint((float)o.optDouble("x"),(float)o.optDouble("y"),(float)o.optDouble("p",0.5)); }
    }

    static final class InkStroke {
        int page, color;
        float width;
        final List<InkPoint> points=new ArrayList<>();
        JSONObject toJson() throws JSONException { JSONObject o=new JSONObject().put("page",page).put("color",color).put("width",width);JSONArray a=new JSONArray();for(InkPoint p:points)a.put(p.toJson());return o.put("points",a); }
        static InkStroke fromJson(JSONObject o) throws JSONException { InkStroke s=new InkStroke();s.page=o.optInt("page");s.color=o.optInt("color",0xFF172033);s.width=(float)o.optDouble("width",0.004);JSONArray a=o.optJSONArray("points");if(a!=null)for(int i=0;i<a.length();i++)s.points.add(InkPoint.fromJson(a.getJSONObject(i)));return s; }
    }

    static final class TranslationNote {
        int page;
        float left, top, right, bottom;
        String source, translated;
        boolean visible = true;

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("page", page).put("left", left).put("top", top)
                    .put("right", right).put("bottom", bottom).put("source", source)
                    .put("translated", translated).put("visible", visible);
        }

        static TranslationNote fromJson(JSONObject o) {
            TranslationNote n = new TranslationNote();
            n.page=o.optInt("page"); n.left=(float)o.optDouble("left"); n.top=(float)o.optDouble("top");
            n.right=(float)o.optDouble("right"); n.bottom=(float)o.optDouble("bottom");
            n.source=o.optString("source",""); n.translated=o.optString("translated","");
            n.visible=o.optBoolean("visible",true); return n;
        }
    }

    private final SharedPreferences prefs;
    private String key;
    final List<Mark> marks = new ArrayList<>();
    final Set<Integer> bookmarks = new HashSet<>();
    final List<OutlineItem> outlines = new ArrayList<>();
    final List<InkStroke> strokes = new ArrayList<>();
    final List<TranslationNote> translations = new ArrayList<>();

    AnnotationStore(Context context) {
        prefs = context.getSharedPreferences("pdf_note_data", Context.MODE_PRIVATE);
    }

    void open(Uri uri) {
        key = "doc_" + sha256(uri.toString());
        marks.clear();
        bookmarks.clear();
        outlines.clear();
        strokes.clear();
        translations.clear();
        try {
            JSONObject root = new JSONObject(prefs.getString(key, "{}"));
            JSONArray a = root.optJSONArray("marks");
            if (a != null) for (int i = 0; i < a.length(); i++) marks.add(Mark.fromJson(a.getJSONObject(i)));
            JSONArray b = root.optJSONArray("bookmarks");
            if (b != null) for (int i = 0; i < b.length(); i++) bookmarks.add(b.getInt(i));
            JSONArray o = root.optJSONArray("outlines");
            if (o != null) for (int i = 0; i < o.length(); i++) outlines.add(OutlineItem.fromJson(o.getJSONObject(i)));
            JSONArray s = root.optJSONArray("strokes");
            if (s != null) for (int i = 0; i < s.length(); i++) strokes.add(InkStroke.fromJson(s.getJSONObject(i)));
            JSONArray t = root.optJSONArray("translations");
            if (t != null) for (int i = 0; i < t.length(); i++) translations.add(TranslationNote.fromJson(t.getJSONObject(i)));
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
            JSONArray s = new JSONArray();
            for (InkStroke stroke : strokes) s.put(stroke.toJson());
            JSONArray t = new JSONArray();
            for (TranslationNote note : translations) t.put(note.toJson());
            root.put("marks", a).put("bookmarks", b).put("outlines", o).put("strokes", s).put("translations",t);
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
        JSONArray s = new JSONArray();
        for (InkStroke stroke : strokes) s.put(stroke.toJson());
        JSONArray t = new JSONArray();
        for (TranslationNote note : translations) t.put(note.toJson());
        root.put("marks", a).put("bookmarks", b).put("outlines", o).put("strokes", s).put("translations",t);
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

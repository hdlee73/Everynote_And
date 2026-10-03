package com.hdlee.pdfnote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.DashPathEffect;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.List;
import java.util.ArrayList;
import java.util.IdentityHashMap;

final class PdfPageView extends View {
    interface Listener {
        void onHighlightCreated(AnnotationStore.Mark mark);
        void onMarkTapped(AnnotationStore.Mark mark);
        void onMemoPointRequested(int page, float x, float y);
        void onZoomGestureStarted();
        void onPageSwipe(int direction);
        void onOutlinePointRequested(int page, float x, float y);
        void onInkChanged();
        void onTextSelectionFinished(TextSelection selection, float anchorX, float anchorY);
        void onTranslationTapped(AnnotationStore.TranslationNote note);
        void onSelectionAdjustStarted();
        void onLassoSelectionFinished();
        void onElementTapped(AnnotationStore.PageElement element);
    }

    static final class TextRegion {
        final String word, line; final RectF wordBounds, lineBounds;
        TextRegion(String word,String line,RectF wordBounds,RectF lineBounds){this.word=word;this.line=line;this.wordBounds=wordBounds;this.lineBounds=lineBounds;}
    }

    static final class TextSelection {
        final String text;
        final List<RectF> bounds;
        final RectF unionBounds;
        final boolean singleWord;
        TextSelection(String text,List<RectF> bounds,RectF unionBounds,boolean singleWord){this.text=text;this.bounds=bounds;this.unionBounds=unionBounds;this.singleWord=singleWord;}
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private Bitmap bitmap;
    private AnnotationStore annotationStore;
    private boolean edgeSwipe,bodySwipeCandidate,directTextSelection;
    private long edgeStartTime;
    private List<AnnotationStore.Mark> marks;
    private List<AnnotationStore.InkStroke> strokes;
    private List<AnnotationStore.TranslationNote> translations;
    private List<TextRegion> textRegions = new ArrayList<>();
    private final List<TextRegion> selectedTextRegions = new ArrayList<>();
    private final Handler selectionHandler = new Handler(Looper.getMainLooper());
    private final IdentityHashMap<AnnotationStore.TranslationNote,RectF> noteHitBoxes=new IdentityHashMap<>();
    private final IdentityHashMap<AnnotationStore.Mark,RectF> memoHitBoxes=new IdentityHashMap<>();
    private boolean lassoMode, lassoDrawing, suppressSelection;
    private final List<PointF> lassoPoints=new ArrayList<>();
    static final int LASSO_FREE=0,LASSO_RECT=1,LASSO_CIRCLE=2;
    private int lassoShape=LASSO_FREE;
    private PointF lassoAnchor;
    private int searchPage=-1;
    private List<RectF> searchBoxes=new ArrayList<>();
    private RectF searchCurrent;
    private final Paint searchPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lassoPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean textSelectMode;
    private boolean showTextBounds;
    private int page;
    private boolean highlightMode;
    private boolean memoMode;
    private int highlightColor = 0x66FFEB3B;
    private float startX, startY, currentX, currentY, lastX, lastY;
    private float panX, panY;
    private boolean drawing, panning, gestureMoved, scalingOccurred;
    private boolean verticalPageSwipe;
    private boolean outlineMode;
    private int inkMode;
    private int inkColor=0xFF1C1C1E;
    private float inkWidth=0.004f;
    private AnnotationStore.InkStroke activeStroke;
    private boolean stylusDrawing;
    private boolean fingerInk,pageSwipeEnabled;
    private float scale = 1f;
    private final RectF crop = new RectF(0, 0, 1, 1), bounds = new RectF(0, 0, 1, 1);
    private int paperColor = 0xFFDDDDDD;
    private PageDrag pageDrag; private boolean dragging; private int dragDirection; private android.view.VelocityTracker dragTracker;
    /** Lets the host follow a finger while it turns the page (Kindle style). */
    interface PageDrag { boolean start(int direction); void move(float distance); void end(float velocity); }
    void setPageDrag(PageDrag drag) { pageDrag = drag; }
    /** Converts a point in this view to normalized page coordinates (clamped to the page). */
    float[] toPage(float vx,float vy){RectF d=contentRect();if(d.width()<=0||d.height()<=0)return new float[]{.5f,.5f};return new float[]{Math.max(0f,Math.min(1f,(vx-d.left)/d.width())),Math.max(0f,Math.min(1f,(vy-d.top)/d.height()))};}
    private AnnotationStore.PageElement selectedElement;
    private int elementDrag; private boolean elementMoved; private float elementStartX, elementStartY; private final RectF elementOrigin = new RectF();
    /** Shows move and resize handles around an attached picture, sticker, video or link box. */
    void selectElement(AnnotationStore.PageElement element) { selectedElement = element; invalidate(); }
    AnnotationStore.PageElement selectedElement() { return selectedElement; }
    private static boolean resizable(AnnotationStore.PageElement e) { return e != null && !e.kind.equals("text") && !e.kind.equals("audio"); }
    static boolean selectable(AnnotationStore.PageElement e){return e.kind.equals("image")||e.kind.equals("sticker")||e.kind.equals("video")||e.kind.equals("shape")||e.kind.equals("table")||e.kind.equals("youtube");}
    private static boolean aspectLocked(AnnotationStore.PageElement e) { return e.kind.equals("image") || e.kind.equals("sticker") || e.kind.equals("video") || e.kind.equals("youtube"); }
    private void drawElementHandles(Canvas canvas, RectF dest) {
        if (selectedElement == null || selectedElement.page != page || annotationStore == null || !annotationStore.elements.contains(selectedElement) || dest.width() <= 0) return;
        float density = getResources().getDisplayMetrics().density; RectF b = AnnotationPainter.box(dest, selectedElement);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f * density); p.setColor(0xFF007AFF); canvas.drawRect(b, p);
        float[][] corners = {{b.left, b.top}, {b.right, b.top}, {b.left, b.bottom}, {b.right, b.bottom}};
        for (float[] c : corners) { p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE); canvas.drawCircle(c[0], c[1], 9f * density, p); p.setStyle(Paint.Style.STROKE); p.setColor(0xFF007AFF); canvas.drawCircle(c[0], c[1], 9f * density, p); }
    }
    private boolean handleElementGesture(MotionEvent e, RectF dest) {
        if (selectedElement == null || annotationStore == null || dest.width() <= 0) return false;
        if (selectedElement.page != page || !annotationStore.elements.contains(selectedElement)) { selectedElement = null; return false; }
        int action = e.getActionMasked(); float density = getResources().getDisplayMetrics().density;
        if (action == MotionEvent.ACTION_DOWN && e.getPointerCount() == 1 && !isStylus(e)) {
            RectF b = AnnotationPainter.box(dest, selectedElement); float reach = 24f * density; int hit = 0;
            if (Math.hypot(e.getX() - b.left, e.getY() - b.top) <= reach) hit = 2; else if (Math.hypot(e.getX() - b.right, e.getY() - b.top) <= reach) hit = 3;
            else if (Math.hypot(e.getX() - b.left, e.getY() - b.bottom) <= reach) hit = 4; else if (Math.hypot(e.getX() - b.right, e.getY() - b.bottom) <= reach) hit = 5;
            else if (b.contains(e.getX(), e.getY())) hit = 1;
            if (hit == 0) { selectedElement = null; invalidate(); return false; }
            if (hit > 1 && !resizable(selectedElement)) hit = 1;
            listener.onSelectionAdjustStarted(); elementDrag = hit; elementMoved = false; elementStartX = e.getX(); elementStartY = e.getY();
            elementOrigin.set(selectedElement.left, selectedElement.top, selectedElement.right, selectedElement.bottom);
            getParent().requestDisallowInterceptTouchEvent(true); return true;
        }
        if (elementDrag == 0) return false;
        if (action == MotionEvent.ACTION_MOVE) {
            float dx = (e.getX() - elementStartX) / dest.width(), dy = (e.getY() - elementStartY) / dest.height();
            if (!elementMoved && Math.hypot(e.getX() - elementStartX, e.getY() - elementStartY) < 8f * density) return true;
            elementMoved = true; AnnotationStore.PageElement el = selectedElement; float w = elementOrigin.width(), h = elementOrigin.height();
            if (elementDrag == 1) {
                float left = Math.max(0f, Math.min(1f - w, elementOrigin.left + dx)), top = Math.max(0f, Math.min(1f - h, elementOrigin.top + dy));
                el.left = left; el.top = top; el.right = left + w; el.bottom = top + h;
            } else {
                boolean leftCorner = elementDrag == 2 || elementDrag == 4, topCorner = elementDrag == 2 || elementDrag == 3;
                float fx = leftCorner ? elementOrigin.right : elementOrigin.left, fy = topCorner ? elementOrigin.bottom : elementOrigin.top;
                float nx = Math.max(0f, Math.min(1f, (e.getX() - dest.left) / dest.width())), ny = Math.max(0f, Math.min(1f, (e.getY() - dest.top) / dest.height()));
                float nw = Math.max(.04f, leftCorner ? fx - nx : nx - fx), nh = Math.max(.03f, topCorner ? fy - ny : ny - fy);
                if (aspectLocked(el)) {
                    float ratio = (w * dest.width()) / (h * dest.height()); nh = nw * dest.width() / (ratio * dest.height());
                    float room = topCorner ? fy : 1f - fy; if (nh > room) { nh = room; nw = nh * ratio * dest.height() / dest.width(); }
                    float roomX = leftCorner ? fx : 1f - fx; if (nw > roomX) { nw = roomX; nh = nw * dest.width() / (ratio * dest.height()); }
                }
                el.left = leftCorner ? fx - nw : fx; el.right = leftCorner ? fx : fx + nw; el.top = topCorner ? fy - nh : fy; el.bottom = topCorner ? fy : fy + nh;
            }
            invalidate(); return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean moved = elementMoved; elementDrag = 0; elementMoved = false; getParent().requestDisallowInterceptTouchEvent(false);
            if (action == MotionEvent.ACTION_CANCEL) { selectedElement.left = elementOrigin.left; selectedElement.top = elementOrigin.top; selectedElement.right = elementOrigin.right; selectedElement.bottom = elementOrigin.bottom; invalidate(); }
            else if (moved) listener.onInkChanged(); else listener.onElementTapped(selectedElement);
            return true;
        }
        return true;
    }
    private final Listener listener;
    private TextRegion selectionStartRegion, selectionEndRegion;
    private boolean selectionCandidate, selectingText;
    private final Runnable beginTextSelection = () -> {
        if (!selectionCandidate || selectionStartRegion == null) return;
        bodySwipeCandidate=false;
        selectingText = true;
        selectionCandidate = false;
        panning = false;
        selectedTextRegions.clear();
        selectedTextRegions.add(selectionStartRegion);
        getParent().requestDisallowInterceptTouchEvent(true);
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        invalidate();
    };

    PdfPageView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setBackgroundColor(paperColor);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                clearLassoSelection();
                selectionHandler.removeCallbacks(beginTextSelection);
                selectingText=selectionCandidate=false;
                selectedTextRegions.clear();
                listener.onSelectionAdjustStarted();
                finishInkStroke();
                drawing = false;
                panning = false;
                scalingOccurred = true;
                listener.onZoomGestureStarted();
                return true;
            }

            @Override public boolean onScale(ScaleGestureDetector detector) {
                RectF before = contentRect();
                float focusX = detector.getFocusX();
                float focusY = detector.getFocusY();
                float nx = before.width() == 0 ? 0.5f : (focusX - before.left) / before.width();
                float ny = before.height() == 0 ? 0.5f : (focusY - before.top) / before.height();
                scale = Math.max(1f, Math.min(4f, scale * detector.getScaleFactor()));
                float[] size = contentSize();
                panX = focusX - nx * size[0] - baseLeft(size);
                panY = focusY - ny * size[1] - baseTop(size);
                clampPan();
                invalidate();
                return true;
            }

            @Override public void onScaleEnd(ScaleGestureDetector detector) {
                clampPan();
            }
        });
    }

    void showPage(Bitmap pageBitmap, int pageNumber, List<AnnotationStore.Mark> allMarks, List<AnnotationStore.InkStroke> allStrokes, List<AnnotationStore.TranslationNote> allTranslations) {
        clearLassoSelection();
        stopTextSelection();
        noteHitBoxes.clear(); memoHitBoxes.clear(); selectedElement = null; elementDrag = 0;
        if (bitmap != null && bitmap != pageBitmap) bitmap.recycle();
        bitmap = pageBitmap;
        page = pageNumber;
        marks = allMarks;
        strokes = allStrokes;
        translations=allTranslations; textRegions.clear(); selectedTextRegions.clear(); textSelectMode=false;
        scale = 1f;
        panX = panY = 0f;
        analyze();
        invalidate();
    }

    void clearPage() {
        clearLassoSelection();
        stopTextSelection();
        noteHitBoxes.clear(); memoHitBoxes.clear();
        if (bitmap != null) bitmap.recycle();
        bitmap = null;
        bounds.set(0, 0, 1, 1); crop.set(0, 0, 1, 1); paperColor = 0xFFDDDDDD; setBackgroundColor(paperColor);
        marks = null;
        strokes = null;
        translations=null; textRegions.clear(); selectedTextRegions.clear(); textSelectMode=false;
        scale = 1f;
        panX = panY = 0f;
        invalidate();
    }

    void setHighlightMode(boolean enabled, int color) {
        highlightMode = enabled;
        if (enabled) { setLassoMode(false);memoMode = false; outlineMode = false; }
        highlightColor = color;
        invalidate();
    }

    void setMemoMode(boolean enabled) {
        memoMode = enabled;
        if (enabled) { setLassoMode(false);highlightMode = false; outlineMode = false; }
        invalidate();
    }

    void setOutlineMode(boolean enabled) {
        outlineMode = enabled;
        if (enabled) { setLassoMode(false);highlightMode = false; memoMode = false; }
        invalidate();
    }

    void setInkTool(int mode, int color, float width) {
        inkMode=mode; inkColor=color; inkWidth=width;
        if(mode!=0){setLassoMode(false);highlightMode=memoMode=outlineMode=false;}
        invalidate();
    }

    void focusOnPoint(float x, float y) {
        if (bitmap == null) return;
        scale = Math.max(scale, 1.7f);
        float[] size = contentSize();
        panX = getWidth() / 2f - (baseLeft(size) + x * size[0]);
        panY = getHeight() / 2f - (baseTop(size) + y * size[1]);
        clampPan();
        invalidate();
    }

    void setDirectTextSelection(boolean enabled){directTextSelection=enabled;clearTextSelectionOverlay();}
    void copyToolsFrom(PdfPageView other){directTextSelection=other.directTextSelection;highlightMode=other.highlightMode;memoMode=other.memoMode;outlineMode=other.outlineMode;highlightColor=other.highlightColor;inkMode=other.inkMode;inkColor=other.inkColor;inkWidth=other.inkWidth;fingerInk=other.fingerInk;pageSwipeEnabled=other.pageSwipeEnabled;verticalPageSwipe=other.verticalPageSwipe;lassoShape=other.lassoShape;if(lassoMode!=other.lassoMode){lassoMode=other.lassoMode;clearLassoSelection();}invalidate();}
    void setAnnotationStore(AnnotationStore store){annotationStore=store;invalidate();}
    void setFingerInk(boolean enabled){fingerInk=enabled;}
    void setPageSwipeEnabled(boolean enabled){pageSwipeEnabled=enabled;}

    void setVerticalPageSwipe(boolean vertical) {
        verticalPageSwipe = vertical;
    }

    Bitmap copyPageBitmap(){return bitmap==null?null:bitmap.copy(Bitmap.Config.ARGB_8888,false);}
    int getPageNumber(){return page;}
    void setTextRegions(List<TextRegion> regions,boolean showBounds){
        selectionHandler.removeCallbacks(beginTextSelection);
        clearTextSelectionOverlay();
        textRegions=regions==null?new ArrayList<>():new ArrayList<>(regions);textSelectMode=true;showTextBounds=showBounds;
        invalidate();
    }
    void stopTextSelection(){directTextSelection=false;textSelectMode=false;textRegions.clear();clearTextSelectionOverlay();}
    void clearTextSelectionOverlay(){
        selectionHandler.removeCallbacks(beginTextSelection);
        selectionCandidate=selectingText=false;
        selectedTextRegions.clear();selectionStartRegion=selectionEndRegion=null;
        drawing=panning=gestureMoved=false;bodySwipeCandidate=false;
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
    }

    @Override protected void onDetachedFromWindow(){
        clearLassoSelection();
        clearTextSelectionOverlay();
        super.onDetachedFromWindow();
    }

    private float highlightHeight(RectF dest) {
        return Math.max(12f, dest.height() * 0.022f);
    }

    /** Finds the paper colour and the box that holds the printed content, so margins can be trimmed. */
    private void analyze() {
        bounds.set(0, 0, 1, 1); crop.set(0, 0, 1, 1); paperColor = 0xFFFFFFFF;
        try {
            int w = Math.min(200, bitmap.getWidth()), h = Math.min(Math.max(1, Math.round(bitmap.getHeight() * (w / (float) bitmap.getWidth()))), bitmap.getHeight()); int[] px = new int[w * h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = bitmap.getPixel(Math.min(bitmap.getWidth() - 1, x * bitmap.getWidth() / w), Math.min(bitmap.getHeight() - 1, y * bitmap.getHeight() / h));
            java.util.HashMap<Integer, Integer> votes = new java.util.HashMap<>(); int best = 0, bestVotes = 0;
            for (int i = 0; i < w * h; i++) { int x = i % w, y = i / w; if (x > 2 && x < w - 3 && y > 2 && y < h - 3) continue; int key = (px[i] & 0xFFF0F0F0); int n = votes.merge(key, 1, Integer::sum); if (n > bestVotes) { bestVotes = n; best = px[i]; } }
            paperColor = best | 0xFF000000; int minX = w, minY = h, maxX = -1, maxY = -1;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) { int c = px[y * w + x]; int diff = Math.abs(Color.red(c) - Color.red(paperColor)) + Math.abs(Color.green(c) - Color.green(paperColor)) + Math.abs(Color.blue(c) - Color.blue(paperColor)); if (diff > 60) { if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y; } }
            if (maxX >= minX && maxY >= minY) {
                float pad = 0.03f; bounds.set(Math.max(0f, minX / (float) w - pad), Math.max(0f, minY / (float) h - pad), Math.min(1f, (maxX + 1) / (float) w + pad), Math.min(1f, (maxY + 1) / (float) h + pad));
                if (bounds.width() * bounds.height() < 0.1f) bounds.set(0, 0, 1, 1);
            }
        } catch (RuntimeException ignored) { bounds.set(0, 0, 1, 1); }
        setBackgroundColor(paperColor);
    }
    /** Normalised box around the printed content (the whole page when it is nearly blank). */
    RectF contentBounds() { return new RectF(bounds); }
    int paperColor() { return paperColor; }
    /** Zooms the base view onto this normalised box so margins disappear; null shows the whole page. */
    void setCrop(RectF box) {
        if (box == null || box.width() < 0.2f || box.height() < 0.2f) crop.set(0, 0, 1, 1); else crop.set(box);
        panX = panY = 0f; invalidate();
    }

    private float[] contentSize() {
        if (bitmap == null) return new float[]{0f, 0f};
        float base = Math.min(getWidth() / (bitmap.getWidth() * crop.width()), getHeight() / (bitmap.getHeight() * crop.height()));
        float w = bitmap.getWidth() * base * scale;
        float h = bitmap.getHeight() * base * scale;
        return new float[]{w, h};
    }
    private float baseLeft(float[] size) { return (getWidth() - size[0]) / 2f - (crop.centerX() - 0.5f) * size[0]; }
    private float baseTop(float[] size) { return (getHeight() - size[1]) / 2f - (crop.centerY() - 0.5f) * size[1]; }

    private void clampPan() {
        if (bitmap == null) return;
        float[] size = contentSize();
        float maxX = Math.max(0f, (size[0] * crop.width() - getWidth()) / 2f);
        float maxY = Math.max(0f, (size[1] * crop.height() - getHeight()) / 2f);
        panX = Math.max(-maxX, Math.min(maxX, panX));
        panY = Math.max(-maxY, Math.min(maxY, panY));
        if (scale <= 1f) panX = panY = 0f;
    }

    /** Where the page is drawn inside this view (follows zoom and pan), in view pixels. */
    RectF pageRect() { return contentRect(); }

    private RectF contentRect() {
        if (bitmap == null) return new RectF();
        float[] size = contentSize();
        float left = baseLeft(size) + panX;
        float top = baseTop(size) + panY;
        return new RectF(left, top, left + size[0], top + size[1]);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) return;
        RectF dest = contentRect();
        paint.setColor(Color.WHITE);
        canvas.drawRect(dest, paint);
        canvas.drawBitmap(bitmap, null, dest, paint);
        if(!suppressSelection&&textSelectMode&&showTextBounds){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f*getResources().getDisplayMetrics().density);paint.setColor(0xAA2563EB);for(TextRegion r:textRegions){RectF b=r.wordBounds;canvas.drawRoundRect(new RectF(dest.left+b.left*dest.width(),dest.top+b.top*dest.height(),dest.left+b.right*dest.width(),dest.top+b.bottom*dest.height()),4,4,paint);}paint.setStyle(Paint.Style.FILL);}
        if(!suppressSelection&&!selectedTextRegions.isEmpty()){
            paint.setStyle(Paint.Style.FILL);paint.setColor(0x663B82F6);
            for(RectF b:selectionLineBounds()){canvas.drawRoundRect(new RectF(dest.left+b.left*dest.width(),dest.top+b.top*dest.height(),dest.left+b.right*dest.width(),dest.top+b.bottom*dest.height()),5,5,paint);}
            RectF first=selectedTextRegions.get(0).wordBounds,last=selectedTextRegions.get(selectedTextRegions.size()-1).wordBounds;float handle=5f*getResources().getDisplayMetrics().density;paint.setColor(0xFF007AFF);canvas.drawCircle(dest.left+first.left*dest.width(),dest.top+first.bottom*dest.height(),handle,paint);canvas.drawCircle(dest.left+last.right*dest.width(),dest.top+last.bottom*dest.height(),handle,paint);
        }
        if (marks != null) for (AnnotationStore.Mark m : marks) if (m.page == page) {
            if (!m.noteOnly) {
                paint.setColor(m.color);
                canvas.drawRect(dest.left + m.left * dest.width(), dest.top + m.top * dest.height(),
                        dest.left + m.right * dest.width(), dest.top + m.bottom * dest.height(), paint);
            }
        }
        if(strokes!=null){paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);for(AnnotationStore.InkStroke s:strokes)if(s.page==page&&s.points.size()>0){paint.setColor(s.color);if(s.points.size()==1){AnnotationStore.InkPoint p=s.points.get(0);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(dest.left+p.x*dest.width(),dest.top+p.y*dest.height(),strokeWidth(s.width,p.pressure,dest)/2f,paint);paint.setStyle(Paint.Style.STROKE);}else for(int i=1;i<s.points.size();i++){AnnotationStore.InkPoint a=s.points.get(i-1),b=s.points.get(i);paint.setStrokeWidth(strokeWidth(s.width,(a.pressure+b.pressure)/2f,dest));canvas.drawLine(dest.left+a.x*dest.width(),dest.top+a.y*dest.height(),dest.left+b.x*dest.width(),dest.top+b.y*dest.height(),paint);}}paint.setStyle(Paint.Style.FILL);}
        memoHitBoxes.clear();if(marks!=null)for(AnnotationStore.Mark m:marks)if(m.page==page&&m.visible&&(m.noteOnly||(m.note!=null&&!m.note.isEmpty())))drawMemo(canvas,dest,m);
        noteHitBoxes.clear();if(translations!=null)for(AnnotationStore.TranslationNote n:translations)if(n.page==page&&n.visible)drawTranslation(canvas,dest,n);
        if (!suppressSelection && drawing) {
            paint.setColor(highlightColor);
            float centerY = (startY + currentY) / 2f;
            float half = highlightHeight(dest) / 2f;
            canvas.drawRect(Math.min(startX, currentX), centerY - half,
                    Math.max(startX, currentX), centerY + half, paint);
        }
        AnnotationPainter.elements(getContext(),canvas,dest,annotationStore,page);
        if(!suppressSelection)drawElementHandles(canvas,dest);
        if(!suppressSelection&&searchPage==page)drawSearchHighlights(canvas,dest);
        if(!suppressSelection&&!lassoPoints.isEmpty()){
            Path path=lassoPath(dest);lassoPaint.setStyle(Paint.Style.FILL);lassoPaint.setColor(0x222563EB);canvas.drawPath(path,lassoPaint);
            lassoPaint.setStyle(Paint.Style.STROKE);lassoPaint.setStrokeWidth(2*getResources().getDisplayMetrics().density);lassoPaint.setColor(0xFF007AFF);
            lassoPaint.setPathEffect(new DashPathEffect(new float[]{8,5},0));canvas.drawPath(path,lassoPaint);lassoPaint.setPathEffect(null);
        }
    }

    void setLassoMode(boolean enabled){
        lassoMode=enabled;clearLassoSelection();clearTextSelectionOverlay();
        if(enabled){finishInkStroke();inkMode=0;highlightMode=memoMode=outlineMode=false;}
    }
    boolean isLassoMode(){return lassoMode;}
    int getLassoShape(){return lassoShape;}
    /** Switches between freehand, rectangle and circle selection; any selection in progress is discarded. */
    void setLassoShape(int shape){lassoShape=Math.max(LASSO_FREE,Math.min(LASSO_CIRCLE,shape));clearLassoSelection();}
    private PointF normalizedPoint(float x,float y,RectF dest){return new PointF(Math.max(0,Math.min(1,(x-dest.left)/dest.width())),Math.max(0,Math.min(1,(y-dest.top)/dest.height())));}
    /** Rebuilds the rectangle / ellipse polygon spanned by the touch-down point and the current finger position. */
    private void updateLassoShape(float x,float y,RectF dest){
        if(lassoAnchor==null||dest.width()<=0||dest.height()<=0)return;
        PointF end=normalizedPoint(x,y,dest);lassoPoints.clear();
        float left=Math.min(lassoAnchor.x,end.x),right=Math.max(lassoAnchor.x,end.x),top=Math.min(lassoAnchor.y,end.y),bottom=Math.max(lassoAnchor.y,end.y);
        if(lassoShape==LASSO_RECT){
            lassoPoints.add(new PointF(left,top));lassoPoints.add(new PointF(right,top));lassoPoints.add(new PointF(right,bottom));lassoPoints.add(new PointF(left,bottom));
        }else{
            int steps=72;float cx=(left+right)/2,cy=(top+bottom)/2,rx=(right-left)/2,ry=(bottom-top)/2;
            for(int i=0;i<steps;i++){double angle=2*Math.PI*i/steps;lassoPoints.add(new PointF((float)(cx+rx*Math.cos(angle)),(float)(cy+ry*Math.sin(angle))));}
        }
    }
    void setSearchHighlights(int pageNumber,List<RectF> others,RectF current){searchPage=pageNumber;searchBoxes=others==null?new ArrayList<>():new ArrayList<>(others);searchCurrent=current==null?null:new RectF(current);invalidate();}
    void clearSearchHighlights(){searchPage=-1;searchBoxes=new ArrayList<>();searchCurrent=null;invalidate();}
    private RectF searchRect(RectF b,RectF dest,float pad){return new RectF(dest.left+b.left*dest.width()-pad,dest.top+b.top*dest.height()-pad,dest.left+b.right*dest.width()+pad,dest.top+b.bottom*dest.height()+pad);}
    private void drawSearchHighlights(Canvas canvas,RectF dest){
        float density=getResources().getDisplayMetrics().density;
        searchPaint.setStyle(Paint.Style.FILL);searchPaint.setColor(0x66FFD54F);
        for(RectF b:searchBoxes)canvas.drawRoundRect(searchRect(b,dest,density),3*density,3*density,searchPaint);
        if(searchCurrent!=null){
            RectF b=searchRect(searchCurrent,dest,2*density);
            searchPaint.setStyle(Paint.Style.FILL);searchPaint.setColor(0x99FF9800);canvas.drawRoundRect(b,4*density,4*density,searchPaint);
            searchPaint.setStyle(Paint.Style.STROKE);searchPaint.setStrokeWidth(2*density);searchPaint.setColor(0xFFEA580C);canvas.drawRoundRect(b,4*density,4*density,searchPaint);
            searchPaint.setStyle(Paint.Style.FILL);
        }
    }
    float pageAspect(){return bitmap==null||bitmap.getWidth()==0?1.414f:(float)bitmap.getHeight()/bitmap.getWidth();}
    void clearLassoSelection(){lassoAnchor=null;lassoDrawing=false;lassoPoints.clear();if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);invalidate();}
    private void addLassoPoint(float x,float y,RectF dest){
        if(dest.width()<=0||dest.height()<=0)return;
        PointF point=new PointF(Math.max(0,Math.min(1,(x-dest.left)/dest.width())),Math.max(0,Math.min(1,(y-dest.top)/dest.height())));
        if(!lassoPoints.isEmpty()){PointF last=lassoPoints.get(lassoPoints.size()-1);if(Math.hypot((point.x-last.x)*dest.width(),(point.y-last.y)*dest.height())<2)return;}
        if(lassoPoints.size()<8192)lassoPoints.add(point);
    }
    private Path lassoPath(RectF dest){Path path=new Path();for(int i=0;i<lassoPoints.size();i++){PointF p=lassoPoints.get(i);if(i==0)path.moveTo(dest.left+p.x*dest.width(),dest.top+p.y*dest.height());else path.lineTo(dest.left+p.x*dest.width(),dest.top+p.y*dest.height());}path.close();return path;}
    private boolean validLasso(RectF dest){
        if(lassoPoints.size()<3)return false;RectF bounds=new RectF();lassoPath(dest).computeBounds(bounds,true);
        double area=0;for(int i=0;i<lassoPoints.size();i++){PointF a=lassoPoints.get(i),b=lassoPoints.get((i+1)%lassoPoints.size());area+=a.x*b.y-b.x*a.y;}
        float minimum=8*getResources().getDisplayMetrics().density;
        return bounds.width()>=minimum&&bounds.height()>=minimum&&Math.abs(area)*dest.width()*dest.height()/2>=minimum*minimum;
    }
    Bitmap captureLasso(){
        RectF dest=contentRect();if(bitmap==null||!validLasso(dest))return null;
        RectF normalized=new RectF();lassoPath(new RectF(0,0,1,1)).computeBounds(normalized,true);
        int left=Math.max(0,(int)Math.floor(normalized.left*bitmap.getWidth())),top=Math.max(0,(int)Math.floor(normalized.top*bitmap.getHeight()));
        int right=Math.min(bitmap.getWidth(),(int)Math.ceil(normalized.right*bitmap.getWidth())),bottom=Math.min(bitmap.getHeight(),(int)Math.ceil(normalized.bottom*bitmap.getHeight()));
        float outputScale=Math.min(1f,(float)Math.sqrt(8000000d/((double)(right-left)*(bottom-top))));
        int width=Math.max(1,(int)Math.ceil((right-left)*outputScale)),height=Math.max(1,(int)Math.ceil((bottom-top)*outputScale));
        Bitmap capture=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(capture);
        // Clip in original-page pixels; render the same visible annotations without selection controls.
        canvas.scale(outputScale,outputScale);canvas.translate(-left,-top);
        canvas.clipPath(lassoPath(new RectF(0,0,bitmap.getWidth(),bitmap.getHeight())));
        canvas.scale(bitmap.getWidth()/dest.width(),bitmap.getHeight()/dest.height());canvas.translate(-dest.left,-dest.top);
        suppressSelection=true;try{onDraw(canvas);}catch(RuntimeException error){capture.recycle();throw error;}finally{suppressSelection=false;}
        return capture;
    }
    String lassoText(){
        if(lassoPoints.size()<3)return "";android.graphics.Region region=new android.graphics.Region();region.setPath(lassoPath(new RectF(0,0,10000,10000)),new android.graphics.Region(0,0,10000,10000));
        StringBuilder text=new StringBuilder();RectF previousLine=null;
        for(TextRegion word:textRegions)if(region.contains((int)(word.wordBounds.centerX()*10000),(int)(word.wordBounds.centerY()*10000))){if(text.length()>0)text.append(previousLine!=null&&!previousLine.equals(word.lineBounds)?'\n':' ');text.append(word.word);previousLine=word.lineBounds;}
        return text.toString();
    }

    private RectF drawSticky(Canvas canvas,RectF dest,float nx,float ny,String text,boolean minimized,int accent){return drawSticky(canvas,dest,nx,ny,text,minimized,accent,0xFFFFF3A6,13,1);}
    private RectF drawSticky(Canvas canvas,RectF dest,float nx,float ny,String text,boolean minimized,int accent,int paper,int fontSp,int boxSize){
        float density=getResources().getDisplayMetrics().density;
        float anchorX=dest.left+nx*dest.width(),anchorY=dest.top+ny*dest.height();
        if(minimized){float r=14*density;RectF box=new RectF(anchorX-r,anchorY-r,anchorX+r,anchorY+r);paint.setColor(accent);canvas.drawCircle(anchorX,anchorY,r,paint);paint.setColor(Color.WHITE);paint.setTextSize(17*density);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText("▣",anchorX,anchorY+6*density,paint);paint.setTextAlign(Paint.Align.LEFT);return box;}
        float[] boxW={150,220,300},boxH={64,96,170};float w=Math.min(boxW[boxSize]*density,dest.width()*(boxSize==2?0.7f:0.46f)),h=boxH[boxSize]*density;
        float left=Math.min(dest.right-w-6*density,anchorX+8*density);if(left<dest.left)left=dest.left+6*density;
        float top=Math.max(dest.top+6*density,Math.min(dest.bottom-h-6*density,anchorY));RectF box=new RectF(left,top,left+w,top+h);
        paint.setColor(paper);canvas.drawRoundRect(box,10*density,10*density,paint);paint.setColor(accent);canvas.drawCircle(anchorX,anchorY,6*density,paint);
        paint.setColor(0xFF3F3A2D);paint.setTextSize(fontSp*density);float lineHeight=fontSp*1.4f*density;float x=box.left+10*density,y=box.top+(fontSp+9)*density,max=box.width()-20*density;
        for(String paragraph:(text==null?"":text).split("\\n")){String line="";for(String word:paragraph.split(" ")){String candidate=line.isEmpty()?word:line+" "+word;if(paint.measureText(candidate)>max&&!line.isEmpty()){canvas.drawText(line,x,y,paint);y+=lineHeight;line=word;if(y>box.bottom-12*density)return box;}else line=candidate;}if(!line.isEmpty()){canvas.drawText(line,x,y,paint);y+=18*density;if(y>box.bottom-12*density)return box;}}
        return box;
    }
    private void drawMemo(Canvas canvas,RectF dest,AnnotationStore.Mark m){memoHitBoxes.put(m,drawSticky(canvas,dest,m.right,m.top,m.note,m.minimized,0xFFFFB300,m.paper,m.fontSp,m.boxSize));}
    private void drawTranslation(Canvas canvas,RectF dest,AnnotationStore.TranslationNote n){noteHitBoxes.put(n,drawSticky(canvas,dest,n.right,n.top,n.translated,n.minimized,0xFF7C3AED));}

    private float strokeWidth(float base,float pressure,RectF dest){float p=Math.max(0.12f,Math.min(1f,pressure));return Math.max(1.5f,base*dest.width()*(0.45f+p*1.15f));}

    private boolean isStylus(MotionEvent e){int tool=e.getToolType(0);return tool==MotionEvent.TOOL_TYPE_STYLUS||tool==MotionEvent.TOOL_TYPE_ERASER;}
    private TextRegion textRegionAt(float x,float y,RectF dest){
        if(!textSelectMode||dest.width()==0||!dest.contains(x,y))return null;
        float nx=(x-dest.left)/dest.width(),ny=(y-dest.top)/dest.height();
        float tolerance=16f*getResources().getDisplayMetrics().density;
        TextRegion best=null;float bestDistance=Float.MAX_VALUE;
        for(TextRegion r:textRegions){
            float dx=(nx-Math.max(r.wordBounds.left,Math.min(nx,r.wordBounds.right)))*dest.width();
            float dy=(ny-Math.max(r.wordBounds.top,Math.min(ny,r.wordBounds.bottom)))*dest.height();
            float distance=dx*dx+dy*dy;
            if(distance<bestDistance){bestDistance=distance;best=r;}
        }
        return bestDistance<=tolerance*tolerance?best:null;
    }
    private TextRegion nearestTextRegion(float x,float y,RectF dest){TextRegion hit=textRegionAt(x,y,dest);if(hit!=null)return hit;if(dest.width()==0||textRegions.isEmpty())return null;float nx=Math.max(0f,Math.min(1f,(x-dest.left)/dest.width())),ny=Math.max(0f,Math.min(1f,(y-dest.top)/dest.height())),bestDistance=Float.MAX_VALUE;TextRegion best=null;for(TextRegion r:textRegions){float dx=nx-Math.max(r.wordBounds.left,Math.min(nx,r.wordBounds.right)),dy=ny-Math.max(r.wordBounds.top,Math.min(ny,r.wordBounds.bottom));float distance=dx*dx+dy*dy*2f;if(distance<bestDistance){bestDistance=distance;best=r;}}return best;}
    private void beginTextSelectionNow(){selectionHandler.removeCallbacks(beginTextSelection);if(selectingText||selectionStartRegion==null)return;selectingText=true;selectionCandidate=false;bodySwipeCandidate=false;panning=false;selectedTextRegions.clear();selectedTextRegions.add(selectionStartRegion);getParent().requestDisallowInterceptTouchEvent(true);performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);invalidate();}
    private void updateTextSelection(TextRegion end){if(end==null||selectionStartRegion==null)return;selectionEndRegion=end;int a=textRegions.indexOf(selectionStartRegion),b=textRegions.indexOf(end);if(a<0||b<0)return;selectedTextRegions.clear();for(int i=Math.min(a,b);i<=Math.max(a,b);i++)selectedTextRegions.add(textRegions.get(i));invalidate();}
    private List<RectF> selectionLineBounds(){
        List<RectF> bounds=new ArrayList<>();RectF previousLine=null,current=null;
        for(TextRegion region:selectedTextRegions){
            if(previousLine!=null&&previousLine.equals(region.lineBounds)){current.union(region.wordBounds);}
            else{current=new RectF(region.wordBounds);bounds.add(current);previousLine=region.lineBounds;}
        }
        return bounds;
    }
    private TextSelection finishTextSelection(){if(selectedTextRegions.isEmpty())return null;StringBuilder text=new StringBuilder();List<RectF> bounds=selectionLineBounds();RectF union=new RectF(selectedTextRegions.get(0).wordBounds);for(TextRegion r:selectedTextRegions){if(text.length()>0)text.append(' ');text.append(r.word);union.union(r.wordBounds);}return new TextSelection(text.toString(),bounds,union,selectedTextRegions.size()==1);}
    private boolean temporaryEraser(MotionEvent e){return e.getToolType(0)==MotionEvent.TOOL_TYPE_ERASER||(e.getButtonState()&MotionEvent.BUTTON_STYLUS_PRIMARY)!=0;}
    private void finishInkStroke(){if(activeStroke!=null&&!activeStroke.points.isEmpty())listener.onInkChanged();activeStroke=null;stylusDrawing=false;getParent().requestDisallowInterceptTouchEvent(false);}
    private float inputPressure(MotionEvent e){return isStylus(e)?Math.max(0.05f,Math.min(1f,e.getPressure())):0.65f;}
    private void addInkPoint(MotionEvent e,RectF dest){if(activeStroke==null||!dest.contains(e.getX(),e.getY()))return;float x=(e.getX()-dest.left)/dest.width(),y=(e.getY()-dest.top)/dest.height();float pressure=inputPressure(e);if(activeStroke.points.isEmpty()){activeStroke.points.add(new AnnotationStore.InkPoint(x,y,pressure));return;}if(inkMode==3){AnnotationStore.InkPoint end=new AnnotationStore.InkPoint(x,y,pressure);if(activeStroke.points.size()==1)activeStroke.points.add(end);else activeStroke.points.set(1,end);return;}AnnotationStore.InkPoint last=activeStroke.points.get(activeStroke.points.size()-1);float dx=x-last.x,dy=y-last.y;if(dx*dx+dy*dy>0.000002f)activeStroke.points.add(new AnnotationStore.InkPoint(x,y,pressure));}
    private void eraseAt(MotionEvent e,RectF dest){if(strokes==null||dest.width()==0)return;float x=(e.getX()-dest.left)/dest.width(),y=(e.getY()-dest.top)/dest.height();float threshold=Math.max(0.012f,18f/dest.width());for(int i=strokes.size()-1;i>=0;i--){AnnotationStore.InkStroke s=strokes.get(i);if(s.page!=page)continue;for(AnnotationStore.InkPoint p:s.points)if(Math.hypot(p.x-x,p.y-y)<=threshold){strokes.remove(i);listener.onInkChanged();invalidate();return;}}}

    private float touchSlop(){return Math.max(14*getResources().getDisplayMetrics().density,android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop()*1.5f);}
    private float swipeDistance(){return Math.max(48*getResources().getDisplayMetrics().density,Math.min((verticalPageSwipe?getHeight():getWidth())*.1f,100*getResources().getDisplayMetrics().density));}

    @Override public boolean onTouchEvent(MotionEvent e) {
        scaleDetector.onTouchEvent(e);
        if (scaleDetector.isInProgress()) return true;
        RectF dest = contentRect();
        boolean stylus=isStylus(e);
        if(handleElementGesture(e,dest))return true;
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN)scalingOccurred=false;
        if(e.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN){finishInkStroke();clearLassoSelection();}
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
            listener.onSelectionAdjustStarted();
            float edge=72*getResources().getDisplayMetrics().density;
            boolean eligible=pageSwipeEnabled&&!stylus&&!directTextSelection&&!lassoMode&&!highlightMode&&!memoMode&&!outlineMode&&!(inkMode!=0&&fingerInk);
            bodySwipeCandidate=eligible&&scale<=1f;edgeStartTime=e.getEventTime();
            edgeSwipe=eligible&&scale>1f&&
                (verticalPageSwipe?(e.getY()<edge||e.getY()>getHeight()-edge):(e.getX()<edge||e.getX()>getWidth()-edge));
            if(edgeSwipe){startX=e.getX();startY=e.getY();edgeStartTime=e.getEventTime();selectionHandler.removeCallbacks(beginTextSelection);getParent().requestDisallowInterceptTouchEvent(true);return true;}
        }
        if(e.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN){edgeSwipe=bodySwipeCandidate=false;if(dragging){dragging=false;if(dragTracker!=null){dragTracker.recycle();dragTracker=null;}pageDrag.end(-1e6f);}}
        if(edgeSwipe){
            if(e.getActionMasked()==MotionEvent.ACTION_UP){float along=verticalPageSwipe?e.getY()-startY:e.getX()-startX,cross=verticalPageSwipe?e.getX()-startX:e.getY()-startY;
                edgeSwipe=false;getParent().requestDisallowInterceptTouchEvent(false);
                float threshold=swipeDistance();
                if(Math.abs(along)>=threshold&&Math.abs(along)>Math.abs(cross)*1.5f&&e.getEventTime()-edgeStartTime<=1200)listener.onPageSwipe(along<0?1:-1);
            }else if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){edgeSwipe=false;getParent().requestDisallowInterceptTouchEvent(false);}return true;
        }
        if(lassoMode){
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){listener.onSelectionAdjustStarted();clearLassoSelection();clearTextSelectionOverlay();if(dest.contains(e.getX(),e.getY())){lassoDrawing=true;getParent().requestDisallowInterceptTouchEvent(true);lassoAnchor=normalizedPoint(e.getX(),e.getY(),dest);if(lassoShape==LASSO_FREE)addLassoPoint(e.getX(),e.getY(),dest);}invalidate();return true;}
            if(action==MotionEvent.ACTION_CANCEL){clearLassoSelection();return true;}
            if(e.getPointerCount()!=1||scalingOccurred)return true;
            if(action==MotionEvent.ACTION_MOVE&&lassoDrawing){if(lassoShape==LASSO_FREE){for(int i=0;i<e.getHistorySize();i++)addLassoPoint(e.getHistoricalX(i),e.getHistoricalY(i),dest);addLassoPoint(e.getX(),e.getY(),dest);}else updateLassoShape(e.getX(),e.getY(),dest);invalidate();return true;}
            if(action==MotionEvent.ACTION_UP&&lassoDrawing){if(lassoShape==LASSO_FREE)addLassoPoint(e.getX(),e.getY(),dest);else updateLassoShape(e.getX(),e.getY(),dest);lassoDrawing=false;getParent().requestDisallowInterceptTouchEvent(false);if(validLasso(dest))listener.onLassoSelectionFinished();else clearLassoSelection();invalidate();return true;}
            return true;
        }
        if(inkMode!=0&&(stylus||fingerInk)&&e.getPointerCount()==1&&!scalingOccurred){int action=e.getActionMasked();boolean erase=inkMode==2||temporaryEraser(e);if(action==MotionEvent.ACTION_DOWN){getParent().requestDisallowInterceptTouchEvent(true);stylusDrawing=true;if(erase)eraseAt(e,dest);else if(dest.contains(e.getX(),e.getY())){activeStroke=new AnnotationStore.InkStroke();activeStroke.page=page;activeStroke.color=inkColor;activeStroke.width=inkWidth;addInkPoint(e,dest);if(strokes!=null)strokes.add(activeStroke);}invalidate();return true;}if(action==MotionEvent.ACTION_MOVE&&stylusDrawing){if(erase)eraseAt(e,dest);else{for(int i=0;inkMode!=3&&i<e.getHistorySize();i++){if(activeStroke!=null&&dest.contains(e.getHistoricalX(i),e.getHistoricalY(i))){float x=(e.getHistoricalX(i)-dest.left)/dest.width(),y=(e.getHistoricalY(i)-dest.top)/dest.height(),p=stylus?Math.max(0.05f,Math.min(1f,e.getHistoricalPressure(i))):0.65f;activeStroke.points.add(new AnnotationStore.InkPoint(x,y,p));}}addInkPoint(e,dest);}invalidate();return true;}if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)&&stylusDrawing){if(!erase&&activeStroke!=null){if(action==MotionEvent.ACTION_CANCEL){if(strokes!=null)strokes.remove(activeStroke);}else{addInkPoint(e,dest);if(!activeStroke.points.isEmpty())listener.onInkChanged();}}activeStroke=null;stylusDrawing=false;getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;}}
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            listener.onSelectionAdjustStarted();
            selectionHandler.removeCallbacks(beginTextSelection);
            startX = currentX = lastX = e.getX();
            startY = currentY = lastY = e.getY();
            gestureMoved = false;
            if(!selectedTextRegions.isEmpty()){
                TextRegion first=selectedTextRegions.get(0),last=selectedTextRegions.get(selectedTextRegions.size()-1);
                float radius=22f*getResources().getDisplayMetrics().density;
                float leftDistance=(float)Math.hypot(e.getX()-(dest.left+first.wordBounds.left*dest.width()),e.getY()-(dest.top+first.wordBounds.bottom*dest.height()));
                float rightDistance=(float)Math.hypot(e.getX()-(dest.left+last.wordBounds.right*dest.width()),e.getY()-(dest.top+last.wordBounds.bottom*dest.height()));
                // A touch on a word starts a new selection, even near an old handle.
                boolean onWord=false;
                for(TextRegion region:textRegions){
                    RectF bounds=region.wordBounds;
                    if(new RectF(dest.left+bounds.left*dest.width(),dest.top+bounds.top*dest.height(),dest.left+bounds.right*dest.width(),dest.top+bounds.bottom*dest.height()).contains(e.getX(),e.getY())){onWord=true;break;}
                }
                boolean left=leftDistance<radius&&leftDistance<=rightDistance;
                boolean right=rightDistance<radius&&!left;
                if(!onWord&&(left||right)){selectionStartRegion=left?last:first;selectionEndRegion=left?first:last;selectingText=true;selectionCandidate=false;bodySwipeCandidate=false;panning=false;drawing=false;scalingOccurred=false;getParent().requestDisallowInterceptTouchEvent(true);return true;}
                clearTextSelectionOverlay();
            }
            startX = currentX = e.getX(); startY = currentY = e.getY();
            lastX = startX; lastY = startY;
            gestureMoved = false; scalingOccurred = false;bodySwipeCandidate=pageSwipeEnabled&&!stylus&&!directTextSelection&&!lassoMode&&!highlightMode&&!memoMode&&!outlineMode&&!(inkMode!=0&&fingerInk)&&scale<=1f;
            drawing = highlightMode && dest.contains(startX, startY);
            selectionStartRegion=(!drawing&&!memoMode&&!outlineMode&&inkMode==0)?textRegionAt(startX,startY,dest):null;selectionEndRegion=selectionStartRegion;selectionCandidate=selectionStartRegion!=null;selectingText=false;if(selectionCandidate)selectionHandler.postDelayed(beginTextSelection,420);
            panning = scale > 1f && !outlineMode && !selectionCandidate;
            getParent().requestDisallowInterceptTouchEvent(drawing || panning || selectionCandidate);
            invalidate(); return true;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            selectionHandler.removeCallbacks(beginTextSelection);selectionCandidate=selectingText=false;selectedTextRegions.clear();
            drawing = false; panning = false; scalingOccurred = true;
            return true;
        }
        if(e.getAction()==MotionEvent.ACTION_MOVE&&selectionCandidate&&Math.hypot(e.getX()-startX,e.getY()-startY)>touchSlop()){
            if(directTextSelection||stylus){beginTextSelectionNow();updateTextSelection(nearestTextRegion(e.getX(),e.getY(),dest));return true;}
            selectionHandler.removeCallbacks(beginTextSelection);selectionCandidate=false;panning=scale>1f;
        }
        if(dragging&&e.getActionMasked()==MotionEvent.ACTION_MOVE){dragTracker.addMovement(e);float ddx=e.getX()-startX;pageDrag.move(Math.max(0f,dragDirection>0?-ddx:ddx));return true;}
        if(bodySwipeCandidate&&!selectingText&&e.getActionMasked()==MotionEvent.ACTION_MOVE){if(Math.hypot(e.getX()-startX,e.getY()-startY)>touchSlop()){gestureMoved=true;selectionHandler.removeCallbacks(beginTextSelection);selectionCandidate=false;
            if(pageDrag!=null&&!verticalPageSwipe){float dx=e.getX()-startX,dy=e.getY()-startY;
                if(!dragging&&Math.abs(dx)>Math.abs(dy)*1.5f){dragDirection=dx<0?1:-1;dragging=pageDrag.start(dragDirection);if(dragging){dragTracker=android.view.VelocityTracker.obtain();getParent().requestDisallowInterceptTouchEvent(true);}}
                if(dragging){dragTracker.addMovement(e);pageDrag.move(Math.max(0f,dragDirection>0?-dx:dx));}
            }}return true;}
        if(dragging&&(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)){dragging=false;bodySwipeCandidate=false;getParent().requestDisallowInterceptTouchEvent(false);dragTracker.addMovement(e);dragTracker.computeCurrentVelocity(1000);float v=dragTracker.getXVelocity()*(dragDirection>0?-1:1);dragTracker.recycle();dragTracker=null;pageDrag.end(e.getActionMasked()==MotionEvent.ACTION_CANCEL?-1e6f:v);return true;}
        if(bodySwipeCandidate&&e.getActionMasked()==MotionEvent.ACTION_UP){bodySwipeCandidate=false;if(!selectingText&&gestureMoved){selectionHandler.removeCallbacks(beginTextSelection);selectionCandidate=false;getParent().requestDisallowInterceptTouchEvent(false);float along=verticalPageSwipe?e.getY()-startY:e.getX()-startX,cross=verticalPageSwipe?e.getX()-startX:e.getY()-startY;if(Math.abs(along)>=swipeDistance()&&Math.abs(along)>Math.abs(cross)*1.5f&&e.getEventTime()-edgeStartTime<=1200)listener.onPageSwipe(along<0?1:-1);return true;}}
        if(e.getAction()==MotionEvent.ACTION_MOVE&&selectingText){currentX=e.getX();currentY=e.getY();updateTextSelection(nearestTextRegion(currentX,currentY,dest));return true;}
        if (e.getActionMasked() == MotionEvent.ACTION_POINTER_UP && scale > 1f) {
            int remaining = e.getActionIndex() == 0 ? 1 : 0;
            if (remaining < e.getPointerCount()) {
                lastX = e.getX(remaining);
                lastY = e.getY(remaining);
                startX = lastX;
                startY = lastY;
                panning = true;
                gestureMoved = false;
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_MOVE && drawing) {
            currentX = e.getX(); currentY = e.getY(); invalidate(); return true;
        }
        if (e.getAction() == MotionEvent.ACTION_MOVE && panning && e.getPointerCount() == 1) {
            if(!gestureMoved&&Math.hypot(e.getX()-startX,e.getY()-startY)<=touchSlop())return true;
            float dx = e.getX() - lastX;
            float dy = e.getY() - lastY;
            panX += dx*.8f; panY += dy*.8f;
            lastX = e.getX(); lastY = e.getY();
            if (Math.hypot(e.getX() - startX, e.getY() - startY) > touchSlop()) gestureMoved = true;
            clampPan(); invalidate(); return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            selectionHandler.removeCallbacks(beginTextSelection);
            getParent().requestDisallowInterceptTouchEvent(false);
            if(selectingText){updateTextSelection(nearestTextRegion(e.getX(),e.getY(),dest));TextSelection selection=finishTextSelection();selectingText=selectionCandidate=false;if(selection!=null){listener.onTextSelectionFinished(selection,e.getX(),e.getY());return true;}}
            if(selectionCandidate&&selectionStartRegion!=null&&(directTextSelection||stylus)){
                selectedTextRegions.clear();selectedTextRegions.add(selectionStartRegion);
                TextSelection selection=finishTextSelection();selectionCandidate=false;
                listener.onTextSelectionFinished(selection,e.getX(),e.getY());invalidate();return true;
            }
            selectionCandidate=false;
            if (panning && gestureMoved) {
                panning = false; return true;
            }
            if (scalingOccurred) {
                panning = false; return true;
            }
            if(Math.hypot(e.getX()-startX,e.getY()-startY)<20){for(AnnotationStore.TranslationNote n:noteHitBoxes.keySet()){RectF b=noteHitBoxes.get(n);if(b!=null&&b.contains(e.getX(),e.getY())){listener.onTranslationTapped(n);return true;}}for(AnnotationStore.Mark m:memoHitBoxes.keySet()){RectF b=memoHitBoxes.get(m);if(b!=null&&b.contains(e.getX(),e.getY())){listener.onMarkTapped(m);return true;}}}
            if (drawing) {
                currentX = Math.max(dest.left, Math.min(dest.right, e.getX()));
                currentY = Math.max(dest.top, Math.min(dest.bottom, e.getY()));
                if (Math.abs(currentX - startX) > 12) {
                    AnnotationStore.Mark m = new AnnotationStore.Mark();
                    m.page = page;
                    m.left = (Math.min(startX, currentX) - dest.left) / dest.width();
                    m.right = (Math.max(startX, currentX) - dest.left) / dest.width();
                    float centerY = (startY + currentY) / 2f;
                    float half = highlightHeight(dest) / 2f;
                    m.top = (Math.max(dest.top, centerY - half) - dest.top) / dest.height();
                    m.bottom = (Math.min(dest.bottom, centerY + half) - dest.top) / dest.height();
                    m.color = highlightColor;
                    listener.onHighlightCreated(m);
                }
                drawing = false; invalidate(); return true;
            }
            if (Math.hypot(e.getX() - startX, e.getY() - startY) < 20 && memoMode && dest.contains(e.getX(), e.getY())) {
                listener.onMemoPointRequested(page, (e.getX() - dest.left) / dest.width(),
                        (e.getY() - dest.top) / dest.height());
                return true;
            }
            if (Math.hypot(e.getX() - startX, e.getY() - startY) < 20 && outlineMode && dest.contains(e.getX(), e.getY())) {
                listener.onOutlinePointRequested(page, (e.getX() - dest.left) / dest.width(),
                        (e.getY() - dest.top) / dest.height());
                return true;
            }
            if (Math.hypot(e.getX() - startX, e.getY() - startY) < 20 && marks != null) {
                float nx = (e.getX() - dest.left) / dest.width();
                float ny = (e.getY() - dest.top) / dest.height();
                if(annotationStore!=null)for(int i=annotationStore.elements.size()-1;i>=0;i--){AnnotationStore.PageElement element=annotationStore.elements.get(i);if(element.page==page&&nx>=element.left&&nx<=element.right&&ny>=element.top&&ny<=element.bottom){if(selectable(element)){if(element==selectedElement)listener.onElementTapped(element);else{selectedElement=element;invalidate();}}else listener.onElementTapped(element);return true;}}
                for (int i = marks.size() - 1; i >= 0; i--) {
                    AnnotationStore.Mark m = marks.get(i);
                    if (m.page == page && nx >= m.left && nx <= m.right && ny >= m.top && ny <= m.bottom) {
                        listener.onMarkTapped(m); return true;
                    }
                }
            }

        }
        if(e.getAction()==MotionEvent.ACTION_CANCEL){edgeSwipe=bodySwipeCandidate=false;clearTextSelectionOverlay();}
        return true;
    }
}

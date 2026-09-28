package com.hdlee.pdfnote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.List;

final class PdfPageView extends View {
    interface Listener {
        void onHighlightCreated(AnnotationStore.Mark mark);
        void onMarkTapped(AnnotationStore.Mark mark);
        void onMemoPointRequested(int page, float x, float y);
        void onZoomGestureStarted();
        void onPageSwipe(int direction);
        void onOutlinePointRequested(int page, float x, float y);
        void onInkChanged();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private Bitmap bitmap;
    private List<AnnotationStore.Mark> marks;
    private List<AnnotationStore.InkStroke> strokes;
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
    private int inkColor=0xFF172033;
    private float inkWidth=0.004f;
    private AnnotationStore.InkStroke activeStroke;
    private boolean stylusDrawing;
    private float scale = 1f;
    private final Listener listener;

    PdfPageView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setBackgroundColor(0xFFDDDDDD);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
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
                panX = focusX - nx * size[0] - (getWidth() - size[0]) / 2f;
                panY = focusY - ny * size[1] - (getHeight() - size[1]) / 2f;
                clampPan();
                invalidate();
                return true;
            }

            @Override public void onScaleEnd(ScaleGestureDetector detector) {
                clampPan();
            }
        });
    }

    void showPage(Bitmap pageBitmap, int pageNumber, List<AnnotationStore.Mark> allMarks, List<AnnotationStore.InkStroke> allStrokes) {
        if (bitmap != null && bitmap != pageBitmap) bitmap.recycle();
        bitmap = pageBitmap;
        page = pageNumber;
        marks = allMarks;
        strokes = allStrokes;
        scale = 1f;
        panX = panY = 0f;
        invalidate();
    }

    void clearPage() {
        if (bitmap != null) bitmap.recycle();
        bitmap = null;
        marks = null;
        strokes = null;
        scale = 1f;
        panX = panY = 0f;
        invalidate();
    }

    void setHighlightMode(boolean enabled, int color) {
        highlightMode = enabled;
        if (enabled) { memoMode = false; outlineMode = false; }
        highlightColor = color;
        invalidate();
    }

    void setMemoMode(boolean enabled) {
        memoMode = enabled;
        if (enabled) { highlightMode = false; outlineMode = false; }
        invalidate();
    }

    void setOutlineMode(boolean enabled) {
        outlineMode = enabled;
        if (enabled) { highlightMode = false; memoMode = false; }
        invalidate();
    }

    void setInkTool(int mode, int color, float width) {
        inkMode=mode; inkColor=color; inkWidth=width;
        if(mode!=0){highlightMode=memoMode=outlineMode=false;}
        invalidate();
    }

    void focusOnPoint(float x, float y) {
        if (bitmap == null) return;
        scale = Math.max(scale, 1.7f);
        float[] size = contentSize();
        panX = getWidth() / 2f - ((getWidth() - size[0]) / 2f + x * size[0]);
        panY = getHeight() / 2f - ((getHeight() - size[1]) / 2f + y * size[1]);
        clampPan();
        invalidate();
    }

    void setVerticalPageSwipe(boolean vertical) {
        verticalPageSwipe = vertical;
    }

    private float highlightHeight(RectF dest) {
        return Math.max(12f, dest.height() * 0.022f);
    }

    private float[] contentSize() {
        if (bitmap == null) return new float[]{0f, 0f};
        float base = Math.min((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
        float w = bitmap.getWidth() * base * scale;
        float h = bitmap.getHeight() * base * scale;
        return new float[]{w, h};
    }

    private void clampPan() {
        if (bitmap == null) return;
        float[] size = contentSize();
        float maxX = Math.max(0f, (size[0] - getWidth()) / 2f);
        float maxY = Math.max(0f, (size[1] - getHeight()) / 2f);
        panX = Math.max(-maxX, Math.min(maxX, panX));
        panY = Math.max(-maxY, Math.min(maxY, panY));
        if (scale <= 1f) panX = panY = 0f;
    }

    private RectF contentRect() {
        if (bitmap == null) return new RectF();
        float[] size = contentSize();
        float left = (getWidth() - size[0]) / 2f + panX;
        float top = (getHeight() - size[1]) / 2f + panY;
        return new RectF(left, top, left + size[0], top + size[1]);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) return;
        RectF dest = contentRect();
        paint.setColor(Color.WHITE);
        canvas.drawRect(dest, paint);
        canvas.drawBitmap(bitmap, null, dest, paint);
        if (marks != null) for (AnnotationStore.Mark m : marks) if (m.page == page) {
            if (!m.noteOnly) {
                paint.setColor(m.color);
                canvas.drawRect(dest.left + m.left * dest.width(), dest.top + m.top * dest.height(),
                        dest.left + m.right * dest.width(), dest.top + m.bottom * dest.height(), paint);
            }
            if (m.noteOnly || (m.note != null && !m.note.isEmpty())) {
                paint.setColor(0xFF1565C0);
                float cx = dest.left + m.right * dest.width();
                float cy = dest.top + m.top * dest.height();
                canvas.drawCircle(cx, cy, 12f, paint);
                paint.setColor(Color.WHITE); paint.setStrokeWidth(2f);
                canvas.drawLine(cx - 5f, cy - 3f, cx + 5f, cy - 3f, paint);
                canvas.drawLine(cx - 5f, cy + 2f, cx + 2f, cy + 2f, paint);
            }
        }
        if(strokes!=null){paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);for(AnnotationStore.InkStroke s:strokes)if(s.page==page&&s.points.size()>0){paint.setColor(s.color);if(s.points.size()==1){AnnotationStore.InkPoint p=s.points.get(0);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(dest.left+p.x*dest.width(),dest.top+p.y*dest.height(),strokeWidth(s.width,p.pressure,dest)/2f,paint);paint.setStyle(Paint.Style.STROKE);}else for(int i=1;i<s.points.size();i++){AnnotationStore.InkPoint a=s.points.get(i-1),b=s.points.get(i);paint.setStrokeWidth(strokeWidth(s.width,(a.pressure+b.pressure)/2f,dest));canvas.drawLine(dest.left+a.x*dest.width(),dest.top+a.y*dest.height(),dest.left+b.x*dest.width(),dest.top+b.y*dest.height(),paint);}}paint.setStyle(Paint.Style.FILL);}
        if (drawing) {
            paint.setColor(highlightColor);
            float centerY = (startY + currentY) / 2f;
            float half = highlightHeight(dest) / 2f;
            canvas.drawRect(Math.min(startX, currentX), centerY - half,
                    Math.max(startX, currentX), centerY + half, paint);
        }
    }

    private float strokeWidth(float base,float pressure,RectF dest){float p=Math.max(0.12f,Math.min(1f,pressure));return Math.max(1.5f,base*dest.width()*(0.45f+p*1.15f));}

    private boolean isStylus(MotionEvent e){int tool=e.getToolType(0);return tool==MotionEvent.TOOL_TYPE_STYLUS||tool==MotionEvent.TOOL_TYPE_ERASER;}
    private boolean temporaryEraser(MotionEvent e){return e.getToolType(0)==MotionEvent.TOOL_TYPE_ERASER||(e.getButtonState()&MotionEvent.BUTTON_STYLUS_PRIMARY)!=0;}
    private void addInkPoint(MotionEvent e,RectF dest){if(activeStroke==null||!dest.contains(e.getX(),e.getY()))return;float x=(e.getX()-dest.left)/dest.width(),y=(e.getY()-dest.top)/dest.height();float pressure=Math.max(0.05f,Math.min(1f,e.getPressure()));if(activeStroke.points.isEmpty()){activeStroke.points.add(new AnnotationStore.InkPoint(x,y,pressure));return;}AnnotationStore.InkPoint last=activeStroke.points.get(activeStroke.points.size()-1);float dx=x-last.x,dy=y-last.y;if(dx*dx+dy*dy>0.000002f)activeStroke.points.add(new AnnotationStore.InkPoint(x,y,pressure));}
    private void eraseAt(MotionEvent e,RectF dest){if(strokes==null||dest.width()==0)return;float x=(e.getX()-dest.left)/dest.width(),y=(e.getY()-dest.top)/dest.height();float threshold=Math.max(0.012f,18f/dest.width());for(int i=strokes.size()-1;i>=0;i--){AnnotationStore.InkStroke s=strokes.get(i);if(s.page!=page)continue;for(AnnotationStore.InkPoint p:s.points)if(Math.hypot(p.x-x,p.y-y)<=threshold){strokes.remove(i);listener.onInkChanged();invalidate();return;}}}

    @Override public boolean onTouchEvent(MotionEvent e) {
        scaleDetector.onTouchEvent(e);
        if (scaleDetector.isInProgress()) return true;
        RectF dest = contentRect();
        boolean stylus=isStylus(e);
        if(inkMode!=0&&stylus){int action=e.getActionMasked();boolean erase=inkMode==2||temporaryEraser(e);if(action==MotionEvent.ACTION_DOWN){getParent().requestDisallowInterceptTouchEvent(true);stylusDrawing=true;if(erase)eraseAt(e,dest);else if(dest.contains(e.getX(),e.getY())){activeStroke=new AnnotationStore.InkStroke();activeStroke.page=page;activeStroke.color=inkColor;activeStroke.width=inkWidth;addInkPoint(e,dest);if(strokes!=null)strokes.add(activeStroke);}invalidate();return true;}if(action==MotionEvent.ACTION_MOVE&&stylusDrawing){if(erase)eraseAt(e,dest);else{for(int i=0;i<e.getHistorySize();i++){if(activeStroke!=null&&dest.contains(e.getHistoricalX(i),e.getHistoricalY(i))){float x=(e.getHistoricalX(i)-dest.left)/dest.width(),y=(e.getHistoricalY(i)-dest.top)/dest.height(),p=Math.max(0.05f,Math.min(1f,e.getHistoricalPressure(i)));activeStroke.points.add(new AnnotationStore.InkPoint(x,y,p));}}addInkPoint(e,dest);}invalidate();return true;}if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)&&stylusDrawing){if(!erase&&activeStroke!=null&&!activeStroke.points.isEmpty())listener.onInkChanged();activeStroke=null;stylusDrawing=false;getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;}}
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            startX = currentX = e.getX(); startY = currentY = e.getY();
            lastX = startX; lastY = startY;
            gestureMoved = false; scalingOccurred = false;
            drawing = highlightMode && dest.contains(startX, startY);
            panning = scale > 1f && !outlineMode;
            getParent().requestDisallowInterceptTouchEvent(drawing || panning);
            invalidate(); return true;
        }
        if (e.getAction() == MotionEvent.ACTION_POINTER_DOWN) {
            drawing = false; panning = false; scalingOccurred = true;
            return true;
        }
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
            float dx = e.getX() - lastX;
            float dy = e.getY() - lastY;
            panX += dx; panY += dy;
            lastX = e.getX(); lastY = e.getY();
            if (Math.hypot(e.getX() - startX, e.getY() - startY) > 8) gestureMoved = true;
            clampPan(); invalidate(); return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            getParent().requestDisallowInterceptTouchEvent(false);
            if (panning && gestureMoved) {
                panning = false; return true;
            }
            if (scalingOccurred) {
                panning = false; return true;
            }
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
                for (int i = marks.size() - 1; i >= 0; i--) {
                    AnnotationStore.Mark m = marks.get(i);
                    if (m.page == page && nx >= m.left && nx <= m.right && ny >= m.top && ny <= m.bottom) {
                        listener.onMarkTapped(m); return true;
                    }
                }
            }
            if (!highlightMode && !memoMode && !outlineMode && scale <= 1f) {
                float dx = e.getX() - startX;
                float dy = e.getY() - startY;
                float distance = verticalPageSwipe ? Math.abs(dy) : Math.abs(dx);
                float cross = verticalPageSwipe ? Math.abs(dx) : Math.abs(dy);
                if (distance > Math.max(72f, cross * 1.25f)) {
                    int direction = verticalPageSwipe ? (dy < 0 ? 1 : -1) : (dx < 0 ? 1 : -1);
                    listener.onPageSwipe(direction);
                    return true;
                }
            }
        }
        return true;
    }
}

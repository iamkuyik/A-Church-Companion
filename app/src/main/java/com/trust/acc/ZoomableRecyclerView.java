package com.trust.acc;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

/**
 * RecyclerView supporting smooth pinch-to-zoom (1.0x - 3.5x), double-tap zoom,
 * and 2D horizontal/vertical panning for reading musical score PDFs.
 */
public class ZoomableRecyclerView extends RecyclerView {

    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 3.5f;
    private static final float DOUBLE_TAP_SCALE = 2.2f;

    private float scaleFactor = 1.0f;
    private float translateX = 0f;
    private float translateY = 0f;

    private float lastTouchX;
    private float lastTouchY;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private boolean isScaling = false;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    public ZoomableRecyclerView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public ZoomableRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomableRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                isScaling = true;
                return true;
            }

            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                float prevScale = scaleFactor;
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(MIN_SCALE, Math.min(scaleFactor, MAX_SCALE));

                if (prevScale != scaleFactor) {
                    float focusX = detector.getFocusX();
                    float focusY = detector.getFocusY();
                    float scaleChange = scaleFactor / prevScale;
                    translateX = focusX - (focusX - translateX) * scaleChange;
                    translateY = focusY - (focusY - translateY) * scaleChange;
                    clampTranslations();
                    invalidate();
                }
                return true;
            }

            @Override
            public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                isScaling = false;
                if (scaleFactor <= 1.02f) {
                    resetZoom();
                }
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (scaleFactor > 1.05f) {
                    resetZoom();
                } else {
                    float targetScale = DOUBLE_TAP_SCALE;
                    float focusX = e.getX();
                    float focusY = e.getY();
                    float scaleChange = targetScale / scaleFactor;
                    scaleFactor = targetScale;
                    translateX = focusX - (focusX - translateX) * scaleChange;
                    translateY = focusY - (focusY - translateY) * scaleChange;
                    clampTranslations();
                    invalidate();
                }
                return true;
            }
        });
    }

    public void resetZoom() {
        scaleFactor = 1.0f;
        translateX = 0f;
        translateY = 0f;
        invalidate();
    }

    public boolean isZoomed() {
        return scaleFactor > 1.02f;
    }

    private void clampTranslations() {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0 || scaleFactor <= 1.0f) {
            translateX = 0f;
            translateY = 0f;
            return;
        }
        float minX = -width * (scaleFactor - 1.0f);
        float minY = -height * (scaleFactor - 1.0f);
        translateX = Math.max(minX, Math.min(0f, translateX));
        translateY = Math.max(minY, Math.min(0f, translateY));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        clampTranslations();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean wasScaling = isScaling;
        gestureDetector.onTouchEvent(ev);
        scaleDetector.onTouchEvent(ev);

        if (!wasScaling && isScaling) {
            // Cancel any in-flight RecyclerView scroll when pinch-zoom begins
            MotionEvent cancel = MotionEvent.obtain(ev);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.onTouchEvent(cancel);
            cancel.recycle();
            return true;
        }

        final int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                final int pointerIndex = ev.getActionIndex();
                lastTouchX = ev.getX(pointerIndex);
                lastTouchY = ev.getY(pointerIndex);
                activePointerId = ev.getPointerId(pointerIndex);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (isScaling) {
                    return true;
                }
                if (scaleFactor > 1.0f) {
                    final int pointerIndex = ev.findPointerIndex(activePointerId);
                    if (pointerIndex != -1) {
                        final float x = ev.getX(pointerIndex);
                        final float y = ev.getY(pointerIndex);
                        final float dx = x - lastTouchX;
                        final float dy = y - lastTouchY;

                        translateX += dx;
                        // Use explicit non-zero scroll direction (-1 up, +1 down) to avoid (int) truncation to 0
                        int scrollDir = dy > 0 ? -1 : (dy < 0 ? 1 : 0);
                        if (scrollDir != 0 && !canScrollVertically(scrollDir)) {
                            translateY += dy;
                        }
                        clampTranslations();
                        invalidate();

                        lastTouchX = x;
                        lastTouchY = y;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                isScaling = false;
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                final int pointerIndex = ev.getActionIndex();
                final int pointerId = ev.getPointerId(pointerIndex);
                if (pointerId == activePointerId) {
                    final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                    if (newPointerIndex < ev.getPointerCount()) {
                        lastTouchX = ev.getX(newPointerIndex);
                        lastTouchY = ev.getY(newPointerIndex);
                        activePointerId = ev.getPointerId(newPointerIndex);
                    }
                }
                break;
            }
        }

        if (isScaling) {
            return true;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        canvas.save();
        canvas.translate(translateX, translateY);
        canvas.scale(scaleFactor, scaleFactor);
        super.dispatchDraw(canvas);
        canvas.restore();
    }
}

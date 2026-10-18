package com.marciogames.betacraft.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;

/**
 * Reusable transparent on-screen controls for an Android-hosted legacy Minecraft view.
 * Multiple pointers may hold movement/jump and mouse buttons at the same time.
 * This view does not render Minecraft; the host must provide a live LWJGL CallbackSink.
 */
final class AndroidTouchControls extends View {
    private static final int KEY_W = 87, KEY_A = 65, KEY_S = 83, KEY_D = 68;
    private static final int KEY_SPACE = 32, KEY_E = 69, KEY_ESCAPE = 256;
    private static final int CONTROL_MOUSE_LEFT = -1;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    // Maps pointer IDs to a GLFW key or CONTROL_MOUSE_LEFT while each finger is held.
    private final SparseIntArray pointerControls = new SparseIntArray();
    private final SparseIntArray heldControlCounts = new SparseIntArray();
    private AndroidLwjglInputAdapter.CallbackSink sink;

    AndroidTouchControls(Context context) { super(context); init(); }
    AndroidTouchControls(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    void setCallbackSink(AndroidLwjglInputAdapter.CallbackSink value) {
        releaseAll();
        sink = value;
        setVisibility(value == null ? View.GONE : View.VISIBLE);
        invalidate();
    }

    private void init() {
        setClickable(true);
        setFocusable(false);
        setVisibility(View.GONE);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (sink == null) return;
        float w = getWidth(), h = getHeight();
        float r = Math.min(w, h) * 0.055f;
        drawButton(canvas, w * 0.12f, h * 0.78f, r, "W");
        drawButton(canvas, w * 0.055f, h * 0.88f, r, "A");
        drawButton(canvas, w * 0.12f, h * 0.88f, r, "S");
        drawButton(canvas, w * 0.185f, h * 0.88f, r, "D");
        drawButton(canvas, w * 0.86f, h * 0.86f, r * 1.2f, "JUMP");
        drawButton(canvas, w * 0.72f, h * 0.76f, r * 0.85f, "HIT");
        drawButton(canvas, w * 0.92f, h * 0.68f, r * 0.7f, "E");
        drawButton(canvas, w * 0.08f, h * 0.68f, r * 0.7f, "ESC");
    }

    private void drawButton(Canvas canvas, float cx, float cy, float radius, String label) {
        paint.setColor(0x66303948);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(cx - radius, cy - radius, cx + radius, cy + radius),
                radius * 0.25f, radius * 0.25f, paint);
        paint.setColor(0xDDEEF2F7);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Math.max(11f, radius * (label.length() > 2 ? 0.42f : 0.8f)));
        canvas.drawText(label, cx, cy + paint.getTextSize() * 0.34f, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (sink == null) return false;
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int pointerId = event.getPointerId(index);
            float x = event.getX(index), y = event.getY(index);
            int key = hitKey(x, y);
            boolean mouseHit = hit(x, y, 0.72f, 0.76f, 0.10f);
            if (key == 0 && !mouseHit) {
                // Let the underlying game surface receive look/aim gestures.
                return pointerControls.size() > 0;
            }
            if (pointerControls.indexOfKey(pointerId) >= 0) return true;
            int control = key != 0 ? key : CONTROL_MOUSE_LEFT;
            pointerControls.put(pointerId, control);
            int count = heldControlCounts.get(control, 0);
            heldControlCounts.put(control, count + 1);
            if (count == 0) {
                if (control == CONTROL_MOUSE_LEFT) sink.mouseButton(0, true);
                else sink.key(control, 0, true);
            }
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            releasePointer(event.getPointerId(index));
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            releaseAll();
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            // Fingers already assigned to a control remain held until lifted.
            // Unassigned fingers can still be used for the underlying look surface.
            return pointerControls.size() > 0;
        }
        return pointerControls.size() > 0;
    }

    private int hitKey(float x, float y) {
        if (hit(x, y, 0.12f, 0.78f, 0.065f)) return KEY_W;
        if (hit(x, y, 0.055f, 0.88f, 0.065f)) return KEY_A;
        if (hit(x, y, 0.12f, 0.88f, 0.065f)) return KEY_S;
        if (hit(x, y, 0.185f, 0.88f, 0.065f)) return KEY_D;
        if (hit(x, y, 0.86f, 0.86f, 0.08f)) return KEY_SPACE;
        if (hit(x, y, 0.92f, 0.68f, 0.055f)) return KEY_E;
        if (hit(x, y, 0.08f, 0.68f, 0.055f)) return KEY_ESCAPE;
        return 0;
    }

    private boolean hit(float x, float y, float nx, float ny, float radius) {
        float dx = x / Math.max(1f, getWidth()) - nx;
        float dy = y / Math.max(1f, getHeight()) - ny;
        return dx * dx + dy * dy <= radius * radius;
    }

    private void releasePointer(int pointerId) {
        int index = pointerControls.indexOfKey(pointerId);
        if (index < 0) return;
        int control = pointerControls.valueAt(index);
        pointerControls.removeAt(index);
        int count = heldControlCounts.get(control, 0) - 1;
        if (count <= 0) {
            heldControlCounts.delete(control);
            if (sink != null) {
                if (control == CONTROL_MOUSE_LEFT) sink.mouseButton(0, false);
                else sink.key(control, 0, false);
            }
        } else {
            heldControlCounts.put(control, count);
        }
    }

    private void releaseAll() {
        if (sink != null) {
            for (int i = heldControlCounts.size() - 1; i >= 0; i--) {
                int control = heldControlCounts.keyAt(i);
                if (control == CONTROL_MOUSE_LEFT) sink.mouseButton(0, false);
                else sink.key(control, 0, false);
            }
        }
        pointerControls.clear();
        heldControlCounts.clear();
    }

    @Override protected void onDetachedFromWindow() {
        releaseAll();
        super.onDetachedFromWindow();
    }
}

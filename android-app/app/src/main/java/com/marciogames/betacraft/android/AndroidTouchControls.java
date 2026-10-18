package com.marciogames.betacraft.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import java.util.HashMap;
import java.util.Map;

/**
 * Reusable transparent on-screen controls for an Android-hosted legacy Minecraft view.
 * Add this View above the game SurfaceView in a FrameLayout and connect a CallbackSink.
 * This view does not render Minecraft and remains inert until a compatible LWJGL sink is attached.
 */
final class AndroidTouchControls extends View {
    private static final int KEY_W = 87, KEY_A = 65, KEY_S = 83, KEY_D = 68;
    private static final int KEY_SPACE = 32, KEY_E = 69, KEY_ESCAPE = 256;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<Integer, Integer> heldKeys = new HashMap<>();
    private AndroidLwjglInputAdapter.CallbackSink sink;
    private float downX, downY;
    private int activePointer = -1;
    private int activeKey;
    private boolean activeMouse;

    AndroidTouchControls(Context context) { super(context); init(); }
    AndroidTouchControls(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    void setCallbackSink(AndroidLwjglInputAdapter.CallbackSink value) {
        releaseAll();
        sink = value;
        setVisibility(value == null ? View.GONE : View.VISIBLE);
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
            if (activePointer != -1) return true;
            downX = event.getX(index);
            downY = event.getY(index);
            int key = hitKey(downX, downY);
            boolean mouseHit = hit(downX, downY, 0.72f, 0.76f, 0.10f);
            if (key == 0 && !mouseHit) return false;
            activePointer = event.getPointerId(index);
            if (key != 0) { activeKey = key; sink.key(key, 0, true); }
            else { activeMouse = true; sink.mouseButton(0, true); }
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP
                || action == MotionEvent.ACTION_CANCEL) {
            int pointer = event.getPointerId(index);
            if (pointer == activePointer) releaseActive();
            invalidate();
            return true;
        }
        return true;
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

    private void releaseActive() {
        if (activeKey != 0) sink.key(activeKey, 0, false);
        if (activeMouse) sink.mouseButton(0, false);
        activeKey = 0;
        activeMouse = false;
        activePointer = -1;
    }

    private void releaseAll() {
        if (sink != null) {
            for (Integer key : heldKeys.values()) sink.key(key, 0, false);
            if (activeKey != 0) sink.key(activeKey, 0, false);
            if (activeMouse) sink.mouseButton(0, false);
        }
        heldKeys.clear();
        activeKey = 0;
        activeMouse = false;
        activePointer = -1;
    }

    @Override protected void onDetachedFromWindow() {
        releaseAll();
        super.onDetachedFromWindow();
    }
}

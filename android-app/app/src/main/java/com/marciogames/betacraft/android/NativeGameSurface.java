package com.marciogames.betacraft.android;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;

/**
 * Native EGL window surface smoke test. This proves Android surface creation
 * and frame presentation only; it is not yet the Minecraft/LWJGL renderer.
 */
final class NativeGameSurface extends SurfaceView implements SurfaceHolder.Callback {
    interface Listener {
        void onSurfaceStatus(String message);
    }

    private static final boolean LIBRARY_AVAILABLE;
    static {
        boolean loaded;
        try {
            System.loadLibrary("betacraft_renderer");
            loaded = true;
        } catch (UnsatisfiedLinkError error) {
            loaded = false;
        }
        LIBRARY_AVAILABLE = loaded;
    }

    private final Listener listener;
    private boolean running;
    static final int INPUT_TOUCH = 1;
    static final int INPUT_KEY = 2;
    static final int INPUT_MOUSE = 3;

    NativeGameSurface(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        init();
    }

    NativeGameSurface(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.listener = null;
        init();
    }

    private void init() {
        getHolder().addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setOnTouchListener((view, event) -> {
            if (!LIBRARY_AVAILABLE || !running) return true;
            int source = event.getSource();
            float width = Math.max(1, getWidth());
            float height = Math.max(1, getHeight());
            float x = Math.max(0f, Math.min(1f, event.getX() / width));
            float y = Math.max(0f, Math.min(1f, event.getY() / height));
            if ((source & android.view.InputDevice.SOURCE_MOUSE) == android.view.InputDevice.SOURCE_MOUSE) {
                nativeSendInput(INPUT_MOUSE, event.getActionMasked(), event.getButtonState(), x, y);
                return true;
            }
            int action = event.getActionMasked();
            int pointerIndex = event.getActionIndex();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN
                    || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
                int count = action == MotionEvent.ACTION_MOVE ? event.getPointerCount() : 1;
                for (int n = 0; n < count; n++) {
                    int index = action == MotionEvent.ACTION_MOVE ? n : pointerIndex;
                    float px = Math.max(0f, Math.min(1f, event.getX(index) / width));
                    float py = Math.max(0f, Math.min(1f, event.getY(index) / height));
                    nativeSendInput(INPUT_TOUCH, action, event.getPointerId(index), px, py);
                }
                return true;
            }
            return true;
        });
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        Surface surface = holder.getSurface();
        if (!LIBRARY_AVAILABLE) {
            notifyStatus("Renderer library missing from this APK.");
            return;
        }
        running = nativeStart(surface);
        notifyStatus(running
                ? "EGL surface started. This is a graphics test, not Minecraft."
                : "Could not start EGL surface; check Android logs.");
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        notifyStatus("Android surface: " + width + " × " + height + " pixels.");
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        if (LIBRARY_AVAILABLE) nativeStop();
        running = false;
        notifyStatus("Graphics surface stopped.");
    }

    private void notifyStatus(String message) {
        if (listener != null) listener.onSurfaceStatus(message);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (LIBRARY_AVAILABLE && running) {
            nativeSendInput(INPUT_KEY, KeyEvent.ACTION_DOWN, keyCode, 0f, 0f);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (LIBRARY_AVAILABLE && running) {
            nativeSendInput(INPUT_KEY, KeyEvent.ACTION_UP, keyCode, 0f, 0f);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /** Non-blocking input poll for the future LWJGL Android event pump. Returns five floats or null. */
    static float[] pollNativeInputEvent() {
        return LIBRARY_AVAILABLE ? nativePollInput() : null;
    }

    private static native void nativeSendInput(int type, int action, int code, float x, float y);
    private static native float[] nativePollInput();
    private static native boolean nativeStart(Surface surface);
    private static native void nativeStop();
}

package com.marciogames.betacraft.android;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;
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

    private static native boolean nativeStart(Surface surface);
    private static native void nativeStop();
}

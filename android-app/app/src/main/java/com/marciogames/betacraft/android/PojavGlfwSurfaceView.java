package com.marciogames.betacraft.android;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import git.artdeell.dnbootstrap.glfw.GLFW;
import git.artdeell.dnbootstrap.glfw.GLFWClipboard;
import git.artdeell.dnbootstrap.glfw.GLFWCursor;

/**
 * Android SurfaceView adapter for the pinned MojoLauncher/Pojav GLFW JNI backend.
 *
 * This provides the Android window and input side of the GLFW integration. It
 * does not by itself adapt LWJGL 2's Display API or start Minecraft's render loop.
 */
final class PojavGlfwSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    interface Listener {
        void onStatus(String message);
        void onCursor(GLFWCursor cursor);
        void onGrabChanged(boolean grabbed);
    }

    private final Listener listener;
    private boolean surfaceReady;

    PojavGlfwSurfaceView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setFocusable(true);
        setFocusableInTouchMode(true);
        getHolder().addCallback(this);
        try {
            GLFW.setGrabListener(grabbed -> {
                if (this.listener != null) this.listener.onGrabChanged(grabbed);
            });
            GLFW.setPositionCallback((x, y) -> { });
            GLFW.setCursorCallback(cursor -> {
                if (this.listener != null) this.listener.onCursor(cursor);
            });
            GLFW.setInitCallback(() -> { });
            GLFW.setGamepadEnableHandler(() -> { });
            GLFW.setClipboardImpl(new GLFWClipboard() {
                @Override public String getClipboardString() {
                    ClipboardManager clipboard = (ClipboardManager) getContext()
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard == null || !clipboard.hasPrimaryClip()) return "";
                    ClipData clip = clipboard.getPrimaryClip();
                    if (clip == null || clip.getItemCount() == 0) return "";
                    CharSequence text = clip.getItemAt(0).coerceToText(getContext());
                    return text == null ? "" : text.toString();
                }

                @Override public void setClipboardString(String text) {
                    ClipboardManager clipboard = (ClipboardManager) getContext()
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(
                            ClipData.newPlainText("Minecraft", text == null ? "" : text));
                }
            });
            setOnTouchListener(this::onPointerEvent);
        } catch (UnsatisfiedLinkError | ExceptionInInitializerError error) {
            notifyStatus("Pojav GLFW could not initialize: " + error.getMessage());
        }
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        try {
            Surface surface = holder.getSurface();
            GLFW.nativeSurfaceCreated(surface);
            surfaceReady = true;
            requestFocus();
            notifyStatus("Pojav Android GLFW surface created.");
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            surfaceReady = false;
            notifyStatus("GLFW surface creation failed: " + error.getMessage());
        }
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (surfaceReady) {
            try {
                GLFW.nativeSurfaceUpdated();
                notifyStatus("GLFW surface resized: " + width + " × " + height);
            } catch (UnsatisfiedLinkError | RuntimeException error) {
                notifyStatus("GLFW surface update failed: " + error.getMessage());
            }
        }
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        if (surfaceReady) {
            try {
                GLFW.nativeSurfaceDestroyed();
            } catch (UnsatisfiedLinkError | RuntimeException error) {
                notifyStatus("GLFW surface shutdown failed: " + error.getMessage());
            }
        }
        surfaceReady = false;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!surfaceReady) return super.onKeyDown(keyCode, event);
        GLFW.sendRawKeyEvent(keyCode, 1, event.getMetaState(), (char) event.getUnicodeChar());
        return true;
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (!surfaceReady) return super.onKeyUp(keyCode, event);
        GLFW.sendRawKeyEvent(keyCode, 0, event.getMetaState(), (char) event.getUnicodeChar());
        return true;
    }

    private boolean onPointerEvent(View view, MotionEvent event) {
        if (!surfaceReady) return true;
        int source = event.getSource();
        float x = event.getX();
        float y = event.getY();
        if ((source & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_SCROLL) {
                GLFW.sendScrollEvent(event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                        event.getAxisValue(MotionEvent.AXIS_VSCROLL));
            } else {
                GLFW.sendMousePosition0(x, y);
                int button = androidMouseButton(event.getButtonState(), event.getActionButton());
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_BUTTON_PRESS) {
                    GLFW.sendMouseEvent(button, 1, event.getMetaState());
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_BUTTON_RELEASE) {
                    GLFW.sendMouseEvent(button, 0, event.getMetaState());
                }
            }
            return true;
        }
        GLFW.sendMousePosition0(x, y);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            GLFW.sendMouseEvent(0, 1, event.getMetaState());
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP
                || action == MotionEvent.ACTION_CANCEL) {
            GLFW.sendMouseEvent(0, 0, event.getMetaState());
        }
        return true;
    }

    private static int androidMouseButton(int buttons, int actionButton) {
        if (actionButton == MotionEvent.BUTTON_SECONDARY
                || (buttons & MotionEvent.BUTTON_SECONDARY) != 0) return 1;
        if (actionButton == MotionEvent.BUTTON_TERTIARY
                || (buttons & MotionEvent.BUTTON_TERTIARY) != 0) return 2;
        return 0;
    }

    private void notifyStatus(String message) {
        if (listener != null) listener.onStatus(message);
    }
}

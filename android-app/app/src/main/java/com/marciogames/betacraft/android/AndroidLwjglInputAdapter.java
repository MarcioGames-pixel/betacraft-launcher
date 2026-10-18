package com.marciogames.betacraft.android;

import java.lang.reflect.Method;

/**
 * Optional adapter for the Pojav-style LWJGL3 compatibility layer.
 *
 * This does not provide LWJGL itself. It connects Android input events to the
 * CallbackBridge API when a compatible shim is present on the JVM class path.
 * If the shim is absent, it fails closed instead of silently claiming input works.
 */
final class AndroidLwjglInputAdapter {
    interface CallbackSink {
        void key(int glfwKey, int codePoint, boolean down);
        void cursor(float x, float y);
        void mouseButton(int button, boolean down);
        void scroll(double x, double y);
    }

    private AndroidLwjglInputAdapter() { }

    static CallbackSink createReflectiveSink(ClassLoader loader) throws Exception {
        Class<?> bridge = Class.forName("org.lwjgl.glfw.CallbackBridge", true, loader);
        Method key = bridge.getMethod("sendKeyPress",
                int.class, char.class, int.class, int.class, boolean.class);
        Method cursor = bridge.getMethod("sendCursorPos", float.class, float.class);
        Method mouse = bridge.getMethod("sendMouseButton", int.class, boolean.class);
        Method scroll = bridge.getMethod("sendScroll", double.class, double.class);
        return new CallbackSink() {
            @Override public void key(int glfwKey, int codePoint, boolean down) {
                invoke(key, null, glfwKey, (char) codePoint, 0, 0, down);
            }
            @Override public void cursor(float x, float y) {
                invoke(cursor, null, x, y);
            }
            @Override public void mouseButton(int button, boolean down) {
                invoke(mouse, null, button, down);
            }
            @Override public void scroll(double x, double y) {
                invoke(scroll, null, x, y);
            }
        };
    }

    /**
     * Drain queued Android input from the game/render thread and forward it to the
     * optional LWJGL CallbackBridge. Call once per frame from the actual game loop.
     * The bounded drain prevents a stalled frame from monopolizing the render thread.
     */
    static int pumpPendingEvents(CallbackSink sink, int pixelWidth, int pixelHeight, int maxEvents) {
        if (sink == null || maxEvents <= 0) return 0;
        int handled = 0;
        while (handled < maxEvents) {
            float[] event = NativeGameSurface.pollNativeInputEvent();
            if (event == null) break;
            dispatch(sink, event, pixelWidth, pixelHeight);
            handled++;
        }
        return handled;
    }
    static void dispatch(CallbackSink sink, float[] event, int pixelWidth, int pixelHeight) {
        if (sink == null || event == null || event.length < 5) return;
        int type = Math.round(event[0]);
        int action = Math.round(event[1]);
        int code = Math.round(event[2]);
        if (type == NativeGameSurface.INPUT_MOUSE && action == 8) {
            sink.scroll(event[3], event[4]);
            return;
        }
        // NativeGameSurface uses action 13 for cursor-only movement (touch-look).
        // Forward the cursor position without synthesizing a mouse-button transition.
        if (type == NativeGameSurface.INPUT_MOUSE && action == 13) {
            sink.cursor(clamp(event[3]) * Math.max(1, pixelWidth),
                    clamp(event[4]) * Math.max(1, pixelHeight));
            return;
        }
        float x = clamp(event[3]) * Math.max(1, pixelWidth);
        float y = clamp(event[4]) * Math.max(1, pixelHeight);
        if (type == NativeGameSurface.INPUT_KEY) {
            int key = androidKeyToGlfw(code);
            if (key != 0) sink.key(key, 0, action == 0);
        } else if (type == NativeGameSurface.INPUT_TOUCH) {
            sink.cursor(x, y);
            if (action == 0 || action == 5) sink.mouseButton(0, true);
            else if (action == 1 || action == 3 || action == 6) sink.mouseButton(0, false);
        } else if (type == NativeGameSurface.INPUT_MOUSE) {
            sink.cursor(x, y);
            int buttons = code;
            boolean down;
            if (action == 11 || action == 0 || action == 5) down = true;
            else if (action == 12 || action == 1 || action == 6) down = false;
            else return;
            if ((buttons & 1) != 0) sink.mouseButton(0, down);
            if ((buttons & 2) != 0) sink.mouseButton(2, down);
            if ((buttons & 4) != 0) sink.mouseButton(1, down);
        }
    }

    static int androidKeyToGlfw(int keyCode) {
        if (keyCode >= 29 && keyCode <= 54) return 65 + keyCode - 29; // A-Z
        if (keyCode >= 7 && keyCode <= 16) return 48 + keyCode - 7; // 0-9
        switch (keyCode) {
            case 4: return 256; // Back/Escape
            case 19: return 265; // Up
            case 20: return 264; // Down
            case 21: return 263; // Left
            case 22: return 262; // Right
            case 23: return 257; // Enter
            case 24: return  equalKey(); // Volume-up maps to keypad add only as a fallback
            case 61: return 258; // Tab
            case 62: return 32;  // Space
            case 66: return 257; // Enter
            case 67: return 259; // Backspace
            case 111: return 256; // Escape
            case 112: return 261; // Delete
            case 113: return 341; // Left Ctrl
            case 114: return 345; // Right Ctrl
            case 59: return 340; // Left Shift
            case 60: return 344; // Right Shift
            case 57: return 342; // Left Alt
            case 58: return 346; // Right Alt
            case 122: return 268; // Home
            case 123: return 269; // End
            case 92: return 266; // Page Up
            case 93: return 267; // Page Down
            case 124: return 260; // Insert
            case 131: return 290; // F1
            case 132: return 291;
            case 133: return 292;
            case 134: return 293;
            case 135: return 294;
            case 136: return 295;
            case 137: return 296;
            case 138: return 297;
            case 139: return 298;
            case 140: return 299;
            case 141: return 300;
            case 142: return 301;
            case 143: return 302;
            default: return 0;
        }
    }

    private static int equalKey() { return 334; }

    private static float clamp(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }

    private static void invoke(Method method, Object receiver, Object... args) {
        try {
            method.invoke(receiver, args);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("LWJGL input callback failed: " + method.getName(), error);
        }
    }
}

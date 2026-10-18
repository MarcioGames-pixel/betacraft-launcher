package com.marciogames.betacraft.android;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AndroidLwjglInputAdapterTest {
    private static final class Recorder implements AndroidLwjglInputAdapter.CallbackSink {
        int key;
        boolean down;
        int button = -1;
        float x;
        float y;
        int scrolls;
        @Override public void key(int key, int codePoint, boolean down) {
            this.key = key;
            this.down = down;
        }
        @Override public void cursor(float x, float y) {
            this.x = x;
            this.y = y;
        }
        @Override public void mouseButton(int button, boolean down) {
            this.button = button;
            this.down = down;
        }
        @Override public void scroll(double x, double y) { scrolls++; }
    }

    @Test public void mapsCommonAndroidKeysToGlfw() {
        assertEquals(65, AndroidLwjglInputAdapter.androidKeyToGlfw(29)); // A
        assertEquals(90, AndroidLwjglInputAdapter.androidKeyToGlfw(54)); // Z
        assertEquals(48, AndroidLwjglInputAdapter.androidKeyToGlfw(7));  // 0
        assertEquals(57, AndroidLwjglInputAdapter.androidKeyToGlfw(16)); // 9
        assertEquals(256, AndroidLwjglInputAdapter.androidKeyToGlfw(111)); // Escape
        assertEquals(262, AndroidLwjglInputAdapter.androidKeyToGlfw(22)); // Right
    }

    @Test public void dispatchesKeyDownAndUp() {
        Recorder recorder = new Recorder();
        AndroidLwjglInputAdapter.dispatch(recorder,
                new float[]{NativeGameSurface.INPUT_KEY, 0, 29, 0, 0}, 100, 50);
        assertEquals(65, recorder.key);
        assertTrue(recorder.down);
        AndroidLwjglInputAdapter.dispatch(recorder,
                new float[]{NativeGameSurface.INPUT_KEY, 1, 29, 0, 0}, 100, 50);
        assertEquals(65, recorder.key);
        assertEquals(false, recorder.down);
    }

    @Test public void mapsTouchToCursorAndLeftClick() {
        Recorder recorder = new Recorder();
        AndroidLwjglInputAdapter.dispatch(recorder,
                new float[]{NativeGameSurface.INPUT_TOUCH, 0, 0, 0.5f, 0.25f}, 800, 400);
        assertEquals(400f, recorder.x, 0.001f);
        assertEquals(100f, recorder.y, 0.001f);
        assertEquals(0, recorder.button);
        assertTrue(recorder.down);
    }

    @Test public void forwardsMouseScrollAxisDeltas() {
        Recorder recorder = new Recorder();
        AndroidLwjglInputAdapter.dispatch(recorder,
                new float[]{NativeGameSurface.INPUT_MOUSE, 8, 0, -1f, 2f}, 800, 400);
        assertEquals(1, recorder.scrolls);
        assertEquals(0f, recorder.x, 0.001f);
    }

    @Test public void clampsInvalidCoordinates() {
        Recorder recorder = new Recorder();
        AndroidLwjglInputAdapter.dispatch(recorder,
                new float[]{NativeGameSurface.INPUT_TOUCH, 2, 0, Float.NaN, 2f}, 100, 100);
        assertEquals(0f, recorder.x, 0.001f);
        assertEquals(100f, recorder.y, 0.001f);
    }
}

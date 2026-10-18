package com.marciogames.betacraft.android;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/**
 * Diagnostic screen for EGL and the Android-to-JNI input queue.
 * It is intentionally not the Minecraft/LWJGL renderer.
 */
public final class RendererTestActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView inputStatus;
    private boolean polling;
    private int eventCount;

    private final Runnable inputPoller = new Runnable() {
        @Override public void run() {
            if (!polling) return;
            float[] event;
            boolean received = false;
            while ((event = NativeGameSurface.pollNativeInputEvent()) != null) {
                eventCount++;
                inputStatus.setText(String.format(Locale.US,
                        "JNI input events: %d\\nLast: type=%.0f action=%.0f code=%.0f x=%.3f y=%.3f",
                        eventCount, event[0], event[1], event[2], event[3], event[4]));
                received = true;
            }
            if (!received && eventCount == 0) {
                inputStatus.setText("Touch the graphics area or press a keyboard key to test input.");
            }
            handler.postDelayed(this, 50);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(25, 29, 36));
        getWindow().setNavigationBarColor(Color.rgb(25, 29, 36));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(25, 29, 36));
        root.setPadding(dp(16), dp(12), dp(16), dp(16));

        TextView title = new TextView(this);
        title.setText("Android graphics + input test");
        title.setTextColor(Color.rgb(236, 166, 63));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));

        status = new TextView(this);
        status.setText("Waiting for Android surface…");
        status.setTextColor(Color.WHITE);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(48)));

        FrameLayout gameSurfaceFrame = new FrameLayout(this);
        LinearLayout.LayoutParams surfaceParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(gameSurfaceFrame, surfaceParams);
        NativeGameSurface surface = new NativeGameSurface(this, message ->
                runOnUiThread(() -> status.setText(message)));
        surface.setFocusableInTouchMode(true);
        gameSurfaceFrame.addView(surface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        AndroidTouchControls touchControls = new AndroidTouchControls(this);
        touchControls.setCallbackSink(new AndroidLwjglInputAdapter.CallbackSink() {
            @Override public void key(int glfwKey, int codePoint, boolean down) {
                NativeGameSurface.sendVirtualKey(glfwKey, down);
            }
            @Override public void cursor(float x, float y) { }
            @Override public void mouseButton(int button, boolean down) {
                if (button == 0) NativeGameSurface.sendVirtualMouse(down);
            }
            @Override public void scroll(double x, double y) { }
        });
        gameSurfaceFrame.addView(touchControls, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        inputStatus = new TextView(this);
        inputStatus.setText("Touch the graphics area or press a keyboard key to test input.");
        inputStatus.setTextColor(Color.rgb(190, 205, 220));
        inputStatus.setTextSize(13);
        inputStatus.setGravity(Gravity.CENTER);
        inputStatus.setPadding(0, dp(8), 0, dp(8));
        root.addView(inputStatus, new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView note = new TextView(this);
        note.setText("This verifies Android input → JNI queue and EGL presentation only. It does not launch Minecraft or feed LWJGL.");
        note.setTextColor(Color.LTGRAY);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(6), 0, dp(10));
        root.addView(note, new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button back = new Button(this);
        back.setText("Back to launcher");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        polling = true;
        handler.post(inputPoller);
    }

    @Override protected void onPause() {
        polling = false;
        handler.removeCallbacks(inputPoller);
        super.onPause();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

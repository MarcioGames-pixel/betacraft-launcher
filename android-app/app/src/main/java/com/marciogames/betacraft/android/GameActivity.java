package com.marciogames.betacraft.android;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import git.artdeell.dnbootstrap.glfw.GLFW;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hosts the actual Android GLFW window before invoking the Minecraft JVM.
 * The lwjgl2-glfw backend replaces LWJGL2 Display and binds it to this Surface.
 */
public final class GameActivity extends Activity {
    static final String EXTRA_JAVA_HOME = "betacraft.java_home";
    static final String EXTRA_JLI_ARGUMENTS = "betacraft.jli_arguments";
    static final String EXTRA_VERSION = "betacraft.version";

    private final ExecutorService gameThread = Executors.newSingleThreadExecutor();
    private final AtomicBoolean launchStarted = new AtomicBoolean(false);
    private TextView status;
    private PojavGlfwSurfaceView gameSurface;
    private String[] jliArguments;
    private File javaHome;
    private String version;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        String home = getIntent().getStringExtra(EXTRA_JAVA_HOME);
        jliArguments = getIntent().getStringArrayExtra(EXTRA_JLI_ARGUMENTS);
        version = getIntent().getStringExtra(EXTRA_VERSION);
        if (home == null || jliArguments == null || jliArguments.length == 0) {
            finish();
            return;
        }
        javaHome = new File(home);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        gameSurface = new PojavGlfwSurfaceView(this, new PojavGlfwSurfaceView.Listener() {
            @Override public void onStatus(String message) {
                runOnUiThread(() -> {
                    if (status != null) status.setText(message);
                    if (message != null && message.startsWith("Pojav Android GLFW surface created.")) {
                        launchMinecraftAfterSurfaceReady();
                    }
                });
            }

            @Override public void onCursor(git.artdeell.dnbootstrap.glfw.GLFWCursor cursor) {
                // Cursor images are provided by GLFW; Android's fallback pointer remains available.
            }

            @Override public void onGrabChanged(boolean grabbed) {
                runOnUiThread(() -> {
                    if (status != null) status.setText(grabbed
                            ? "Mouse captured — use Back to leave the game."
                            : "Mouse released.");
                });
            }
        });
        root.addView(gameSurface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        AndroidTouchControls controls = new AndroidTouchControls(this);
        controls.setCallbackSink(new AndroidLwjglInputAdapter.CallbackSink() {
            @Override public void key(int glfwKey, int codePoint, boolean down) {
                GLFW.sendKeyEvent(glfwKey, down ? 1 : 0, 0);
            }

            @Override public void cursor(float x, float y) {
                GLFW.sendMousePosition0(x * Math.max(1, gameSurface.getWidth()),
                        y * Math.max(1, gameSurface.getHeight()));
            }

            @Override public void mouseButton(int button, boolean down) {
                GLFW.sendMouseEvent(button, down ? 1 : 0, 0);
            }

            @Override public void scroll(double x, double y) {
                GLFW.sendScrollEvent(x, y);
            }
        });
        root.addView(controls, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(8), dp(4), dp(8), dp(4));
        topBar.setBackgroundColor(0x99000000);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(12);
        status.setText("Creating Android GLFW surface…");
        topBar.addView(status, new LinearLayout.LayoutParams(0, dp(40), 1f));
        Button back = new Button(this);
        back.setText("Exit");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        topBar.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        root.addView(topBar, topParams);
        setContentView(root);
    }

    private void launchMinecraftAfterSurfaceReady() {
        if (!launchStarted.compareAndSet(false, true)) return;
        status.setText("GLFW surface ready. Starting " + (version == null ? "Minecraft" : version) + "…");
        gameThread.execute(() -> {
            try {
                int result = AndroidJvmBridge.launch(javaHome, jliArguments);
                runOnUiThread(() -> {
                    if (status != null) status.setText("Minecraft JVM exited with code " + result
                            + ". Check logcat for BetaCraftRuntime output.");
                });
            } catch (Throwable error) {
                String detail = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                android.util.Log.e("BetaCraftGame", "Minecraft JVM launch failed", error);
                runOnUiThread(() -> {
                    if (status != null) status.setText("Could not start Minecraft: " + detail);
                });
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        gameThread.shutdown();
        super.onDestroy();
    }
}

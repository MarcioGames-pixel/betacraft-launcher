package org.betacraft.launcher.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(31, 27, 23);
    private static final int PANEL = Color.rgb(54, 45, 35);
    private static final int GOLD = Color.rgb(221, 177, 83);
    private static final String MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<String> versions = new ArrayList<>();
    private Spinner versionSpinner;
    private TextView status;
    private Button refreshButton;
    private Button playButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        ensureDirectories();
        loadCachedVersions();
        refreshVersions();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(20));
        root.setBackgroundColor(BG);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("BETACRAFT");
        title.setTextColor(GOLD);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("LEGACY MINECRAFT LAUNCHER  •  ANDROID PORT");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(11);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle, matchWrap());

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));
        panel.setBackground(round(PANEL, 8, Color.rgb(105, 82, 51)));
        root.addView(panel, matchWrap());

        label(panel, "GAME VERSION");
        versionSpinner = new Spinner(this);
        versionSpinner.setAdapter(new ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_dropdown_item, versions));
        panel.addView(versionSpinner, matchWrap());

        refreshButton = button("Refresh version list");
        panel.addView(refreshButton, matchWrap());
        refreshButton.setOnClickListener(v -> refreshVersions());

        label(panel, "GAME DIRECTORY");
        TextView path = new TextView(this);
        path.setText(getFilesDir().getAbsolutePath() + "/.betacraft");
        path.setTextColor(Color.LTGRAY);
        path.setTextSize(12);
        panel.addView(path, matchWrap());

        playButton = button("Play selected version");
        playButton.setOnClickListener(v -> showRuntimeNotice());
        root.addView(playButton, matchWrap());

        Button settings = button("Storage / diagnostics");
        settings.setOnClickListener(v -> showDiagnostics());
        root.addView(settings, matchWrap());

        status = new TextView(this);
        status.setTextColor(Color.rgb(230, 210, 170));
        status.setTextSize(13);
        status.setPadding(dp(4), dp(12), dp(4), dp(4));
        status.setText("Loading version manifest…");
        root.addView(status, matchWrap());

        TextView footer = new TextView(this);
        footer.setText("BetaCraft v1-inspired Android port\nDesktop launcher files are kept unchanged.");
        footer.setTextColor(Color.GRAY);
        footer.setGravity(Gravity.CENTER);
        footer.setTextSize(11);
        footer.setPadding(0, dp(22), 0, 0);
        root.addView(footer, matchWrap());
        setContentView(scroll);
    }

    private void label(LinearLayout parent, String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(GOLD);
        text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        text.setTextSize(12);
        text.setPadding(0, dp(12), 0, dp(5));
        parent.addView(text, matchWrap());
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(Color.rgb(104, 75, 39), 6, GOLD));
        b.setPadding(dp(8), dp(7), dp(8), dp(7));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void ensureDirectories() {
        File base = new File(getFilesDir(), ".betacraft");
        for (String name : new String[]{"versions", "metadata", "instances", "addons", "resources", "logs", "assets"}) {
            new File(base, name).mkdirs();
        }
    }

    private void loadCachedVersions() {
        File cache = new File(getCacheDir(), "versions.json");
        if (!cache.isFile()) return;
        try (InputStream in = new java.io.FileInputStream(cache)) {
            byte[] data = new byte[(int) cache.length()];
            int n = in.read(data);
            if (n > 0) applyManifest(new JSONObject(new String(data, 0, n, StandardCharsets.UTF_8)), false);
        } catch (Exception ignored) { }
    }

    private void refreshVersions() {
        refreshButton.setEnabled(false);
        status.setText("Fetching official version manifest…");
        worker.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(MANIFEST).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("User-Agent", "BetaCraft-Android-v1-port");
                try (InputStream in = new BufferedInputStream(connection.getInputStream())) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                    byte[] bytes = out.toByteArray();
                    File cache = new File(getCacheDir(), "versions.json");
                    try (FileOutputStream file = new FileOutputStream(cache)) { file.write(bytes); }
                    JSONObject manifest = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                    runOnUiThread(() -> applyManifest(manifest, true));
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Could not refresh versions. Cached versions, if available, remain usable. " + e.getClass().getSimpleName());
                    refreshButton.setEnabled(true);
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void applyManifest(JSONObject manifest, boolean fresh) {
        try {
            JSONArray all = manifest.getJSONArray("versions");
            List<String> found = new ArrayList<>();
            for (int i = 0; i < all.length(); i++) {
                JSONObject item = all.getJSONObject(i);
                String id = item.optString("id", "");
                if (isLegacyRelease(id)) found.add(id);
            }
            versions.clear();
            versions.addAll(found);
            if (versions.isEmpty()) versions.add("1.5.2");
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, versions);
            versionSpinner.setAdapter(adapter);
            if (versions.contains("1.5.2")) versionSpinner.setSelection(versions.indexOf("1.5.2"));
            status.setText((fresh ? "Updated" : "Cached") + " version list: " + found.size()
                + " official releases up to 1.5.2. This list does not include every historical BetaCraft build.");
        } catch (Exception e) {
            status.setText("Version manifest was invalid: " + e.getMessage());
        }
        refreshButton.setEnabled(true);
    }

    private boolean isLegacyRelease(String id) {
        if (!id.matches("\\d+\\.\\d+(?:\\.\\d+)?")) return false;
        try {
            String[] p = id.split("\\.");
            int major = Integer.parseInt(p[0]);
            int minor = Integer.parseInt(p[1]);
            int patch = p.length > 2 ? Integer.parseInt(p[2]) : 0;
            if (major == 1) return minor < 5 || (minor == 5 && patch <= 2);
            return major == 0;
        } catch (Exception e) { return false; }
    }

    private void showRuntimeNotice() {
        String selected = versionSpinner.getSelectedItem() == null ? "unknown" : versionSpinner.getSelectedItem().toString();
        new AlertDialog.Builder(this)
            .setTitle("Runtime integration required")
            .setMessage("Selected version: " + selected + "\n\nThe Android UI and version catalogue are in place, but the desktop launcher cannot start a Minecraft JVM directly on Android. The Java runtime / game-process backend still needs to be connected and tested before Play can launch the game.")
            .setPositiveButton("OK", null).show();
    }

    private void showDiagnostics() {
        File base = new File(getFilesDir(), ".betacraft");
        String message = "App files: " + getFilesDir().getAbsolutePath()
            + "\nLauncher data: " + base.getAbsolutePath()
            + "\nInternet permission: enabled"
            + "\nVersion cache: " + new File(getCacheDir(), "versions.json").exists()
            + "\n\nGame launch status: runtime backend not connected.";
        new AlertDialog.Builder(this).setTitle("Storage / diagnostics")
            .setMessage(message).setPositiveButton("OK", null).show();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }
}

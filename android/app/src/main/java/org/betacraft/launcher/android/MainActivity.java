package org.betacraft.launcher.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Android entry point for the BetaCraft mobile adaptation.
 *
 * This first milestone deliberately keeps the desktop source tree untouched.
 * Desktop Swing/AWT classes cannot run directly in Android's framework, so
 * mobile UI and the game/runtime bridge must be adapted separately.
 */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(25, 29, 36);
    private static final int PANEL = Color.rgb(37, 43, 53);
    private static final int BLUE = Color.rgb(55, 133, 220);
    private static final int TEXT = Color.rgb(238, 241, 246);
    private static final int MUTED = Color.rgb(166, 176, 190);
    private LinearLayout content;
    private String selectedVersion = "b1.7.3";
    private String selectedTab = "Launcher";
    private SharedPreferences preferences;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        preferences = getSharedPreferences("betacraft-mobile", MODE_PRIVATE);
        selectedVersion = preferences.getString("version", "b1.7.3");
        showTab("Launcher");
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private void pad(View view, int left, int top, int right, int bottom) {
        view.setPadding(dp(left), dp(top), dp(right), dp(bottom));
    }

    private Button button(String label, boolean primary, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary ? BLUE : PANEL));
        b.setOnClickListener(listener);
        return b;
    }

    private void addGap(LinearLayout parent, int height) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private void showTab(String tab) {
        selectedTab = tab;
        LinearLayout root = column();
        root.setBackgroundColor(BG);

        LinearLayout header = column();
        pad(header, 20, 16, 20, 14);
        header.setBackgroundColor(PANEL);
        TextView brand = text("BetaCraft", 25, TEXT, true);
        header.addView(brand);
        TextView subtitle = text("LEGACY MINECRAFT LAUNCHER", 11, MUTED, true);
        header.addView(subtitle);
        root.addView(header);

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(PANEL);
        String[] tabs = {"Launcher", "Versions", "Servers", "Settings", "Console"};
        for (String item : tabs) {
            Button b = new Button(this);
            b.setText(item);
            b.setTextSize(11);
            b.setAllCaps(false);
            b.setTextColor(item.equals(tab) ? Color.WHITE : MUTED);
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(item.equals(tab) ? BLUE : PANEL));
            b.setOnClickListener(v -> showTab(item));
            nav.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        root.addView(nav);

        ScrollView scroll = new ScrollView(this);
        content = column();
        pad(content, 18, 18, 18, 24);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        if ("Launcher".equals(tab)) renderLauncher();
        else if ("Versions".equals(tab)) renderVersions();
        else if ("Servers".equals(tab)) renderServers();
        else if ("Settings".equals(tab)) renderSettings();
        else renderConsole();
    }

    private void section(String title, String detail) {
        TextView heading = text(title, 19, TEXT, true);
        content.addView(heading);
        if (detail != null) {
            TextView sub = text(detail, 13, MUTED, false);
            pad(sub, 0, 3, 0, 0);
            content.addView(sub);
        }
        addGap(content, 14);
    }

    private void card(String title, String detail) {
        LinearLayout box = column();
        box.setBackgroundColor(PANEL);
        pad(box, 16, 13, 16, 13);
        box.addView(text(title, 15, TEXT, true));
        if (detail != null) {
            TextView sub = text(detail, 12, MUTED, false);
            pad(sub, 0, 5, 0, 0);
            box.addView(sub);
        }
        content.addView(box, new LinearLayout.LayoutParams(-1, -2));
        addGap(content, 10);
    }

    private void renderLauncher() {
        section("Play Minecraft", "Choose a legacy version and manage your game instances.");
        card("Selected version", selectedVersion);
        content.addView(button("Choose version", false, v -> showTab("Versions")),
                new LinearLayout.LayoutParams(-1, -2));
        addGap(content, 8);
        content.addView(button("Launch game", true, v -> new AlertDialog.Builder(this)
                .setTitle("Android runtime not connected")
                .setMessage("The mobile launcher interface is in place, but the Minecraft runtime and desktop launcher bridge still need Android-specific implementation. No game has been started.")
                .setPositiveButton("OK", null).show()),
                new LinearLayout.LayoutParams(-1, -2));
        addGap(content, 18);
        section("Launcher status", null);
        card("Android preview", "The original desktop source and its build remain unchanged.");
        card("Next integration step", "Replace Swing/AWT entry points and connect an ARM64-compatible Java runtime and renderer.");
    }

    private void renderVersions() {
        section("Game versions", "Select a version for the mobile launcher profile.");
        String[] versions = {"c0.0.15a", "rd-132211", "in-20100124", "a1.1.2_01", "b1.7.3", "1.0", "1.2.5", "1.5.2"};
        for (String version : versions) {
            content.addView(button((version.equals(selectedVersion) ? "✓  " : "") + version, version.equals(selectedVersion), v -> {
                selectedVersion = version;
                preferences.edit().putString("version", version).apply();
                Toast.makeText(this, "Selected " + version, Toast.LENGTH_SHORT).show();
                showTab("Versions");
            }), new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void renderServers() {
        section("Server list", "Legacy multiplayer");
        card("Server browser", "The original live server list and protocol integrations have not yet been connected to this Android interface.");
        content.addView(button("Refresh", false, v -> Toast.makeText(this, "Server integration is not connected yet", Toast.LENGTH_SHORT).show()),
                new LinearLayout.LayoutParams(-1, -2));
    }

    private void renderSettings() {
        section("Settings", "Mobile launcher preferences");
        card("Selected version", selectedVersion);
        card("Game directory", "App-private storage (runtime integration pending)");
        card("Controls", "Touch controls, keyboard input and game display scaling still need to be wired into the game renderer.");
        content.addView(button("Reset selected version", false, v -> {
            selectedVersion = "b1.7.3";
            preferences.edit().putString("version", selectedVersion).apply();
            showTab("Settings");
        }), new LinearLayout.LayoutParams(-1, -2));
    }

    private void renderConsole() {
        section("Console", "Build and runtime diagnostics");
        card("Launcher", "Android UI started successfully.");
        card("Runtime", "Not connected");
        card("Game process", "Not started");
        card("Diagnostics", "Use this screen for mobile-specific logs as the runtime bridge is implemented.");
    }
}

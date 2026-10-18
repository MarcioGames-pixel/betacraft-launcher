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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.Properties;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Native Android front end for BetaCraft Launcher v1.
 * The desktop Swing entry points stay untouched; the runtime bridge is a
 * separate milestone because Android cannot execute the desktop ProcessBuilder
 * + AWT/LWJGL path directly.
 */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(25, 29, 36);
    private static final int PANEL = Color.rgb(37, 43, 53);
    private static final int BLUE = Color.rgb(55, 133, 220);
    private static final int TEXT = Color.rgb(238, 241, 246);
    private static final int MUTED = Color.rgb(166, 176, 190);

    private static final String[] VERSIONS = {
        "c0.0.15a", "rd-132211", "rd-160052", "inf-20100618",
        "a1.1.2_01", "b1.7.3", "1.0", "1.2.5", "1.5.2"
    };

    private LinearLayout content;
    private SharedPreferences preferences;
    private String selectedVersion = "b1.7.3";
    private String selectedTab = "Launcher";
    private String activeInstance = "Default";
    private boolean newestFirst = false;
    private final List<String[]> instances = new ArrayList<>();
    private final List<String[]> servers = new ArrayList<>();
    private final List<String> availableVersions = new ArrayList<>();
    private final ExecutorService catalogExecutor = Executors.newSingleThreadExecutor();
    private String catalogStatus = "Catalog not loaded";
    private String versionSearch = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        preferences = getSharedPreferences("betacraft-mobile", MODE_PRIVATE);
        selectedVersion = preferences.getString("version", "b1.7.3");
        activeInstance = preferences.getString("active_instance", "Default");
        newestFirst = preferences.getBoolean("newest_first", false);
        loadPairs("instances", instances);
        loadPairs("servers", servers);
        loadCachedVersions();
        if (instances.isEmpty()) {
            instances.add(new String[]{"Default", selectedVersion});
            savePairs("instances", instances);
        }
        if (!hasInstance(activeInstance)) activeInstance = instances.get(0)[0];
        showTab("Launcher");
        refreshVersionCatalog();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        catalogExecutor.shutdownNow();
    }

    private void loadCachedVersions() {
        try {
            JSONArray array = new JSONArray(preferences.getString("version_catalog", "[]"));
            for (int i = 0; i < array.length(); i++) {
                String version = array.optString(i, "").trim();
                if (!version.isEmpty() && !availableVersions.contains(version)) availableVersions.add(version);
            }
            if (!availableVersions.isEmpty()) catalogStatus = "Cached catalog • " + availableVersions.size() + " versions";
        } catch (Exception ignored) { }
        if (availableVersions.isEmpty()) Collections.addAll(availableVersions, VERSIONS);
    }

    private void refreshVersionCatalog() {
        catalogStatus = "Refreshing official BetaCraft catalog…";
        catalogExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://files.betacraft.uk/launcher/assets/jsons/");
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "BetaCraft-Android-Preview/1.09");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new java.io.IOException("HTTP " + code);
                StringBuilder html = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) html.append(line).append('\n');
                }
                Matcher matcher = Pattern.compile("href=[\\\"']([^\\\"']+\\.info)[\\\"']", Pattern.CASE_INSENSITIVE).matcher(html.toString());
                List<String> found = new ArrayList<>();
                while (matcher.find()) {
                    String fileName = matcher.group(1);
                    if (fileName.contains("/") || fileName.startsWith(".") || fileName.contains(".info.")) continue;
                    String version = URLDecoder.decode(fileName.substring(0, fileName.length() - 5), "UTF-8").trim();
                    if (!version.isEmpty() && !found.contains(version)) found.add(version);
                }
                if (found.isEmpty()) throw new java.io.IOException("No version entries found");
                Collections.sort(found, String.CASE_INSENSITIVE_ORDER);
                JSONArray cache = new JSONArray();
                for (String version : found) cache.put(version);
                synchronized (availableVersions) {
                    availableVersions.clear();
                    availableVersions.addAll(found);
                }
                preferences.edit().putString("version_catalog", cache.toString()).apply();
                catalogStatus = "Official catalog loaded • " + found.size() + " versions";
            } catch (Exception ex) {
                catalogStatus = "Offline: using " + availableVersions.size() + " cached versions (" + ex.getMessage() + ")";
            } finally {
                if (connection != null) connection.disconnect();
                runOnUiThread(() -> { if ("Versions".equals(selectedTab)) showTab("Versions"); });
            }
        });
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
        pad(header, 20, 14, 20, 12);
        header.setBackgroundColor(PANEL);
        header.addView(text("BetaCraft", 25, TEXT, true));
        header.addView(text("LEGACY MINECRAFT LAUNCHER", 11, MUTED, true));
        root.addView(header);

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(PANEL);
        String[] tabs = {"Launcher", "Versions", "Servers", "Settings", "Console"};
        for (String item : tabs) {
            Button b = new Button(this);
            b.setText(item);
            b.setTextSize(10);
            b.setAllCaps(false);
            b.setPadding(dp(2), 0, dp(2), 0);
            b.setTextColor(item.equals(tab) ? Color.WHITE : MUTED);
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(item.equals(tab) ? BLUE : PANEL));
            b.setOnClickListener(v -> showTab(item));
            nav.addView(b, new LinearLayout.LayoutParams(0, dp(46), 1));
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
        content.addView(text(title, 19, TEXT, true));
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
        String[] current = getInstance(activeInstance);
        if (current != null) selectedVersion = current[1];
        section("Play Minecraft", "Choose a legacy version and manage separate game profiles.");
        card("Active instance", activeInstance + "  •  " + selectedVersion);
        content.addView(button("Manage instances", false, v -> showInstanceManager()),
                new LinearLayout.LayoutParams(-1, -2));
        addGap(content, 8);
        content.addView(button("Choose version", false, v -> showTab("Versions")),
                new LinearLayout.LayoutParams(-1, -2));
        addGap(content, 8);
        content.addView(button("Launch game", true, v -> new AlertDialog.Builder(this)
                .setTitle("Runtime not connected")
                .setMessage("The Android profile and launcher UI are working, but Minecraft still needs the ARM64 Java runtime, LWJGL/GL4ES renderer, native libraries and Android launch bridge. The game has not been started.")
                .setPositiveButton("OK", null).show()),
                new LinearLayout.LayoutParams(-1, -2));
        addGap(content, 18);
        section("Launcher status", null);
        card("Profiles", instances.size() + " saved instance(s) on this device");
        card("Desktop compatibility", "The original BetaCraft v1 desktop files and Windows workflow remain unchanged.");
    }

    private void renderVersions() {
        section("Game versions", "Browse the official BetaCraft metadata catalog. Choosing a version saves it to this profile.");
        card("Catalog status", catalogStatus);
        content.addView(button("Refresh official catalog", false, v -> {
            catalogStatus = "Refreshing official BetaCraft catalog…";
            showTab("Versions");
            refreshVersionCatalog();
        }), new LinearLayout.LayoutParams(-1, -2));
        addGap(content, 8);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(TEXT);
        search.setHintTextColor(MUTED);
        search.setHint("Search versions (e.g. b1.7.3 or c0.30)");
        search.setText(versionSearch);
        content.addView(search, new LinearLayout.LayoutParams(-1, -2));
        addGap(content, 4);
        content.addView(button("Search catalog", true, v -> {
            versionSearch = search.getText().toString().trim();
            showTab("Versions");
        }), new LinearLayout.LayoutParams(-1, -2));
        addGap(content, 8);
        content.addView(button(newestFirst ? "Sort: Z → A" : "Sort: A → Z", false, v -> {
            newestFirst = !newestFirst;
            preferences.edit().putBoolean("newest_first", newestFirst).apply();
            showTab("Versions");
        }), new LinearLayout.LayoutParams(-1, -2));
        addGap(content, 8);

        List<String> filtered = new ArrayList<>();
        synchronized (availableVersions) {
            for (String version : availableVersions) {
                if (version.toLowerCase(Locale.ROOT).contains(versionSearch.toLowerCase(Locale.ROOT))) filtered.add(version);
            }
        }
        Collections.sort(filtered, String.CASE_INSENSITIVE_ORDER);
        if (newestFirst) Collections.reverse(filtered);
        int total = filtered.size();
        int limit = Math.min(total, versionSearch.isEmpty() ? 100 : 250);
        card("Results", total + " matching version(s)" + (total > limit ? " • showing " + limit + "; search to narrow the list" : ""));
        for (int i = 0; i < limit; i++) {
            final String version = filtered.get(i);
            boolean active = version.equals(selectedVersion);
            content.addView(button((active ? "✓  " : "") + version, active, v -> {
                selectedVersion = version;
                preferences.edit().putString("version", version).apply();
                setInstanceVersion(activeInstance, version);
                Toast.makeText(this, "Selected " + version, Toast.LENGTH_SHORT).show();
                showTab("Versions");
            }), new LinearLayout.LayoutParams(-1, -2));
        }
        if (total == 0) card("No matches", "Try a shorter version name or refresh the catalog.");
        addGap(content, 10);
        content.addView(button("Download selected version metadata + JAR", true, v -> downloadSelectedVersion()),
                new LinearLayout.LayoutParams(-1, -2));
        addGap(content, 8);
        card("Download status", "Downloads the official .info metadata and version JAR into private app storage. This does not install Java or enable game launching yet.");
    }

    private void downloadSelectedVersion() {
        final String version = selectedVersion;
        Toast.makeText(this, "Starting download for " + version, Toast.LENGTH_SHORT).show();
        catalogExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                File root = new File(getFilesDir(), "betacraft/versions");
                File metadataDir = new File(root, "jsons");
                if (!metadataDir.exists() && !metadataDir.mkdirs()) throw new java.io.IOException("Cannot create metadata folder");
                if (!root.exists() && !root.mkdirs()) throw new java.io.IOException("Cannot create versions folder");
                URL metadataUrl = new URL("https://files.betacraft.uk/launcher/assets/jsons/" +
                        java.net.URLEncoder.encode(version, "UTF-8").replace("+", "%20") + ".info");
                connection = (HttpURLConnection) metadataUrl.openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("User-Agent", "BetaCraft-Android-Preview/1.10");
                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300)
                    throw new java.io.IOException("Metadata HTTP " + connection.getResponseCode());
                File metadataFile = new File(metadataDir, version + ".info");
                try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(metadataFile)) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                } finally { connection.disconnect(); connection = null; }

                Properties properties = new Properties();
                try (InputStream in = new java.io.FileInputStream(metadataFile)) { properties.load(in); }
                String downloadUrl = properties.getProperty("url", "").trim();
                if (downloadUrl.isEmpty()) throw new java.io.IOException("Metadata has no 'url' field");
                if (downloadUrl.startsWith("http://")) downloadUrl = "https://" + downloadUrl.substring(7);
                URL jarUrl = new URL(downloadUrl);
                if (!"https".equalsIgnoreCase(jarUrl.getProtocol()))
                    throw new java.io.IOException("Refusing non-HTTPS JAR URL");
                File jarFile = new File(root, version + ".jar");
                connection = (HttpURLConnection) jarUrl.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setRequestProperty("User-Agent", "BetaCraft-Android-Preview/1.10");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new java.io.IOException("JAR HTTP " + code);
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(jarFile)) {
                    byte[] buffer = new byte[16384]; int count;
                    while ((count = in.read(buffer)) != -1) { out.write(buffer, 0, count); digest.update(buffer, 0, count); }
                } finally { connection.disconnect(); connection = null; }
                String expected = properties.getProperty("sha1", "").trim();
                String actual = toHex(digest.digest());
                if (!expected.isEmpty() && !expected.equalsIgnoreCase(actual)) {
                    jarFile.delete();
                    throw new java.io.IOException("SHA-1 mismatch; incomplete/corrupt JAR removed");
                }
                runOnUiThread(() -> {
                    Toast.makeText(this, "Downloaded " + version + " JAR and metadata", Toast.LENGTH_LONG).show();
                    if ("Versions".equals(selectedTab)) showTab("Versions");
                });
            } catch (Exception ex) {
                if (connection != null) connection.disconnect();
                final String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Version download failed")
                        .setMessage(message).setPositiveButton("OK", null).show());
            }
        });
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    private void renderServers() {
        section("Multiplayer servers", "Saved server addresses for quick reference.");
        content.addView(button("Add server", true, v -> showAddServerDialog()),
                new LinearLayout.LayoutParams(-1, -2));
        addGap(content, 10);
        if (servers.isEmpty()) card("No saved servers", "Tap Add server to save a server name and address.");
        for (String[] server : new ArrayList<>(servers)) {
            LinearLayout box = column();
            box.setBackgroundColor(PANEL);
            pad(box, 14, 12, 14, 12);
            box.addView(text(server[0], 15, TEXT, true));
            box.addView(text(server[1], 12, MUTED, false));
            LinearLayout actions = new LinearLayout(this);
            actions.addView(button("Copy address", false, v -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Minecraft server", server[1]));
                Toast.makeText(this, "Address copied", Toast.LENGTH_SHORT).show();
            }), new LinearLayout.LayoutParams(0, -2, 1));
            actions.addView(button("Remove", false, v -> new AlertDialog.Builder(this)
                    .setTitle("Remove server?")
                    .setMessage(server[0] + " (" + server[1] + ")")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove", (d, w) -> {
                        servers.remove(server);
                        savePairs("servers", servers);
                        showTab("Servers");
                    }).show()), new LinearLayout.LayoutParams(0, -2, 1));
            box.addView(actions);
            content.addView(box);
            addGap(content, 10);
        }
        card("Server connection", "Live ping, protocol detection and joining are not connected until the game runtime is integrated.");
    }

    private void renderSettings() {
        section("Settings", "Mobile launcher preferences");
        card("Active instance", activeInstance);
        card("Selected version", selectedVersion);
        card("Storage", getFilesDir().getAbsolutePath());
        card("Compatibility", "Minimum Android 6.0. Game rendering is not enabled in this preview.");
        content.addView(button("Manage instances", false, v -> showInstanceManager()),
                new LinearLayout.LayoutParams(-1, -2));
        addGap(content, 8);
        content.addView(button("Reset active version", false, v -> {
            selectedVersion = "b1.7.3";
            preferences.edit().putString("version", selectedVersion).apply();
            setInstanceVersion(activeInstance, selectedVersion);
            showTab("Settings");
        }), new LinearLayout.LayoutParams(-1, -2));
    }

    private void renderConsole() {
        section("Console", "Local app diagnostics");
        card("Launcher UI", "Started successfully");
        card("Android", "API " + android.os.Build.VERSION.SDK_INT + " • " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
        card("ABI", android.os.Build.SUPPORTED_ABIS.length == 0 ? "Unknown" : android.os.Build.SUPPORTED_ABIS[0]);
        card("Instances", Integer.toString(instances.size()));
        card("Saved servers", Integer.toString(servers.size()));
        card("Game runtime", "Not installed or connected");
        card("Last action", "Profile and server lists are stored locally with app preferences.");
    }

    private void showInstanceManager() {
        final String[] names = new String[instances.size() + 1];
        for (int i = 0; i < instances.size(); i++) names[i] = instances.get(i)[0] + " — " + instances.get(i)[1];
        names[names.length - 1] = "+ Create new instance";
        new AlertDialog.Builder(this).setTitle("Game instances").setItems(names, (dialog, which) -> {
            if (which == instances.size()) showCreateInstanceDialog();
            else {
                activeInstance = instances.get(which)[0];
                selectedVersion = instances.get(which)[1];
                preferences.edit().putString("active_instance", activeInstance).putString("version", selectedVersion).apply();
                new AlertDialog.Builder(this).setTitle(activeInstance)
                        .setMessage("Version: " + selectedVersion)
                        .setNegativeButton("Close", null)
                        .setPositiveButton("Delete", (d, w) -> confirmDeleteInstance(activeInstance))
                        .show();
                showTab("Launcher");
            }
        }).setNegativeButton("Close", null).show();
    }

    private void showCreateInstanceDialog() {
        LinearLayout form = column();
        pad(form, 20, 8, 20, 0);
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("Instance name");
        form.addView(name);
        new AlertDialog.Builder(this).setTitle("Create instance").setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (d, w) -> {
                    String clean = name.getText().toString().trim();
                    if (clean.isEmpty() || clean.contains("/") || clean.contains("\\") || clean.equals(".") || clean.equals("..")) {
                        Toast.makeText(this, "Enter a valid instance name", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (hasInstance(clean)) {
                        Toast.makeText(this, "That instance already exists", Toast.LENGTH_LONG).show();
                        return;
                    }
                    instances.add(new String[]{clean, selectedVersion});
                    activeInstance = clean;
                    preferences.edit().putString("active_instance", clean).putString("version", selectedVersion).apply();
                    savePairs("instances", instances);
                    showTab("Launcher");
                }).show();
    }

    private void confirmDeleteInstance(String name) {
        if (instances.size() <= 1) {
            Toast.makeText(this, "Keep at least one instance", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Delete instance?")
                .setMessage("Remove the saved profile '" + name + "'? This does not delete any game files.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    for (int i = 0; i < instances.size(); i++) if (instances.get(i)[0].equals(name)) { instances.remove(i); break; }
                    if (activeInstance.equals(name)) activeInstance = instances.get(0)[0];
                    selectedVersion = getInstance(activeInstance)[1];
                    preferences.edit().putString("active_instance", activeInstance).putString("version", selectedVersion).apply();
                    savePairs("instances", instances);
                    showTab("Launcher");
                }).show();
    }

    private void showAddServerDialog() {
        LinearLayout form = column();
        pad(form, 20, 8, 20, 0);
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("Server name");
        form.addView(name);
        EditText address = new EditText(this);
        address.setSingleLine(true);
        address.setHint("host:port (port optional)");
        form.addView(address);
        new AlertDialog.Builder(this).setTitle("Add server").setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String n = name.getText().toString().trim();
                    String a = address.getText().toString().trim();
                    if (n.isEmpty() || a.isEmpty() || a.contains(" ") || a.contains("/") || a.contains("\\")) {
                        Toast.makeText(this, "Enter a server name and valid host:port address", Toast.LENGTH_LONG).show();
                        return;
                    }
                    servers.add(new String[]{n, a});
                    savePairs("servers", servers);
                    showTab("Servers");
                }).show();
    }

    private boolean hasInstance(String name) { return getInstance(name) != null; }

    private String[] getInstance(String name) {
        for (String[] instance : instances) if (instance[0].equals(name)) return instance;
        return null;
    }

    private void setInstanceVersion(String name, String version) {
        String[] instance = getInstance(name);
        if (instance != null) instance[1] = version;
        savePairs("instances", instances);
    }

    private void loadPairs(String key, List<String[]> target) {
        target.clear();
        try {
            JSONArray array = new JSONArray(preferences.getString(key, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String first = item.optString("first", "").trim();
                String second = item.optString("second", "").trim();
                if (!first.isEmpty() && !second.isEmpty()) target.add(new String[]{first, second});
            }
        } catch (Exception ignored) { target.clear(); }
    }

    private void savePairs(String key, List<String[]> source) {
        JSONArray array = new JSONArray();
        try {
            for (String[] pair : source) {
                JSONObject item = new JSONObject();
                item.put("first", pair[0]);
                item.put("second", pair[1]);
                array.put(item);
            }
            preferences.edit().putString(key, array.toString()).apply();
        } catch (Exception ignored) { }
    }
}

package com.marciogames.betacraft.android;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String PREFS = "betacraft_android";
    private static final String PREF_VERSION = "selected_version";
    private static final String VERSION_LIST_URL =
            "https://files.betacraft.uk/launcher/assets/version_list.txt";
    private static final String INFO_BASE_URL =
            "https://files.betacraft.uk/launcher/assets/jsons/";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<String> versions = new ArrayList<>();
    private ArrayAdapter<String> versionAdapter;
    private Spinner versionSpinner;
    private TextView status;
    private TextView storageInfo;
    private Button refreshButton;
    private Button downloadButton;
    private Button launchMethodButton;
    private Button runtimeCheckButton;
    private Button runtimeTestButton;
    private Button runtimeInstallButton;
    private Button runtimeInstallJava8Button;
    private Button runtimeInstallRecommendedButton;
    private ProgressBar progressBar;
    private File dataRoot;
    private volatile boolean activityAlive = true;
    private String ignoreProgrammaticVersion = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dataRoot = new File(getFilesDir(), "betacraft");
        createStorageLayout();
        buildInterface();
        loadCachedVersions();
        refreshVersions(false);
    }

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(25, 29, 36));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("BetaCraft");
        title.setTextColor(Color.rgb(236, 166, 63));
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("MINECRAFT JAVA EDITION • ANDROID");
        subtitle.setTextColor(Color.rgb(177, 185, 198));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextSize(14);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(5);
        root.addView(subtitle, subtitleParams);

        TextView versionLabel = new TextView(this);
        versionLabel.setText("GAME VERSION");
        versionLabel.setTextColor(Color.WHITE);
        versionLabel.setTextSize(13);
        versionLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = matchWrap();
        labelParams.topMargin = dp(28);
        root.addView(versionLabel, labelParams);

        versionSpinner = new Spinner(this);
        versionAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, versions);
        versionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        versionSpinner.setAdapter(versionAdapter);
        LinearLayout.LayoutParams spinnerParams = matchWrap();
        spinnerParams.topMargin = dp(8);
        root.addView(versionSpinner, spinnerParams);

        versionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view,
                                       int position, long id) {
                if (position >= 0 && position < versions.size()) {
                    String selected = versions.get(position);
                    if (selected.equals(ignoreProgrammaticVersion)) {
                        ignoreProgrammaticVersion = null;
                        updateSelectedVersionStatus();
                        return;
                    }
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString(PREF_VERSION, selected).apply();
                    saveVersionToActiveInstance(selected);
                    updateSelectedVersionStatus();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        refreshButton = new Button(this);
        refreshButton.setText("Refresh version list");
        styleButton(refreshButton, false);
        LinearLayout.LayoutParams refreshParams = matchWrap();
        refreshParams.topMargin = dp(10);
        root.addView(refreshButton, refreshParams);
        refreshButton.setOnClickListener(v -> refreshVersions(true));

        downloadButton = new Button(this);
        downloadButton.setText("Download selected version");
        styleButton(downloadButton, true);
        LinearLayout.LayoutParams downloadParams = matchWrap();
        downloadParams.topMargin = dp(8);
        root.addView(downloadButton, downloadParams);
        downloadButton.setOnClickListener(v -> downloadSelectedVersion());

        launchMethodButton = new Button(this);
        launchMethodButton.setText("Install launch method");
        styleButton(launchMethodButton, false);
        LinearLayout.LayoutParams methodParams = matchWrap();
        methodParams.topMargin = dp(8);
        root.addView(launchMethodButton, methodParams);
        launchMethodButton.setOnClickListener(v -> prepareLaunchMethod());

        runtimeInstallButton = new Button(this);
        runtimeInstallButton.setText("Install Java 17 runtime (ARM64)");
        styleButton(runtimeInstallButton, false);
        LinearLayout.LayoutParams runtimeInstallParams = matchWrap();
        runtimeInstallParams.topMargin = dp(8);
        root.addView(runtimeInstallButton, runtimeInstallParams);
        runtimeInstallButton.setOnClickListener(v -> installRuntime(17));

        runtimeInstallJava8Button = new Button(this);
        runtimeInstallJava8Button.setText("Install Java 8 runtime (ARM64)");
        styleButton(runtimeInstallJava8Button, false);
        LinearLayout.LayoutParams runtime8Params = matchWrap();
        runtime8Params.topMargin = dp(8);
        root.addView(runtimeInstallJava8Button, runtime8Params);
        runtimeInstallJava8Button.setOnClickListener(v -> installRuntime(8));

        runtimeInstallRecommendedButton = new Button(this);
        runtimeInstallRecommendedButton.setText("Install recommended Java version");
        styleButton(runtimeInstallRecommendedButton, false);
        LinearLayout.LayoutParams recommendedParams = matchWrap();
        recommendedParams.topMargin = dp(8);
        root.addView(runtimeInstallRecommendedButton, recommendedParams);
        runtimeInstallRecommendedButton.setOnClickListener(v -> {
            String selected = versionSpinner == null || versionSpinner.getSelectedItem() == null
                    ? "" : String.valueOf(versionSpinner.getSelectedItem());
            installRuntime(AndroidLaunchPreflight.recommendedJavaMajor(selected));
        });

        runtimeCheckButton = new Button(this);
        runtimeCheckButton.setText("Check launch requirements");
        styleButton(runtimeCheckButton, false);
        LinearLayout.LayoutParams runtimeCheckParams = matchWrap();
        runtimeCheckParams.topMargin = dp(8);
        root.addView(runtimeCheckButton, runtimeCheckParams);
        runtimeCheckButton.setOnClickListener(v -> inspectRuntime());

        runtimeTestButton = new Button(this);
        runtimeTestButton.setText("Test Java runtime");
        styleButton(runtimeTestButton, false);
        LinearLayout.LayoutParams runtimeTestParams = matchWrap();
        runtimeTestParams.topMargin = dp(8);
        root.addView(runtimeTestButton, runtimeTestParams);
        runtimeTestButton.setOnClickListener(v -> testJavaRuntime());

        Button graphicsTestButton = new Button(this);
        graphicsTestButton.setText("Test Android graphics surface");
        styleButton(graphicsTestButton, false);
        LinearLayout.LayoutParams graphicsParams = matchWrap();
        graphicsParams.topMargin = dp(8);
        root.addView(graphicsTestButton, graphicsParams);
        graphicsTestButton.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, RendererTestActivity.class)));

        Button instancesButton = new Button(this);
        instancesButton.setText("Manage game instances");
        styleButton(instancesButton, false);
        LinearLayout.LayoutParams instancesParams = matchWrap();
        instancesParams.topMargin = dp(8);
        root.addView(instancesButton, instancesParams);
        instancesButton.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, InstancesActivity.class)));

        Button settingsButton = new Button(this);
        settingsButton.setText("Launcher settings");
        styleButton(settingsButton, false);
        LinearLayout.LayoutParams settingsParams = matchWrap();
        settingsParams.topMargin = dp(8);
        root.addView(settingsButton, settingsParams);
        settingsButton.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, LauncherSettingsActivity.class)));

        Button serversButton = new Button(this);
        serversButton.setText("Saved multiplayer servers");
        styleButton(serversButton, false);
        LinearLayout.LayoutParams serversParams = matchWrap();
        serversParams.topMargin = dp(8);
        root.addView(serversButton, serversParams);
        serversButton.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, ServerListActivity.class)));

        Button filesButton = new Button(this);
        filesButton.setText("Browse launcher files");
        styleButton(filesButton, false);
        LinearLayout.LayoutParams filesParams = matchWrap();
        filesParams.topMargin = dp(8);
        root.addView(filesButton, filesParams);
        filesButton.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, LauncherFilesActivity.class)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(android.view.View.GONE);
        LinearLayout.LayoutParams progressParams = matchWrap();
        progressParams.topMargin = dp(8);
        root.addView(progressBar, progressParams);

        status = new TextView(this);
        status.setTextColor(Color.rgb(255, 210, 120));
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        status.setText("Preparing launcher storage…");
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(18);
        root.addView(status, statusParams);

        TextView storageLabel = new TextView(this);
        storageLabel.setText("GAME DATA");
        storageLabel.setTextColor(Color.WHITE);
        storageLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        storageLabel.setTextSize(15);
        LinearLayout.LayoutParams storageLabelParams = matchWrap();
        storageLabelParams.topMargin = dp(24);
        root.addView(storageLabel, storageLabelParams);

        storageInfo = new TextView(this);
        storageInfo.setTextColor(Color.LTGRAY);
        storageInfo.setTextSize(12);
        storageInfo.setText(dataRoot.getAbsolutePath());
        LinearLayout.LayoutParams storageParams = matchWrap();
        storageParams.topMargin = dp(6);
        root.addView(storageInfo, storageParams);

        TextView note = new TextView(this);
        note.setTextColor(Color.LTGRAY);
        note.setTextSize(13);
        note.setText("BetaCraft for Android is an early port. Install the matching ARM64 Java runtime and the version launch method before testing. A native JVM bootstrap is included, but Minecraft rendering, touch input and legacy LWJGL compatibility are not complete yet.");
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.topMargin = dp(18);
        root.addView(note, noteParams);

        setContentView(scroll);
    }

    private void createStorageLayout() {
        String[] directories = {
                "versions", "versions/jsons", "launcher", "launcher/instances",
                "launcher/addons", "launcher/launch-methods", "launcher/lang",
                "logs", "assets", "runtime", "runtime/active"
        };
        boolean ok = dataRoot.mkdirs() || dataRoot.isDirectory();
        for (String relative : directories) {
            File directory = new File(dataRoot, relative);
            ok &= directory.mkdirs() || directory.isDirectory();
        }
        if (!ok) Toast.makeText(this, "Could not create all launcher folders",
                Toast.LENGTH_LONG).show();
    }

    private void loadCachedVersions() {
        File cache = new File(dataRoot, "version_list.txt");
        if (!cache.isFile()) {
            setFallbackVersions();
            return;
        }
        try (InputStream input = new FileInputStream(cache)) {
            List<String> cached = parseVersionList(readAll(input));
            if (!cached.isEmpty()) {
                replaceVersions(cached, true);
                status.setText("Using cached versions. Checking for updates…");
            } else {
                setFallbackVersions();
            }
        } catch (Exception e) {
            setFallbackVersions();
        }
    }

    private void setFallbackVersions() {
        List<String> fallback = new ArrayList<>();
        fallback.add("Classic / Pre-Classic");
        fallback.add("Indev");
        fallback.add("Infdev");
        fallback.add("Alpha");
        fallback.add("Beta 1.7.3");
        fallback.add("Release 1.5.2");
        replaceVersions(fallback, true);
    }

    private void refreshVersions(boolean showToast) {
        setBusy(true, "Loading version catalog…");
        io.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = openConnection(VERSION_LIST_URL);
                String body;
                try (InputStream input = connection.getInputStream()) {
                    body = readAll(input);
                }
                List<String> downloaded = parseVersionList(body);
                if (downloaded.isEmpty()) throw new IllegalStateException("The version catalog is empty or invalid.");

                writeAtomically(new File(dataRoot, "version_list.txt"),
                        body.getBytes(StandardCharsets.UTF_8));
                runOnUiThread(() -> {
                    replaceVersions(downloaded, true);
                    setBusy(false, "Version catalog updated: " + downloaded.size()
                            + " versions available. Select one to continue.");
                    if (showToast) Toast.makeText(this, "Version list updated",
                            Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, "Could not refresh the catalog; keeping local versions. "
                            + safeMessage(e));
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void downloadSelectedVersion() {
        if (versionSpinner.getSelectedItem() == null) {
            status.setText("Select a Minecraft version first.");
            return;
        }
        String selected = String.valueOf(versionSpinner.getSelectedItem());
        if (!isSafeVersionName(selected)) {
            status.setText("Invalid version name; download cancelled.");
            return;
        }
        setBusy(true, "Preparing download for " + selected + "…");
        io.execute(() -> {
            try {
                File versionsDir = new File(dataRoot, "versions");
                File infoDir = new File(versionsDir, "jsons");
                if (!(infoDir.mkdirs() || infoDir.isDirectory())) {
                    throw new IllegalStateException("Could not create the metadata directory.");
                }

                File infoFile = new File(infoDir, selected + ".info");
                String infoText;
                if (infoFile.isFile() && infoFile.length() > 0) {
                    infoText = readFile(infoFile);
                    if (!hasProperty(infoText, "url")) {
                        infoText = downloadInfo(selected, infoFile);
                    }
                } else {
                    infoText = downloadInfo(selected, infoFile);
                }

                String jarUrl = property(infoText, "url");
                if (jarUrl == null || jarUrl.trim().isEmpty()) {
                    throw new IllegalStateException("The .info file does not contain a JAR URL for this version.");
                }
                jarUrl = normalizeDownloadUrl(jarUrl.trim());

                File jarFile = new File(versionsDir, selected + ".jar");
                String expectedSha1 = property(infoText, "sha1");
                if (jarFile.isFile() && isValidJar(jarFile, expectedSha1)) {
                    runOnUiThread(() -> setBusy(false,
                            "Existing JAR validated for " + selected + ". Path: "
                                    + jarFile.getAbsolutePath()));
                    return;
                }

                final String finalJarUrl = jarUrl;
                runOnUiThread(() -> status.setText("Downloading JAR: " + selected
                        + " (missing, incomplete or invalid files will be replaced)"));

                downloadFile(finalJarUrl, jarFile, expectedSha1);

                runOnUiThread(() -> {
                    setBusy(false, "Download complete: " + selected + "\n"
                            + jarFile.getAbsolutePath()
                            + "\nNext: validate the runtime and launch pipeline.");
                    Toast.makeText(this, "Version downloaded", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> setBusy(false,
                        "Download failed: " + safeMessage(e)));
            }
        });
    }

    private void prepareLaunchMethod() {
        if (versionSpinner == null || versionSpinner.getSelectedItem() == null) {
            status.setText("Select a version first.");
            return;
        }
        String selected = String.valueOf(versionSpinner.getSelectedItem());
        if (!isSafeVersionName(selected)) {
            status.setText("Invalid version name.");
            return;
        }
        setBusy(true, "Installing launch method for " + selected + "…");
        io.execute(() -> {
            try {
                String report = AndroidLaunchMethodInstaller.install(dataRoot, selected,
                        message -> runOnUiThread(() -> {
                            if (activityAlive && status != null) status.setText(message);
                        }));
                runOnUiThread(() -> setBusy(false, report));
            } catch (Exception e) {
                runOnUiThread(() -> setBusy(false,
                        "Could not install the launch method: " + safeMessage(e)));
            }
        });
    }

    private void testJavaRuntime() {
        String selected = versionSpinner == null || versionSpinner.getSelectedItem() == null
                ? "" : String.valueOf(versionSpinner.getSelectedItem());
        int major = AndroidLaunchPreflight.recommendedJavaMajor(selected);
        File home = new File(dataRoot, "runtime/java" + major);
        if (!AndroidRuntimeInstaller.hasCompatibleRuntime(home, major)) {
            status.setText("Java " + major + " ARM64 is not installed or failed architecture validation. Install the runtime first.");
            return;
        }
        setBusy(true, "Starting the Java " + major + " runtime self-test…");
        io.execute(() -> {
            try {
                int result = AndroidJvmBridge.test(home);
                runOnUiThread(() -> setBusy(false, result == 0
                        ? "Java " + major + " runtime self-test completed successfully. See logcat for JVM output."
                        : "Java " + major + " returned exit code " + result + ". Check the device log for details."));
            } catch (Throwable e) {
                runOnUiThread(() -> setBusy(false,
                        "Java runtime test failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())));
            }
        });
    }

    private void inspectRuntime() {
        if (versionSpinner.getSelectedItem() == null) {
            status.setText("Select a version before checking requirements.");
            return;
        }
        String selected = String.valueOf(versionSpinner.getSelectedItem());
        setBusy(true, "Checking Android runtime requirements…");
        io.execute(() -> {
            String report = AndroidLaunchPreflight.inspect(
                    dataRoot, selected, android.os.Build.SUPPORTED_ABIS,
                    new File(getApplicationInfo().nativeLibraryDir));
            runOnUiThread(() -> setBusy(false, report));
        });
    }

    private void installRuntime(int javaMajor) {
        boolean arm64 = false;
        for (String abi : android.os.Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) arm64 = true;
        }
        if (!arm64) {
            status.setText("This runtime requires a 64-bit ARM (ARM64) device.");
            return;
        }
        final String selectedVersion = versionSpinner == null || versionSpinner.getSelectedItem() == null
                ? "" : String.valueOf(versionSpinner.getSelectedItem());
        setBusy(true, "Installing OpenJDK " + javaMajor + " for ARM64…");
        io.execute(() -> {
            try {
                AndroidRuntimeInstaller.install(new File(dataRoot, "runtime"), javaMajor,
                        (stage, current, total) -> runOnUiThread(() -> {
                            if (total > 0 && total < 50000) {
                                int percent = (int) Math.min(100, current * 100L / total);
                                progressBar.setIndeterminate(false);
                                progressBar.setProgress(percent);
                            } else {
                                progressBar.setIndeterminate(true);
                            }
                            status.setText(stage + (total > 0 && total < 50000
                                    ? " (" + current + "/" + total + ")" : ""));
                        }));
                String report = AndroidLaunchPreflight.inspect(
                        dataRoot, selectedVersion,
                        android.os.Build.SUPPORTED_ABIS,
                        new File(getApplicationInfo().nativeLibraryDir));
                runOnUiThread(() -> setBusy(false,
                        "OpenJDK " + javaMajor + " ARM64 installed and validated. The JVM bootstrap is available, but the Minecraft renderer and input layer are still missing. Report: "
                                + report));
            } catch (Exception e) {
                runOnUiThread(() -> setBusy(false,
                        "Could not install OpenJDK " + javaMajor + ": " + safeMessage(e)));
            }
        });
    }

    private String downloadInfo(String version, File destination) throws Exception {
        String encoded = URLEncoder.encode(version, "UTF-8").replace("+", "%20");
        String url = INFO_BASE_URL + encoded + ".info";
        runOnUiThread(() -> status.setText("Downloading metadata for " + version + "…"));
        HttpURLConnection connection = null;
        try {
            connection = openConnection(url);
            String body;
            try (InputStream input = connection.getInputStream()) {
                body = readAll(input);
            }
            if (!hasProperty(body, "url")) {
                throw new IllegalStateException("Invalid metadata: required property 'url' is missing.");
            }
            writeAtomically(destination, body.getBytes(StandardCharsets.UTF_8));
            return body;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private boolean isValidJar(File file, String expectedSha1) {
        if (file == null || !file.isFile() || file.length() == 0) return false;
        try (java.util.jar.JarFile archive = new java.util.jar.JarFile(file)) {
            if (archive.size() == 0) return false;
        } catch (Exception invalidArchive) {
            return false;
        }
        if (expectedSha1 == null || expectedSha1.trim().isEmpty()) return true;
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            return expectedSha1.trim().equalsIgnoreCase(toHex(digest.digest()));
        } catch (Exception invalidDigest) {
            return false;
        }
    }

    private void downloadFile(String sourceUrl, File destination, String expectedSha1)
            throws Exception {
        HttpURLConnection connection = null;
        File temporary = new File(destination.getParentFile(), destination.getName() + ".part");
        try {
            connection = openConnection(sourceUrl);
            int response = connection.getResponseCode();
            if (response < 200 || response >= 300) {
                throw new IllegalStateException("Server returned HTTP " + response);
            }
            final long total = connection.getContentLength();
            runOnUiThread(() -> {
                progressBar.setIndeterminate(total <= 0);
                progressBar.setProgress(0);
            });

            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            long downloaded = 0;
            byte[] buffer = new byte[32 * 1024];
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(temporary)) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    digest.update(buffer, 0, count);
                    downloaded += count;
                    final long current = downloaded;
                    if (total > 0) {
                        final int percent = (int) Math.min(100, current * 100L / total);
                        runOnUiThread(() -> {
                            progressBar.setIndeterminate(false);
                            progressBar.setProgress(percent);
                            status.setText("Downloading… " + percent + "% ("
                                    + humanSize(current) + " / " + humanSize(total) + ")");
                        });
                    } else {
                        runOnUiThread(() -> status.setText("Downloading… " + humanSize(current)));
                    }
                }
                output.getFD().sync();
            }

            if (temporary.length() == 0) throw new IllegalStateException("The server returned an empty file.");
            if (expectedSha1 != null && !expectedSha1.trim().isEmpty()) {
                String actual = toHex(digest.digest());
                if (!actual.equalsIgnoreCase(expectedSha1.trim())) {
                    throw new IllegalStateException("SHA-1 mismatch; temporary file discarded.");
                }
            }
            if (destination.exists() && !destination.delete()) {
                throw new IllegalStateException("Could not replace the destination file.");
            }
            if (!temporary.renameTo(destination)) {
                copyFile(temporary, destination);
                if (!temporary.delete()) temporary.deleteOnExit();
            }
        } catch (Exception e) {
            if (temporary.exists()) temporary.delete();
            throw e;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private HttpURLConnection openConnection(String address) throws Exception {
        URL current = new URL(address);
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (!"https".equalsIgnoreCase(current.getProtocol())
                    || current.getUserInfo() != null) {
                throw new IllegalStateException("Rejected insecure connection: " + current.getProtocol());
            }
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "BetaCraft-Android-Port/0.3");

            int code;
            try {
                code = connection.getResponseCode();
            } catch (Exception e) {
                connection.disconnect();
                throw e;
            }
            if (code >= 200 && code < 300) {
                return connection;
            }
            if (code >= 300 && code < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.trim().isEmpty() || redirects == 5) {
                    throw new IllegalStateException("Missing or excessive redirect chain.");
                }
                URL redirected = new URL(current, location);
                if (!"https".equalsIgnoreCase(redirected.getProtocol())
                        || redirected.getUserInfo() != null) {
                    throw new IllegalStateException("Rejected insecure redirect.");
                }
                current = redirected;
                continue;
            }
            connection.disconnect();
            throw new IllegalStateException("Servidor respondeu HTTP " + code + " em "
                    + current.getHost());
        }
        throw new IllegalStateException("Too many HTTPS redirects.");
    }

    private String normalizeDownloadUrl(String value) throws Exception {
        URL url = new URL(value);
        if ("https".equalsIgnoreCase(url.getProtocol())) return url.toString();
        if ("http".equalsIgnoreCase(url.getProtocol())
                && ("files.betacraft.uk".equalsIgnoreCase(url.getHost())
                || "betacraft.uk".equalsIgnoreCase(url.getHost())
                || url.getHost().endsWith(".betacraft.uk"))) {
            return new URL("https", url.getHost(), url.getPort(), url.getFile()).toString();
        }
        throw new IllegalStateException("HTTP download URL rejected; HTTPS is required.");
    }

    private void replaceVersions(List<String> newVersions, boolean preserveSelection) {
        String previous = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(PREF_VERSION, "");
        String assigned = getActiveInstanceVersion();
        versions.clear();
        versions.addAll(newVersions);
        if (versionAdapter != null) versionAdapter.notifyDataSetChanged();

        // On startup/refresh, prefer the active instance's version when it exists
        // in the catalogue. Otherwise preserve the user's last global selection.
        int selectedIndex = !assigned.isEmpty() ? versions.indexOf(assigned) : -1;
        if (selectedIndex < 0) selectedIndex = versions.indexOf(previous);
        if (selectedIndex < 0 && !versions.isEmpty()) selectedIndex = 0;
        if (selectedIndex >= 0) {
            String selected = versions.get(selectedIndex);
            ignoreProgrammaticVersion = selected;
            versionSpinner.setSelection(selectedIndex);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_VERSION, selected).apply();
        }
    }

    private String getActiveInstanceVersion() {
        String id = getSharedPreferences(PREFS, MODE_PRIVATE).getString("active_instance_id", "");
        if (id.isEmpty()) return "";
        try {
            InstanceStorage.Instance instance = InstanceStorage.findById(dataRoot, id);
            return instance == null ? "" : instance.version;
        } catch (Exception ignored) {
            return "";
        }
    }

    private void saveVersionToActiveInstance(String version) {
        String id = getSharedPreferences(PREFS, MODE_PRIVATE).getString("active_instance_id", "");
        if (id.isEmpty()) return;
        try {
            InstanceStorage.Instance instance = InstanceStorage.findById(dataRoot, id);
            if (instance != null && !version.equals(instance.version)) {
                InstanceStorage.setVersion(instance, version);
            }
        } catch (Exception e) {
            if (status != null) status.setText("Version selected, but the active instance could not be updated: "
                    + safeMessage(e));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (versionSpinner == null || versions.isEmpty()) return;
        String assigned = getActiveInstanceVersion();
        int index = versions.indexOf(assigned);
        if (index >= 0 && index != versionSpinner.getSelectedItemPosition()) {
            ignoreProgrammaticVersion = assigned;
            versionSpinner.setSelection(index);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_VERSION, assigned).apply();
        }
    }

    private void updateSelectedVersionStatus() {
        if (status == null || versionSpinner == null || versionSpinner.getSelectedItem() == null) return;
        String version = String.valueOf(versionSpinner.getSelectedItem());
        File jar = new File(new File(dataRoot, "versions"), version + ".jar");
        if (jar.isFile() && jar.length() > 0) {
            status.setText("JAR already downloaded for " + version + ".");
        }
    }

    private List<String> parseVersionList(String body) {
        List<String> parsed = new ArrayList<>();
        String delimiter = String.valueOf((char) 96);
        for (String line : body.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String[] fields = trimmed.split(delimiter, -1);
            String name = fields[0].trim();
            if (isSafeVersionName(name) && !parsed.contains(name)) parsed.add(name);
        }
        return parsed;
    }

    private boolean isSafeVersionName(String name) {
        if (name == null || name.isEmpty() || name.length() > 100) return false;
        if (".".equals(name) || "..".equals(name)) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-'
                    || c == ' ' || c == '(' || c == ')' || c == '+')) return false;
        }
        return true;
    }

    private boolean hasProperty(String content, String key) {
        return property(content, key) != null;
    }

    private String property(String content, String key) {
        for (String line : content.split("\\r?\\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equals(key)) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    private String readFile(File file) throws Exception {
        try (InputStream input = new FileInputStream(file)) {
            return readAll(input);
        }
    }

    private String readAll(InputStream input) throws Exception {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line).append('\n');
        }
        return result.toString();
    }

    private void writeAtomically(File destination, byte[] bytes) throws Exception {
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(bytes);
            output.getFD().sync();
        }
        if (destination.exists() && !destination.delete()) {
            throw new IllegalStateException("Could not update " + destination.getName());
        }
        if (!temporary.renameTo(destination)) {
            copyFile(temporary, destination);
            if (!temporary.delete()) temporary.deleteOnExit();
        }
    }

    private void copyFile(File source, File destination) throws Exception {
        try (InputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.getFD().sync();
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return result.toString();
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0));
    }

    private void styleButton(Button button, boolean primary) {
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackgroundTintList(ColorStateList.valueOf(primary
                ? Color.rgb(190, 113, 35) : Color.rgb(53, 62, 76)));
        button.setMinHeight(dp(48));
    }

    private void setBusy(boolean busy, String message) {
        if (status != null) status.setText(message);
        if (refreshButton != null) refreshButton.setEnabled(!busy);
        if (downloadButton != null) downloadButton.setEnabled(!busy);
        if (launchMethodButton != null) launchMethodButton.setEnabled(!busy);
        if (runtimeCheckButton != null) runtimeCheckButton.setEnabled(!busy);
        if (runtimeInstallButton != null) runtimeInstallButton.setEnabled(!busy);
        if (runtimeInstallJava8Button != null) runtimeInstallJava8Button.setEnabled(!busy);
        if (runtimeInstallRecommendedButton != null) runtimeInstallRecommendedButton.setEnabled(!busy);
        if (progressBar != null) {
            progressBar.setVisibility(busy ? android.view.View.VISIBLE : android.view.View.GONE);
            progressBar.setIndeterminate(true);
        }
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        activityAlive = false;
        io.shutdownNow();
        super.onDestroy();
    }
}

package com.marciogames.betacraft.android;

import android.app.Activity;
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
    private ProgressBar progressBar;
    private File dataRoot;
    private volatile boolean activityAlive = true;

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
        scroll.setBackgroundColor(Color.rgb(29, 31, 34));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("BetaCraft");
        title.setTextColor(Color.WHITE);
        title.setTextSize(32);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Android port • early development");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(14);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(5);
        root.addView(subtitle, subtitleParams);

        TextView versionLabel = new TextView(this);
        versionLabel.setText("Minecraft version");
        versionLabel.setTextColor(Color.WHITE);
        versionLabel.setTextSize(16);
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
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString(PREF_VERSION, versions.get(position)).apply();
                    updateSelectedVersionStatus();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        refreshButton = new Button(this);
        refreshButton.setText("Atualizar lista de versões");
        LinearLayout.LayoutParams refreshParams = matchWrap();
        refreshParams.topMargin = dp(10);
        root.addView(refreshButton, refreshParams);
        refreshButton.setOnClickListener(v -> refreshVersions(true));

        downloadButton = new Button(this);
        downloadButton.setText("Baixar versão selecionada");
        LinearLayout.LayoutParams downloadParams = matchWrap();
        downloadParams.topMargin = dp(8);
        root.addView(downloadButton, downloadParams);
        downloadButton.setOnClickListener(v -> downloadSelectedVersion());

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
        status.setText("Preparando armazenamento do launcher…");
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(18);
        root.addView(status, statusParams);

        TextView storageLabel = new TextView(this);
        storageLabel.setText("Armazenamento");
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
        note.setText("O launcher pode baixar metadados e o JAR da versão selecionada. Isso ainda não integra o runtime Java nem inicia o Minecraft.");
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
                "logs", "assets"
        };
        boolean ok = dataRoot.mkdirs() || dataRoot.isDirectory();
        for (String relative : directories) {
            File directory = new File(dataRoot, relative);
            ok &= directory.mkdirs() || directory.isDirectory();
        }
        if (!ok) Toast.makeText(this, "Não foi possível criar todas as pastas",
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
                status.setText("Lista em cache. Tentando atualizar pela internet…");
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
        setBusy(true, "Carregando catálogo de versões…");
        io.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = openConnection(VERSION_LIST_URL);
                String body;
                try (InputStream input = connection.getInputStream()) {
                    body = readAll(input);
                }
                List<String> downloaded = parseVersionList(body);
                if (downloaded.isEmpty()) throw new IllegalStateException("Catálogo vazio ou inválido.");

                writeAtomically(new File(dataRoot, "version_list.txt"),
                        body.getBytes(StandardCharsets.UTF_8));
                runOnUiThread(() -> {
                    replaceVersions(downloaded, true);
                    setBusy(false, "Catálogo atualizado: " + downloaded.size()
                            + " versões disponíveis. Escolha uma e toque em Baixar.");
                    if (showToast) Toast.makeText(this, "Lista de versões atualizada",
                            Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, "Falha ao atualizar o catálogo; mantendo a lista local. "
                            + safeMessage(e));
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void downloadSelectedVersion() {
        if (versionSpinner.getSelectedItem() == null) {
            status.setText("Nenhuma versão selecionada.");
            return;
        }
        String selected = String.valueOf(versionSpinner.getSelectedItem());
        if (!isSafeVersionName(selected)) {
            status.setText("Nome de versão inválido; download cancelado.");
            return;
        }
        setBusy(true, "Preparando download de " + selected + "…");
        io.execute(() -> {
            try {
                File versionsDir = new File(dataRoot, "versions");
                File infoDir = new File(versionsDir, "jsons");
                if (!(infoDir.mkdirs() || infoDir.isDirectory())) {
                    throw new IllegalStateException("Não foi possível criar a pasta de metadados.");
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
                    throw new IllegalStateException("O .info não contém uma URL de JAR para esta versão.");
                }
                jarUrl = normalizeDownloadUrl(jarUrl.trim());

                File jarFile = new File(versionsDir, selected + ".jar");
                if (jarFile.isFile() && jarFile.length() > 0) {
                    runOnUiThread(() -> setBusy(false,
                            "Já existe um JAR para " + selected + ". Caminho: "
                                    + jarFile.getAbsolutePath()));
                    return;
                }

                final String finalJarUrl = jarUrl;
                runOnUiThread(() -> status.setText("Baixando JAR: " + selected));

                String expectedSha1 = property(infoText, "sha1");
                downloadFile(finalJarUrl, jarFile, expectedSha1);

                runOnUiThread(() -> {
                    setBusy(false, "Download concluído: " + selected + "\n"
                            + jarFile.getAbsolutePath()
                            + "\nO próximo passo é integrar o runtime e o lançamento.");
                    Toast.makeText(this, "Versão baixada", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> setBusy(false,
                        "Download falhou: " + safeMessage(e)));
            }
        });
    }

    private String downloadInfo(String version, File destination) throws Exception {
        String encoded = URLEncoder.encode(version, "UTF-8").replace("+", "%20");
        String url = INFO_BASE_URL + encoded + ".info";
        runOnUiThread(() -> status.setText("Baixando metadados de " + version + "…"));
        HttpURLConnection connection = null;
        try {
            connection = openConnection(url);
            String body;
            try (InputStream input = connection.getInputStream()) {
                body = readAll(input);
            }
            if (!hasProperty(body, "url")) {
                throw new IllegalStateException("Metadados inválidos: propriedade 'url' ausente.");
            }
            writeAtomically(destination, body.getBytes(StandardCharsets.UTF_8));
            return body;
        } finally {
            if (connection != null) connection.disconnect();
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
                throw new IllegalStateException("Servidor respondeu HTTP " + response);
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
                            status.setText("Baixando… " + percent + "% ("
                                    + humanSize(current) + " / " + humanSize(total) + ")");
                        });
                    } else {
                        runOnUiThread(() -> status.setText("Baixando… " + humanSize(current)));
                    }
                }
                output.getFD().sync();
            }

            if (temporary.length() == 0) throw new IllegalStateException("O servidor enviou um arquivo vazio.");
            if (expectedSha1 != null && !expectedSha1.trim().isEmpty()) {
                String actual = toHex(digest.digest());
                if (!actual.equalsIgnoreCase(expectedSha1.trim())) {
                    throw new IllegalStateException("SHA-1 não confere; arquivo temporário descartado.");
                }
            }
            if (destination.exists() && !destination.delete()) {
                throw new IllegalStateException("Não foi possível substituir o arquivo de destino.");
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
        URL url = new URL(address);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IllegalStateException("Por segurança, o download precisa usar HTTPS: "
                    + url.getHost());
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "BetaCraft-Android-Port/0.2");
        int code = connection.getResponseCode();
        if (code >= 300 && code < 400) {
            String redirect = connection.getHeaderField("Location");
            connection.disconnect();
            if (redirect == null) throw new IllegalStateException("Redirecionamento sem destino.");
            URL redirected = new URL(url, redirect);
            if (!"https".equalsIgnoreCase(redirected.getProtocol())) {
                throw new IllegalStateException("Redirecionamento HTTP inseguro recusado.");
            }
            connection = (HttpURLConnection) redirected.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("User-Agent", "BetaCraft-Android-Port/0.2");
        }
        return connection;
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
        throw new IllegalStateException("URL de download HTTP recusada; somente HTTPS é permitido.");
    }

    private void replaceVersions(List<String> newVersions, boolean preserveSelection) {
        String previous = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(PREF_VERSION, "");
        versions.clear();
        versions.addAll(newVersions);
        if (versionAdapter != null) versionAdapter.notifyDataSetChanged();
        int selectedIndex = versions.indexOf(previous);
        if (selectedIndex < 0 && !versions.isEmpty()) selectedIndex = 0;
        if (selectedIndex >= 0) {
            versionSpinner.setSelection(selectedIndex);
            if (!preserveSelection || previous.isEmpty() || !versions.contains(previous)) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString(PREF_VERSION, versions.get(selectedIndex)).apply();
            }
        }
    }

    private void updateSelectedVersionStatus() {
        if (status == null || versionSpinner == null || versionSpinner.getSelectedItem() == null) return;
        String version = String.valueOf(versionSpinner.getSelectedItem());
        File jar = new File(new File(dataRoot, "versions"), version + ".jar");
        if (jar.isFile() && jar.length() > 0) {
            status.setText("JAR já baixado para " + version + ".");
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
            throw new IllegalStateException("Não foi possível atualizar " + destination.getName());
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

    private void setBusy(boolean busy, String message) {
        if (status != null) status.setText(message);
        if (refreshButton != null) refreshButton.setEnabled(!busy);
        if (downloadButton != null) downloadButton.setEnabled(!busy);
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

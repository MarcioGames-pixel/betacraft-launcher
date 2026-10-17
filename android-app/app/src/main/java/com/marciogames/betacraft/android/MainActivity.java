package com.marciogames.betacraft.android;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String PREFS = "betacraft_android";
    private static final String PREF_VERSION = "selected_version";
    private static final String VERSION_LIST_URL =
            "https://files.betacraft.uk/launcher/assets/version_list.txt";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<String> versions = new ArrayList<>();
    private ArrayAdapter<String> versionAdapter;
    private Spinner versionSpinner;
    private TextView status;
    private TextView storageInfo;
    private Button refreshButton;
    private File dataRoot;

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

        versionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent,
                                       android.view.View view, int position, long id) {
                if (position >= 0 && position < versions.size()) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString(PREF_VERSION, versions.get(position)).apply();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        refreshButton = new Button(this);
        refreshButton.setText("Atualizar lista de versões");
        LinearLayout.LayoutParams refreshParams = matchWrap();
        refreshParams.topMargin = dp(10);
        root.addView(refreshButton, refreshParams);
        refreshButton.setOnClickListener(v -> refreshVersions(true));

        Button play = new Button(this);
        play.setText("Jogar");
        LinearLayout.LayoutParams playParams = matchWrap();
        playParams.topMargin = dp(16);
        root.addView(play, playParams);

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
        note.setText("Esta etapa já guarda a versão escolhida, cria as pastas básicas e tenta carregar a lista oficial. O download dos arquivos do jogo, o login e a execução do Minecraft ainda não foram portados.");
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.topMargin = dp(18);
        root.addView(note, noteParams);

        play.setOnClickListener(v -> {
            String selected = versionSpinner.getSelectedItem() == null
                    ? "(nenhuma versão)" : String.valueOf(versionSpinner.getSelectedItem());
            status.setText("Versão selecionada: " + selected
                    + "\nAinda não é possível iniciar o jogo: o runtime Android e o processo de lançamento precisam ser integrados.");
            Toast.makeText(this, "A execução do Minecraft ainda não está disponível", Toast.LENGTH_LONG).show();
        });

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
        if (!ok) {
            Toast.makeText(this, "Não foi possível criar todas as pastas do launcher",
                    Toast.LENGTH_LONG).show();
        }
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
                replaceVersions(cached, false);
                status.setText("Lista de versões em cache. Tentando atualizar pela internet…");
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
        replaceVersions(fallback, false);
    }

    private void refreshVersions(boolean showToast) {
        if (refreshButton != null) {
            refreshButton.setEnabled(false);
            refreshButton.setText("Carregando versões…");
        }
        if (showToast) status.setText("Conectando ao catálogo BetaCraft…");

        io.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(VERSION_LIST_URL).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "BetaCraft-Android-Port/0.1");
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) {
                    throw new IllegalStateException("HTTP " + code);
                }
                String body;
                try (InputStream input = connection.getInputStream()) {
                    body = readAll(input);
                }
                List<String> downloaded = parseVersionList(body);
                if (downloaded.isEmpty()) {
                    throw new IllegalStateException("O catálogo não trouxe versões reconhecidas.");
                }

                File cache = new File(dataRoot, "version_list.txt");
                try (FileOutputStream output = new FileOutputStream(cache)) {
                    output.write(body.getBytes(StandardCharsets.UTF_8));
                }
                runOnUiThread(() -> {
                    replaceVersions(downloaded, true);
                    status.setText("Catálogo BetaCraft atualizado: " + downloaded.size()
                            + " versões carregadas. Os arquivos do jogo ainda não foram baixados.");
                    if (showToast) Toast.makeText(this, "Lista de versões atualizada",
                            Toast.LENGTH_SHORT).show();
                    finishRefreshButton();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Não foi possível atualizar o catálogo online. "
                            + "Usando a lista salva ou a lista básica. " + safeMessage(e));
                    finishRefreshButton();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void finishRefreshButton() {
        if (refreshButton != null) {
            refreshButton.setEnabled(true);
            refreshButton.setText("Atualizar lista de versões");
        }
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

    private List<String> parseVersionList(String body) {
        List<String> parsed = new ArrayList<>();
        String delimiter = String.valueOf((char) 96);
        for (String line : body.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String[] fields = trimmed.split(delimiter, -1);
            String name = fields[0].trim();
            if (!name.isEmpty() && !parsed.contains(name)) parsed.add(name);
        }
        return parsed;
    }

    private String readAll(InputStream input) throws Exception {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append('\n');
            }
        }
        return result.toString();
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
        super.onDestroy();
        io.shutdownNow();
    }
}

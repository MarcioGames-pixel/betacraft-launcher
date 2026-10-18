package com.marciogames.betacraft.android;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Persistent launcher preferences; game-side options are applied when launch support exists. */
public final class LauncherSettingsActivity extends Activity {
    private static final String PREFS = "betacraft_android";
    private static final String KEY_RAM = "preferred_ram_mb";
    private static final String KEY_GL4ES = "prefer_gl4es";
    private TextView ramLabel;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(25, 29, 36));
        getWindow().setNavigationBarColor(Color.rgb(25, 29, 36));
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(18));
        root.setBackgroundColor(Color.rgb(25, 29, 36));

        TextView title = new TextView(this);
        title.setText("Launcher settings");
        title.setTextColor(Color.rgb(246, 169, 26));
        title.setTextSize(25);
        root.addView(title);

        TextView explanation = new TextView(this);
        explanation.setText("These preferences are saved on this device. RAM and graphics choices are not passed to Minecraft until the game launch pipeline is integrated.");
        explanation.setTextColor(Color.LTGRAY);
        explanation.setPadding(0, dp(8), 0, dp(16));
        root.addView(explanation);

        ramLabel = new TextView(this);
        ramLabel.setTextColor(Color.WHITE);
        root.addView(ramLabel);
        SeekBar ram = new SeekBar(this);
        ram.setMax(4096 - 512);
        ram.setProgress(Math.max(0, Math.min(ram.getMax(),
                prefs.getInt(KEY_RAM, 1536) - 512)));
        root.addView(ram);
        updateRam(ram.getProgress() + 512);
        ram.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int mb = progress + 512;
                updateRam(mb);
                if (fromUser) prefs.edit().putInt(KEY_RAM, mb).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        CheckBox gl = new CheckBox(this);
        gl.setText("Prefer GL4ES legacy OpenGL compatibility");
        gl.setTextColor(Color.WHITE);
        gl.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(246, 169, 26)));
        gl.setChecked(prefs.getBoolean(KEY_GL4ES, true));
        root.addView(gl);
        gl.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean(KEY_GL4ES, checked).apply());

        TextView note = new TextView(this);
        note.setText("Account login, skins, modpack installers, news feeds, server lists and full graphics/input settings still need their services and game-side integrations.");
        note.setTextColor(Color.LTGRAY);
        note.setPadding(0, dp(16), 0, dp(16));
        root.addView(note);

        Button back = new Button(this);
        back.setText("Save and return");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        root.addView(back);
        setContentView(root);
    }

    private void updateRam(int mb) {
        ramLabel.setText("Preferred maximum Java memory: " + mb + " MB");
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
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

public final class MainActivity extends Activity {
    private Spinner versionSpinner;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(29, 31, 34));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
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
        LinearLayout.LayoutParams subParams = matchWrap();
        subParams.topMargin = dp(6);
        root.addView(subtitle, subParams);

        TextView label = new TextView(this);
        label.setText("Minecraft version");
        label.setTextColor(Color.WHITE);
        label.setTextSize(16);
        LinearLayout.LayoutParams labelParams = matchWrap();
        labelParams.topMargin = dp(32);
        root.addView(label, labelParams);

        versionSpinner = new Spinner(this);
        String[] versions = {
            "Classic / Pre-Classic",
            "Indev",
            "Infdev",
            "Alpha",
            "Beta 1.7.3",
            "Release 1.5.2"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, versions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        versionSpinner.setAdapter(adapter);
        LinearLayout.LayoutParams spinnerParams = matchWrap();
        spinnerParams.topMargin = dp(10);
        root.addView(versionSpinner, spinnerParams);

        Button play = new Button(this);
        play.setText("Play");
        LinearLayout.LayoutParams playParams = matchWrap();
        playParams.topMargin = dp(20);
        root.addView(play, playParams);

        status = new TextView(this);
        status.setTextColor(Color.rgb(255, 210, 120));
        status.setTextSize(14);
        status.setText("Prototype UI only. The original desktop launcher uses Swing/AWT; the Android game runtime and launch pipeline still need to be ported.");
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(18);
        root.addView(status, statusParams);

        TextView info = new TextView(this);
        info.setTextColor(Color.LTGRAY);
        info.setTextSize(13);
        info.setText("This build verifies the Android project and GitHub Actions APK pipeline. It does not yet download or run Minecraft.");
        LinearLayout.LayoutParams infoParams = matchWrap();
        infoParams.topMargin = dp(18);
        root.addView(info, infoParams);

        play.setOnClickListener(v -> {
            String selected = String.valueOf(versionSpinner.getSelectedItem());
            status.setText("Selected: " + selected + "\nGame launching is not available yet: the Android runtime integration is unfinished.");
            Toast.makeText(this, "Android game runtime not integrated yet", Toast.LENGTH_LONG).show();
        });

        setContentView(scroll);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

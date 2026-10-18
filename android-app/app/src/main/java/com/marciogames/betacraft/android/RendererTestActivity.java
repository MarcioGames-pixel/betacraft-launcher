package com.marciogames.betacraft.android;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Diagnostic screen for verifying a real Android EGL window surface. */
public final class RendererTestActivity extends Activity {
    private TextView status;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(25, 29, 36));
        getWindow().setNavigationBarColor(Color.rgb(25, 29, 36));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(25, 29, 36));
        root.setPadding(dp(16), dp(12), dp(16), dp(16));

        TextView title = new TextView(this);
        title.setText("Android graphics test");
        title.setTextColor(Color.rgb(236, 166, 63));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));

        status = new TextView(this);
        status.setText("Waiting for Android surface…");
        status.setTextColor(Color.WHITE);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(54)));

        NativeGameSurface surface = new NativeGameSurface(this, message ->
                runOnUiThread(() -> status.setText(message)));
        LinearLayout.LayoutParams surfaceParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(surface, surfaceParams);

        TextView note = new TextView(this);
        note.setText("This test checks EGL and frame presentation. It does not launch Minecraft.");
        note.setTextColor(Color.LTGRAY);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(12), 0, dp(12));
        root.addView(note, new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button back = new Button(this);
        back.setText("Back to launcher");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));
        setContentView(root);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

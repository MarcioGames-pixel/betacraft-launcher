package com.marciogames.betacraft.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Local profile manager. Profiles are metadata only until the game launch pipeline is complete. */
public final class InstancesActivity extends Activity {
    private static final String PREFS = "betacraft_android";
    private static final String KEY_INSTANCES = "local_instances";
    private static final String KEY_ACTIVE = "active_instance";
    private LinearLayout list;
    private TextView active;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(25, 29, 36));
        getWindow().setNavigationBarColor(Color.rgb(25, 29, 36));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(Color.rgb(25, 29, 36));

        TextView title = new TextView(this);
        title.setText("Game instances");
        title.setTextSize(25);
        title.setTextColor(Color.rgb(246, 169, 26));
        root.addView(title);

        active = new TextView(this);
        active.setTextColor(Color.WHITE);
        active.setPadding(0, dp(8), 0, dp(12));
        root.addView(active);

        Button create = new Button(this);
        create.setText("Create instance");
        create.setAllCaps(false);
        root.addView(create);
        create.setOnClickListener(v -> promptCreate());

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button back = new Button(this);
        back.setText("Back to BetaCraft");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        root.addView(back);
        setContentView(root);
        render();
    }

    private void promptCreate() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint("e.g. Beta 1.7.3 survival");
        new AlertDialog.Builder(this).setTitle("New instance").setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (dialog, which) -> addInstance(input.getText().toString()))
                .show();
    }

    private void addInstance(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || name.length() > 48 || name.contains("/") || name.contains("\\")
                || name.contains("..")) {
            new AlertDialog.Builder(this).setMessage("Use 1–48 characters; path separators are not allowed.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        Set<String> names = load();
        for (String old : names) {
            if (old.equalsIgnoreCase(name)) {
                new AlertDialog.Builder(this).setMessage("An instance with that name already exists.")
                        .setPositiveButton("OK", null).show();
                return;
            }
        }
        names.add(name);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putStringSet(KEY_INSTANCES, names)
                .putString(KEY_ACTIVE, name).apply();
        render();
    }

    private Set<String> load() {
        Set<String> stored = getSharedPreferences(PREFS, MODE_PRIVATE).getStringSet(KEY_INSTANCES,
                Collections.<String>emptySet());
        return new TreeSet<>(stored == null ? Collections.<String>emptySet() : stored);
    }

    private void render() {
        list.removeAllViews();
        Set<String> names = load();
        String selected = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_ACTIVE, "Default");
        active.setText("Selected profile: " + selected + "\nProfiles here store names and selection. They do not yet install modpacks or start a separate Minecraft directory.");
        if (names.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No custom instances yet. Create one to save a named profile.");
            empty.setTextColor(Color.LTGRAY);
            empty.setPadding(0, dp(18), 0, dp(18));
            list.addView(empty);
            return;
        }
        for (String name : new ArrayList<>(names)) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(10), dp(8), dp(10), dp(8));
            TextView label = new TextView(this);
            label.setText((name.equals(selected) ? "✓  " : "") + name);
            label.setTextColor(Color.WHITE);
            label.setTextSize(17);
            row.addView(label);
            LinearLayout actions = new LinearLayout(this);
            Button select = new Button(this);
            select.setText("Select");
            select.setAllCaps(false);
            select.setOnClickListener(v -> {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_ACTIVE, name).apply();
                render();
            });
            actions.addView(select, new LinearLayout.LayoutParams(0, -2, 1f));
            Button delete = new Button(this);
            delete.setText("Delete");
            delete.setAllCaps(false);
            delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Delete " + name + "?")
                    .setMessage("Only the saved profile name will be deleted. Game files are not removed.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (d, w) -> {
                        Set<String> updated = load();
                        updated.remove(name);
                        String current = getSharedPreferences(PREFS, MODE_PRIVATE)
                                .getString(KEY_ACTIVE, "Default");
                        android.content.SharedPreferences.Editor editor =
                                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putStringSet(KEY_INSTANCES, updated);
                        if (name.equals(current)) editor.putString(KEY_ACTIVE, updated.isEmpty()
                                ? "Default" : updated.iterator().next());
                        editor.apply();
                        render();
                    }).show());
            actions.addView(delete, new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(actions);
            list.addView(row);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
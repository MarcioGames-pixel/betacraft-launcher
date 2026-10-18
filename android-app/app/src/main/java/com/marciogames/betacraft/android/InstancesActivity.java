package com.marciogames.betacraft.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Independent local game profiles with their own game directories and version assignments. */
public final class InstancesActivity extends Activity {
    private static final String PREFS = "betacraft_android";
    private static final String KEY_LEGACY_INSTANCES = "local_instances";
    private static final String KEY_LEGACY_ACTIVE = "active_instance";
    private static final String KEY_ACTIVE_ID = "active_instance_id";
    private File dataRoot;
    private LinearLayout list;
    private TextView active;
    private List<InstanceStorage.Instance> instances = new ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(25,29,36));
        getWindow().setNavigationBarColor(Color.rgb(25,29,36));
        dataRoot = new File(getFilesDir(), "betacraft");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(18),dp(18),dp(18));
        root.setBackgroundColor(Color.rgb(25,29,36));
        TextView title = new TextView(this);
        title.setText("Game instances"); title.setTextSize(25);
        title.setTextColor(Color.rgb(246,169,26)); root.addView(title);
        active = new TextView(this); active.setTextColor(Color.WHITE);
        active.setPadding(0,dp(8),0,dp(12)); root.addView(active);
        Button create = new Button(this); create.setText("Create instance");
        create.setAllCaps(false); root.addView(create); create.setOnClickListener(v -> promptCreate());
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        Button back = new Button(this); back.setText("Back to BetaCraft"); back.setAllCaps(false);
        back.setOnClickListener(v -> finish()); root.addView(back);
        setContentView(root);
        migrateLegacyProfiles(); render();
    }

    private void migrateLegacyProfiles() {
        try {
            if (!InstanceStorage.list(dataRoot).isEmpty()) return;
            Set<String> legacy = getSharedPreferences(PREFS,MODE_PRIVATE)
                    .getStringSet(KEY_LEGACY_INSTANCES, Collections.<String>emptySet());
            if (legacy == null || legacy.isEmpty()) return;
            String version = getSharedPreferences(PREFS,MODE_PRIVATE).getString("selected_version","");
            String oldActive = getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_LEGACY_ACTIVE,"");
            String newActive = "";
            for (String name : new TreeSet<>(legacy)) {
                try {
                    InstanceStorage.Instance made = InstanceStorage.create(dataRoot,name,version);
                    if (name.equals(oldActive)) newActive = made.id;
                } catch (Exception ignored) { }
            }
            if (!newActive.isEmpty()) getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                    .putString(KEY_ACTIVE_ID,newActive).apply();
        } catch (Exception e) {
            Toast.makeText(this,"Could not migrate old profiles: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    private void promptCreate() {
        EditText input = new EditText(this); input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint("e.g. Beta 1.7.3 survival");
        new AlertDialog.Builder(this).setTitle("New game instance").setView(input)
                .setMessage("Creates a separate game folder for saves, mods, configuration and logs.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Create",(d,w)->addInstance(input.getText().toString())).show();
    }

    private void addInstance(String raw) {
        String version = getSharedPreferences(PREFS,MODE_PRIVATE).getString("selected_version","");
        try {
            InstanceStorage.Instance made = InstanceStorage.create(dataRoot,raw,version);
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(KEY_ACTIVE_ID,made.id).apply();
            render(); Toast.makeText(this,"Created "+made.name,Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            new AlertDialog.Builder(this).setMessage(e.getMessage()==null?"Could not create instance.":e.getMessage())
                    .setPositiveButton("OK",null).show();
        }
    }

    private void render() {
        list.removeAllViews();
        try { instances = InstanceStorage.list(dataRoot); }
        catch (Exception e) { active.setText("Storage error: "+e.getMessage()); return; }
        String activeId = getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_ACTIVE_ID,"");
        InstanceStorage.Instance chosen = null;
        for (InstanceStorage.Instance i:instances) if (i.id.equals(activeId)) chosen=i;
        if (chosen==null && !instances.isEmpty()) {
            chosen=instances.get(0); activeId=chosen.id;
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(KEY_ACTIVE_ID,activeId).apply();
        }
        if (chosen==null) active.setText("No active instance. Create one to get an independent game directory.");
        else active.setText("Active: "+chosen.name+"\nVersion: "+(chosen.version.isEmpty()?"(not assigned)":chosen.version)
                +"\nGame directory: "+chosen.gameDirectory.getAbsolutePath()
                +"\nShared downloaded JARs remain in the launcher cache.");
        if (instances.isEmpty()) {
            TextView empty=new TextView(this); empty.setText("No instances yet.");
            empty.setTextColor(Color.LTGRAY); empty.setPadding(0,dp(18),0,dp(18)); list.addView(empty); return;
        }
        for (InstanceStorage.Instance i:new ArrayList<>(instances)) {
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(10),dp(8),dp(10),dp(8));
            TextView label=new TextView(this); label.setText((i.id.equals(activeId)?"✓  ":"")+i.name);
            label.setTextColor(Color.WHITE); label.setTextSize(17); row.addView(label);
            TextView detail=new TextView(this);
            detail.setText("Version: "+(i.version.isEmpty()?"(not assigned)":i.version)+"\n"+i.gameDirectory.getAbsolutePath());
            detail.setTextColor(Color.LTGRAY); detail.setTextSize(12); detail.setTextIsSelectable(true); row.addView(detail);
            LinearLayout actions=new LinearLayout(this);
            Button select=new Button(this); select.setText("Select"); select.setAllCaps(false);
            select.setOnClickListener(v->{getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(KEY_ACTIVE_ID,i.id).apply();render();});
            actions.addView(select,new LinearLayout.LayoutParams(0,-2,1f));
            Button setVersion=new Button(this); setVersion.setText("Set version"); setVersion.setAllCaps(false);
            setVersion.setOnClickListener(v->promptVersion(i)); actions.addView(setVersion,new LinearLayout.LayoutParams(0,-2,1f));
            Button delete=new Button(this); delete.setText("Delete"); delete.setAllCaps(false);
            delete.setOnClickListener(v->confirmDelete(i)); actions.addView(delete,new LinearLayout.LayoutParams(0,-2,1f));
            row.addView(actions); list.addView(row);
        }
    }

    private void promptVersion(InstanceStorage.Instance instance) {
        EditText input=new EditText(this); input.setSingleLine(true); input.setText(instance.version);
        input.setHint("Exact BetaCraft version name"); input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this).setTitle("Assign version")
                .setMessage("Enter the exact version label from the BetaCraft catalogue.")
                .setView(input).setNegativeButton("Cancel",null)
                .setPositiveButton("Save",(d,w)->{
                    String version=input.getText().toString().trim();
                    if(version.isEmpty()||version.length()>100||version.contains("/")||version.contains("\\")||version.contains("..")){
                        Toast.makeText(this,"Invalid version name.",Toast.LENGTH_LONG).show();return;
                    }
                    try{InstanceStorage.setVersion(instance,version);render();}
                    catch(Exception e){Toast.makeText(this,"Could not save version: "+e.getMessage(),Toast.LENGTH_LONG).show();}
                }).show();
    }

    private void confirmDelete(InstanceStorage.Instance instance) {
        new AlertDialog.Builder(this).setTitle("Delete "+instance.name+"?")
                .setMessage("This permanently deletes this instance's game folder, including saves, mods, configs and logs. Shared version JARs are kept.")
                .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{
                    try {
                        InstanceStorage.delete(dataRoot,instance);
                        String current=getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_ACTIVE_ID,"");
                        if(instance.id.equals(current)){
                            List<InstanceStorage.Instance> remaining=InstanceStorage.list(dataRoot);
                            android.content.SharedPreferences.Editor edit=getSharedPreferences(PREFS,MODE_PRIVATE).edit();
                            if(remaining.isEmpty())edit.remove(KEY_ACTIVE_ID);else edit.putString(KEY_ACTIVE_ID,remaining.get(0).id);
                            edit.apply();
                        }
                        render();
                    } catch(Exception e) {
                        new AlertDialog.Builder(this).setMessage("Could not delete instance: "+e.getMessage()).setPositiveButton("OK",null).show();
                    }
                }).show();
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}

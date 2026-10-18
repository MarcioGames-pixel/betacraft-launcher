package com.marciogames.betacraft.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.View;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Stores server bookmarks locally. Connecting is unavailable until Minecraft launch is integrated. */
public final class ServerListActivity extends Activity {
    private static final String PREFS = "betacraft_android";
    private static final String KEY = "server_bookmarks_json";
    private LinearLayout entries;

    private static final class Server {
        final String name, address;
        Server(String name, String address) { this.name = name; this.address = address; }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(25,29,36));
        getWindow().setNavigationBarColor(Color.rgb(25,29,36));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(18),dp(18),dp(18));
        root.setBackgroundColor(Color.rgb(25,29,36));
        TextView title = new TextView(this);
        title.setText("Multiplayer servers");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(246,169,26));
        root.addView(title);
        TextView note = new TextView(this);
        note.setText("Save server addresses for later. Direct connection and server status require the Minecraft client to be integrated.");
        note.setTextColor(Color.LTGRAY);
        note.setPadding(0,dp(8),0,dp(12));
        root.addView(note);
        Button add = new Button(this);
        add.setText("Add server");
        add.setAllCaps(false);
        root.addView(add);
        add.setOnClickListener(v -> prompt(null));
        ScrollView scroll = new ScrollView(this);
        entries = new LinearLayout(this);
        entries.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(entries);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Button back = new Button(this);
        back.setText("Back");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        root.addView(back);
        setContentView(root);
        render();
    }

    private List<Server> load() {
        List<Server> result = new ArrayList<>();
        String raw = getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY,"[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for(int i=0;i<arr.length();i++) {
                JSONObject o=arr.optJSONObject(i);
                if(o!=null) result.add(new Server(o.optString("name","Server"),o.optString("address","")));
            }
        } catch(Exception ignored) { }
        return result;
    }

    private void save(List<Server> list) {
        JSONArray arr = new JSONArray();
        try {
            for(Server server:list) {
                JSONObject o=new JSONObject();
                o.put("name",server.name);
                o.put("address",server.address);
                arr.put(o);
            }
        } catch(Exception e) { throw new IllegalStateException("Could not save server list",e); }
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(KEY,arr.toString()).apply();
        render();
    }

    private void prompt(Server old) {
        LinearLayout form=new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad=dp(16); form.setPadding(pad,0,pad,0);
        EditText name=new EditText(this); name.setSingleLine(true); name.setHint("Server name");
        EditText address=new EditText(this); address.setSingleLine(true);
        address.setHint("example.org:25565");
        address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        if(old!=null){name.setText(old.name);address.setText(old.address);}
        form.addView(name);form.addView(address);
        new AlertDialog.Builder(this).setTitle(old==null?"Add server":"Edit server").setView(form)
                .setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
                    String n=name.getText().toString().trim(), a=address.getText().toString().trim();
                    if(n.isEmpty()||n.length()>48||!validAddress(a)){
                        new AlertDialog.Builder(this).setMessage("Enter a name and a valid hostname or IP, optionally followed by :port.")
                                .setPositiveButton("OK",null).show();return;
                    }
                    List<Server> list=load();
                    if(old!=null) list.removeIf(s->s.name.equals(old.name)&&s.address.equals(old.address));
                    for(Server s:list) if(s.address.equalsIgnoreCase(a)){
                        new AlertDialog.Builder(this).setMessage("That server address is already saved.")
                                .setPositiveButton("OK",null).show();return;
                    }
                    list.add(new Server(n,a));save(list);
                }).show();
    }

    private boolean validAddress(String a) {
        if(a.isEmpty()||a.length()>253||a.contains("://")||a.contains("/")||a.contains(" ")||a.contains("@"))return false;
        String host=a;int colon=a.lastIndexOf(':');
        if(colon>0&&a.indexOf(':')==colon){
            String port=a.substring(colon+1);host=a.substring(0,colon);
            try{int p=Integer.parseInt(port);if(p<1||p>65535)return false;}catch(Exception e){return false;}
        }
        if(host.isEmpty()||host.startsWith(".")||host.endsWith("."))return false;
        for(int i=0;i<host.length();i++){char c=host.charAt(i);if(!(Character.isLetterOrDigit(c)||c=='.'||c=='-'||c==':'))return false;}
        return true;
    }

    private void render() {
        entries.removeAllViews();
        List<Server> list=load();
        if(list.isEmpty()){TextView empty=new TextView(this);empty.setText("No saved servers yet.");empty.setTextColor(Color.LTGRAY);empty.setPadding(0,dp(20),0,dp(20));entries.addView(empty);return;}
        for(Server server:list){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(8),dp(8),dp(8),dp(8));
            TextView name=new TextView(this);name.setText(server.name);name.setTextSize(17);name.setTextColor(Color.WHITE);card.addView(name);
            TextView address=new TextView(this);address.setText(server.address);address.setTextColor(Color.LTGRAY);card.addView(address);
            LinearLayout actions=new LinearLayout(this);
            Button copy=new Button(this);copy.setText("Copy address");copy.setAllCaps(false);
            copy.setOnClickListener(v->{ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("Minecraft server",server.address));});
            actions.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            Button edit=new Button(this);edit.setText("Edit");edit.setAllCaps(false);edit.setOnClickListener(v->prompt(server));
            actions.addView(edit,new LinearLayout.LayoutParams(0,-2,1));
            Button delete=new Button(this);delete.setText("Delete");delete.setAllCaps(false);
            delete.setOnClickListener(v->new AlertDialog.Builder(this).setMessage("Delete "+server.name+"?")
                    .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{List<Server> updated=load();updated.removeIf(s->s.name.equals(server.name)&&s.address.equals(server.address));save(updated);}).show());
            actions.addView(delete,new LinearLayout.LayoutParams(0,-2,1));
            card.addView(actions);entries.addView(card);
        }
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
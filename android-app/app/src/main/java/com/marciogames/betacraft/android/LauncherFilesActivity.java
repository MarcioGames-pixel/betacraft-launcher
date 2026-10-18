package com.marciogames.betacraft.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Comparator;

/** Read-only browser for downloaded launcher data, JARs, runtime files and logs. */
public final class LauncherFilesActivity extends Activity {
    private File current;
    private LinearLayout listing;
    private TextView pathLabel;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(25,29,36));
        getWindow().setNavigationBarColor(Color.rgb(25,29,36));
        current=new File(getFilesDir(),"betacraft");
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(14),dp(14),dp(14));
        root.setBackgroundColor(Color.rgb(25,29,36));
        TextView title=new TextView(this);title.setText("Launcher files");title.setTextSize(24);title.setTextColor(Color.rgb(246,169,26));root.addView(title);
        pathLabel=new TextView(this);pathLabel.setTextColor(Color.LTGRAY);pathLabel.setPadding(0,dp(8),0,dp(8));root.addView(pathLabel);
        LinearLayout tools=new LinearLayout(this);
        Button up=new Button(this);up.setText("Parent folder");up.setAllCaps(false);up.setOnClickListener(v->{File parent=current.getParentFile();File rootDir=new File(getFilesDir(),"betacraft");if(parent!=null&&parent.getAbsolutePath().startsWith(rootDir.getAbsolutePath())){current=parent;render();}});
        tools.addView(up,new LinearLayout.LayoutParams(0,-2,1));
        Button refresh=new Button(this);refresh.setText("Refresh");refresh.setAllCaps(false);refresh.setOnClickListener(v->render());tools.addView(refresh,new LinearLayout.LayoutParams(0,-2,1));root.addView(tools);
        ScrollView scroll=new ScrollView(this);listing=new LinearLayout(this);listing.setOrientation(LinearLayout.VERTICAL);scroll.addView(listing);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Button close=new Button(this);close.setText("Back");close.setAllCaps(false);close.setOnClickListener(v->finish());root.addView(close);
        setContentView(root);render();
    }

    private void render(){
        listing.removeAllViews();pathLabel.setText(current.getAbsolutePath());
        File[] files=current.listFiles();
        if(files==null){addText("Folder is empty or cannot be read.");return;}
        Arrays.sort(files,Comparator.comparing((File f)->!f.isDirectory()).thenComparing(File::getName,String.CASE_INSENSITIVE_ORDER));
        if(files.length==0){addText("No files in this folder yet.");return;}
        for(File f:files){
            if(f.isHidden())continue;
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(8),dp(7),dp(8),dp(7));
            TextView label=new TextView(this);label.setText((f.isDirectory()?"📁 ":"📄 ")+f.getName()+(f.isDirectory()?"": "  •  "+size(f.length())));label.setTextColor(Color.WHITE);label.setTextSize(15);row.addView(label);
            LinearLayout actions=new LinearLayout(this);
            if(f.isDirectory()){
                Button open=new Button(this);open.setText("Open");open.setAllCaps(false);open.setOnClickListener(v->{current=f;render();});actions.addView(open,new LinearLayout.LayoutParams(0,-2,1));
            } else {
                Button copy=new Button(this);copy.setText("Copy path");copy.setAllCaps(false);copy.setOnClickListener(v->{ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("File path",f.getAbsolutePath()));});actions.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
                if(f.length()<=512*1024&&(f.getName().endsWith(".txt")||f.getName().endsWith(".log")||f.getName().endsWith(".info")||f.getName().endsWith(".json"))){
                    Button view=new Button(this);view.setText("Preview");view.setAllCaps(false);view.setOnClickListener(v->preview(f));actions.addView(view,new LinearLayout.LayoutParams(0,-2,1));
                }
            }
            row.addView(actions);listing.addView(row);
        }
    }
    private void preview(File file){
        try(java.io.FileInputStream in=new java.io.FileInputStream(file)){
            byte[] data=new byte[(int)Math.min(file.length(),512*1024)];int n=in.read(data);
            String body=n<=0?"(empty file)":new String(data,0,n,java.nio.charset.StandardCharsets.UTF_8);
            ScrollView scroll=new ScrollView(this);TextView text=new TextView(this);text.setText(body);text.setTextIsSelectable(true);text.setPadding(dp(12),dp(12),dp(12),dp(12));scroll.addView(text);
            new AlertDialog.Builder(this).setTitle(file.getName()).setView(scroll).setPositiveButton("Close",null).show();
        }catch(Exception e){new AlertDialog.Builder(this).setMessage("Could not read file: "+e.getMessage()).setPositiveButton("OK",null).show();}
    }
    private void addText(String value){TextView t=new TextView(this);t.setText(value);t.setTextColor(Color.LTGRAY);t.setPadding(0,dp(18),0,dp(18));listing.addView(t);}
    private String size(long bytes){if(bytes<1024)return bytes+" B";if(bytes<1024*1024)return new DecimalFormat("0.0").format(bytes/1024.0)+" KiB";return new DecimalFormat("0.0").format(bytes/(1024.0*1024.0))+" MiB";}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
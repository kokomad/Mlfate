package com.khader.mlfati;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Base64;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.*;

@CapacitorPlugin(name = "FM")
public class FMPlugin extends Plugin {
    File f(String p) { return new File(Environment.getExternalStorageDirectory(), p == null ? "" : p); }

    @PluginMethod
    public void check(PluginCall c) {
        JSObject o = new JSObject();
        o.put("granted", Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager());
        c.resolve(o);
    }

    @PluginMethod
    public void ask(PluginCall c) {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getContext().getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        } catch (Exception e) {
            Intent i = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        }
        c.resolve();
    }

    @PluginMethod
    public void list(PluginCall c) {
        File[] l = f(c.getString("path")).listFiles();
        if (l == null) { c.reject("لا يمكن قراءة المجلد (الصلاحية؟)"); return; }
        JSArray a = new JSArray();
        for (File x : l) {
            JSObject o = new JSObject();
            o.put("name", x.getName());
            o.put("dir", x.isDirectory());
            o.put("size", x.length());
            o.put("mtime", x.lastModified());
            a.put(o);
        }
        JSObject r = new JSObject();
        r.put("files", a);
        c.resolve(r);
    }

    @PluginMethod
    public void mkdir(PluginCall c) { f(c.getString("path")).mkdirs(); c.resolve(); }

    @PluginMethod
    public void del(PluginCall c) { rm(f(c.getString("path"))); c.resolve(); }

    @PluginMethod
    public void mv(PluginCall c) {
        File a = f(c.getString("from")), b = f(c.getString("to"));
        try { if (!a.renameTo(b)) { copy(a, b); rm(a); } c.resolve(); } catch (Exception e) { c.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void cp(PluginCall c) {
        try { copy(f(c.getString("from")), f(c.getString("to"))); c.resolve(); } catch (Exception e) { c.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void write(PluginCall c) {
        try {
            File t = f(c.getString("path"));
            t.getParentFile().mkdirs();
            FileOutputStream o = new FileOutputStream(t);
            o.write(Base64.decode(c.getString("data"), Base64.DEFAULT));
            o.close();
            c.resolve();
        } catch (Exception e) { c.reject(String.valueOf(e.getMessage())); }
    }

    void rm(File x) {
        if (x.isDirectory()) { File[] l = x.listFiles(); if (l != null) for (File y : l) rm(y); }
        x.delete();
    }

    void copy(File a, File b) throws IOException {
        if (a.isDirectory()) {
            b.mkdirs();
            File[] l = a.listFiles();
            if (l != null) for (File y : l) copy(y, new File(b, y.getName()));
        } else {
            b.getParentFile().mkdirs();
            InputStream i = new FileInputStream(a);
            OutputStream o = new FileOutputStream(b);
            byte[] buf = new byte[65536];
            int n;
            while ((n = i.read(buf)) > 0) o.write(buf, 0, n);
            i.close();
            o.close();
        }
    }
}
